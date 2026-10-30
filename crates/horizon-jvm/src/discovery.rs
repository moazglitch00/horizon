//! Fabric Loader's own verdict on a `mods/` folder, and the cross-check against
//! [`horizon_fabric`].
//!
//! The report types here mirror the JSON `horizon.host.Discovery` writes
//! (`java/horizon-host/src/horizon/host/Discovery.java`). They are read back into typed values
//! rather than passed through as a string so the engine can act on them and so a field the host
//! stops sending is a compile-visible change, not a silent `null` in a log.
//!
//! [`Probe`] is the whole phase-2 errand: compile the host if its sources changed, start it, prove
//! the pipe works, ask Loader to read the folder, shut the JVM down and report timings.

use crate::host::{self, HostConfig, HostError, JavaHost};
use crate::libs::LibSet;
use crate::protocol::Side;
use horizon_prepare::java::Jdk;
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};
use thiserror::Error;

/// One mod as Fabric Loader sees it.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LoaderMod {
    pub id: String,
    pub version: String,
    pub name: String,
    /// `fabric.mod.json`'s `type`: `mod`, `builtin`, … (`type` is a Rust keyword).
    #[serde(rename = "type")]
    pub kind: String,
    /// `client`, `server` or `*`.
    pub environment: String,
    /// Supplied by the game provider or the loader, not by a file in `mods/`.
    pub builtin: bool,
    /// Inside another mod jar (`META-INF/jars`).
    pub nested: bool,
    /// The jar(s) or directory the mod came from. A nested mod can have several.
    #[serde(default)]
    pub origins: Vec<String>,
    #[serde(default)]
    pub provides: Vec<String>,
    #[serde(default)]
    pub entrypoints: Vec<String>,
    #[serde(default)]
    pub depends: Vec<LoaderDependency>,
    #[serde(default)]
    pub recommends: Vec<LoaderDependency>,
    #[serde(default)]
    pub suggests: Vec<LoaderDependency>,
    #[serde(default)]
    pub conflicts: Vec<LoaderDependency>,
    #[serde(default)]
    pub breaks: Vec<LoaderDependency>,
}

/// A declared dependency and the version ranges it accepts.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct LoaderDependency {
    pub id: String,
    #[serde(default)]
    pub versions: Vec<String>,
}

/// Fabric Loader's answer for one folder.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LoaderReport {
    /// Loader found no error. Environment-disabled and duplicate mods are not errors.
    pub ok: bool,
    pub loader: String,
    pub game_version: String,
    pub side: String,
    pub java_version: u32,
    /// Root candidates: mod jars found in `mods/` (nested jars are not roots).
    pub roots: usize,
    #[serde(default)]
    pub mods: Vec<LoaderMod>,
    /// Loader's load order: dependencies before the mods that need them.
    #[serde(default)]
    pub order: Vec<String>,
    /// Mods Loader removed because they are client-only on a server or vice versa.
    #[serde(default)]
    pub disabled: Vec<String>,
    #[serde(default)]
    pub errors: Vec<String>,
    #[serde(default)]
    pub warnings: Vec<String>,
    /// How long Loader itself took, inside the host.
    pub elapsed_millis: u64,
}

impl LoaderReport {
    pub fn parse(json: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json)
    }

    /// Mods that came from a file rather than from the provider or the loader itself.
    pub fn file_mods(&self) -> impl Iterator<Item = &LoaderMod> {
        self.mods
            .iter()
            .filter(|module| !module.builtin && module.kind != "builtin")
    }

    pub fn find(&self, id: &str) -> Option<&LoaderMod> {
        self.mods.iter().find(|module| module.id == id)
    }

    /// The ids of mods the folder supplies, sorted. This is the set phase 1's report should also
    /// produce, so a difference is a bug in one of the two readers.
    pub fn file_ids(&self) -> Vec<String> {
        let mut ids: Vec<String> = self.file_mods().map(|module| module.id.clone()).collect();
        ids.sort();
        ids.dedup();
        ids
    }
}

/// What differs between Loader's report and the JVM-free phase-1 report.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
pub struct Comparison {
    /// Mods Loader loaded that phase 1 did not report.
    pub only_loader: Vec<String>,
    /// Mods phase 1 reported that Loader does not load.
    pub only_engine: Vec<String>,
    /// Both agree on the set; only the order can still differ.
    pub order_matches: bool,
    pub engine_order: Vec<String>,
    pub loader_order: Vec<String>,
}

impl Comparison {
    /// Whether the two readers agree on the mod set and the order.
    pub fn agrees(&self) -> bool {
        self.only_loader.is_empty() && self.only_engine.is_empty()
    }
}

/// Mods that only exist because the loader is on the class path. They are real mods, but they
/// are not in any player's `mods/` folder, so the JVM-free reader cannot see them: phase 1
/// models `fabricloader` through `Options::loader_version` and `mixinextras` through
/// [`horizon_fabric::resolve::PINNED_LOADER_BUNDLED`].
const LOADER_PROVIDED: &[&str] = &["fabricloader", "mixinextras"];

pub fn compare(loader: &LoaderReport, engine: &horizon_fabric::Report) -> Comparison {
    let loader_ids: Vec<String> = loader
        .file_ids()
        .into_iter()
        .filter(|id| !LOADER_PROVIDED.contains(&id.as_str()))
        .collect();

    let mut engine_ids: Vec<String> = engine
        .mods
        .iter()
        .filter(|module| module.status == horizon_fabric::Status::Loads)
        .map(|module| module.id.clone())
        .collect();
    engine_ids.sort();
    engine_ids.dedup();

    let only_loader = loader_ids
        .iter()
        .filter(|id| !engine_ids.contains(id))
        .cloned()
        .collect();
    let only_engine = engine_ids
        .iter()
        .filter(|id| !loader_ids.contains(id))
        .cloned()
        .collect();

    let loader_order: Vec<String> = loader
        .order
        .iter()
        .filter(|id| loader_ids.contains(id))
        .cloned()
        .collect();
    let engine_order: Vec<String> = engine
        .order
        .iter()
        .filter(|id| loader_ids.contains(id))
        .cloned()
        .collect();

    Comparison {
        only_loader,
        only_engine,
        order_matches: loader_order == engine_order,
        engine_order,
        loader_order,
    }
}

/// Everything a probe needs. Build one with [`ProbeConfig::new`] and adjust timeouts if a
/// machine is slow.
pub struct ProbeConfig {
    pub jdk: Jdk,
    pub libs: LibSet,
    pub cache: PathBuf,
    pub mods_dir: PathBuf,
    pub game_dir: PathBuf,
    pub game_version: String,
    pub side: Side,
    /// Control round trips measured after the handshake. Zero skips the check.
    pub pings: usize,
    pub log: Option<PathBuf>,
    pub resources: Option<PathBuf>,
    pub startup_timeout: Duration,
    pub frame_timeout: Duration,
    pub discover_timeout: Duration,
    pub shutdown_timeout: Duration,
}

impl ProbeConfig {
    pub fn new(
        jdk: Jdk,
        libs: LibSet,
        cache: impl Into<PathBuf>,
        mods_dir: impl Into<PathBuf>,
        game_dir: impl Into<PathBuf>,
        game_version: impl Into<String>,
        side: Side,
    ) -> Self {
        Self {
            jdk,
            libs,
            cache: cache.into(),
            mods_dir: mods_dir.into(),
            game_dir: game_dir.into(),
            game_version: game_version.into(),
            side,
            pings: 5,
            log: None,
            resources: None,
            startup_timeout: Duration::from_secs(30),
            frame_timeout: Duration::from_secs(10),
            discover_timeout: Duration::from_secs(120),
            shutdown_timeout: Duration::from_secs(10),
        }
    }
}

/// Control-latency samples from the ping loop.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct PingStats {
    pub count: usize,
    pub min: Duration,
    pub mean: Duration,
    pub max: Duration,
}

impl PingStats {
    fn from(samples: &[Duration]) -> Self {
        if samples.is_empty() {
            return Self::default();
        }

        let min = *samples.iter().min().expect("non-empty");
        let max = *samples.iter().max().expect("non-empty");
        let total: Duration = samples.iter().sum();

        Self {
            count: samples.len(),
            min,
            mean: total / samples.len() as u32,
            max,
        }
    }
}

/// Where the probe spent its time.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Timings {
    /// `javac` for the host, or zero when a previous run's classes were reused.
    pub compile: Duration,
    /// Spawn to the `hello` frame (JVM start included).
    pub startup: Duration,
    /// The `discover` request to the report.
    pub discover: Duration,
    /// `shutdown` to the process exiting.
    pub shutdown: Duration,
}

/// A finished probe.
#[derive(Clone, Debug)]
pub struct Probe {
    pub hello: host::HelloInfo,
    pub report: LoaderReport,
    /// Loader log lines that arrived as frames (the rest is on stderr, see [`ProbeConfig::log`]).
    pub logs: Vec<String>,
    pub pings: PingStats,
    pub timings: Timings,
    pub host_build: host::HostBuild,
    pub exit_code: Option<i32>,
}

impl Probe {
    /// Compiles the host, runs one discovery and shuts the JVM down.
    pub fn run(config: ProbeConfig) -> Result<Probe, ProbeError> {
        let compile_start = Instant::now();
        let build = host::compile(&config.jdk, &config.libs.jars, &config.cache)?;
        let compile = compile_start.elapsed();

        let mut host_config = HostConfig::new(
            &config.jdk.java,
            &build.classes,
            config.libs.jars.clone(),
            &config.game_dir,
            &config.game_version,
            config.side,
        );
        host_config.log = config.log.clone();
        host_config.startup_timeout = config.startup_timeout;
        host_config.frame_timeout = config.frame_timeout;
        host_config.discover_timeout = config.discover_timeout;
        host_config.shutdown_timeout = config.shutdown_timeout;

        let startup_start = Instant::now();
        let mut host = JavaHost::start(host_config)?;
        let startup = startup_start.elapsed();

        let mut samples = Vec::with_capacity(config.pings);
        for _ in 0..config.pings {
            samples.push(host.ping()?);
        }
        let pings = PingStats::from(&samples);
        let hello = host.hello().clone();

        // Phase 3 bridge: if prepared resources are available, dump the Rust registry and
        // send it to the host before discovery, so Java mod code can see Rust's world.
        if let Some(resources) = &config.resources {
            if let Some(snapshot) = dump_registry(resources) {
                host.send_registry_snapshot(&snapshot)?;
            }
        }

        let discover_start = Instant::now();
        let discovery = host.discover(&config.mods_dir)?;
        let discover = discover_start.elapsed();

        let shutdown_start = Instant::now();
        let status = host.shutdown(0)?;
        let shutdown = shutdown_start.elapsed();

        Ok(Probe {
            hello,
            report: discovery.report,
            logs: discovery.logs,
            pings,
            timings: Timings {
                compile,
                startup,
                discover,
                shutdown,
            },
            host_build: build,
            exit_code: status.code(),
        })
    }
}

/// Why a probe could not produce a report. A mod set Loader rejects is *not* one of these: that
/// is a successful probe whose [`LoaderReport::ok`] is false.
#[derive(Debug, Error)]
pub enum ProbeError {
    #[error(transparent)]
    Host(#[from] HostError),
    #[error("the Java host exited with code {} instead of 0 after a clean shutdown", .0.map(|c| c.to_string()).unwrap_or_else(|| "unknown".to_string()))]
    Exit(Option<i32>),
}

/// A useful default cache root for the compiled host, outside the game folder.
pub fn default_cache() -> PathBuf {
    std::env::temp_dir().join("horizon-jvm")
}

/// The default game directory for a probe that is not attached to a real install.
pub fn default_game_dir() -> PathBuf {
    std::env::temp_dir().join("horizon-jvm-game")
}

/// Runs the `horizon-registry-dump` binary (a sibling of the current executable) and returns
/// its stdout, or `None` if it could not be found or failed.
fn dump_registry(resources: &Path) -> Option<String> {
    let current = std::env::current_exe().ok()?;
    let dump = current.with_file_name(if cfg!(windows) {
        "horizon-registry-dump.exe"
    } else {
        "horizon-registry-dump"
    });

    if !dump.is_file() {
        return None;
    }

    let output = std::process::Command::new(&dump)
        .arg("--resources")
        .arg(resources)
        .output()
        .ok()?;

    if !output.status.success() {
        return None;
    }

    String::from_utf8(output.stdout).ok()
}
mod tests {
    use super::*;

    const SAMPLE: &str = r#"{
        "ok": true,
        "loader": "0.19.5",
        "gameVersion": "26.3",
        "side": "client",
        "javaVersion": 25,
        "roots": 2,
        "mods": [
            {"id": "minecraft", "version": "26.3", "name": "Minecraft", "type": "builtin",
             "environment": "*", "builtin": true, "nested": false, "origins": [],
             "provides": [], "entrypoints": [],
             "depends": [], "recommends": [], "suggests": [], "conflicts": [], "breaks": []},
            {"id": "fabricloader", "version": "0.19.5", "name": "Fabric Loader", "type": "builtin",
             "environment": "*", "builtin": false, "nested": false, "origins": ["loader.jar"],
             "provides": ["fabric"], "entrypoints": [],
             "depends": [], "recommends": [], "suggests": [], "conflicts": [], "breaks": []},
            {"id": "sodium", "version": "0.6.13", "name": "Sodium", "type": "mod",
             "environment": "client", "builtin": false, "nested": false, "origins": ["mods/sodium.jar"],
             "provides": [], "entrypoints": ["client"],
             "depends": [{"id": "minecraft", "versions": ["1.21.1"]}],
             "recommends": [], "suggests": [], "conflicts": [], "breaks": []}
        ],
        "order": ["minecraft", "fabricloader", "sodium"],
        "disabled": [],
        "errors": [],
        "warnings": [],
        "elapsedMillis": 42
    }"#;

    #[test]
    fn report_round_trips_and_hides_builtins() {
        let report = LoaderReport::parse(SAMPLE).expect("sample report");
        assert!(report.ok);
        assert_eq!(report.roots, 2);
        assert_eq!(report.file_ids(), vec!["sodium".to_string()]);
        assert_eq!(
            report.find("fabricloader").unwrap().provides,
            vec!["fabric"]
        );
        assert_eq!(
            report.find("sodium").unwrap().depends[0].id,
            "minecraft".to_string()
        );

        let json = serde_json::to_string(&report).unwrap();
        let again = LoaderReport::parse(&json).unwrap();
        assert_eq!(again, report);
    }

    #[test]
    fn comparison_finds_missing_and_extra_mods() {
        let report = LoaderReport::parse(SAMPLE).unwrap();
        let engine = horizon_fabric::Report {
            ok: true,
            game_version: "26.3".to_string(),
            side: "client".to_string(),
            mods_folder: "mods".to_string(),
            jars: 2,
            mods: vec![
                horizon_fabric::ModStatus {
                    id: "sodium".to_string(),
                    version: "0.6.13".to_string(),
                    name: "Sodium".to_string(),
                    file: "sodium.jar".to_string(),
                    environment: "client".to_string(),
                    status: horizon_fabric::Status::Loads,
                    reason: None,
                    depends: vec![],
                    provides: vec![],
                    mixin_configs: 0,
                    access_widener: false,
                    nested_jars: 0,
                    game_classes: 0,
                    graphics_classes: 0,
                    kotlin: false,
                },
                horizon_fabric::ModStatus {
                    id: "lithium".to_string(),
                    version: "0.16".to_string(),
                    name: "Lithium".to_string(),
                    file: "lithium.jar".to_string(),
                    environment: "*".to_string(),
                    status: horizon_fabric::Status::Loads,
                    reason: None,
                    depends: vec![],
                    provides: vec![],
                    mixin_configs: 0,
                    access_widener: false,
                    nested_jars: 0,
                    game_classes: 0,
                    graphics_classes: 0,
                    kotlin: false,
                },
            ],
            order: vec!["lithium".to_string(), "sodium".to_string()],
            errors: vec![],
            warnings: vec![],
        };

        let comparison = compare(&report, &engine);
        assert!(comparison.only_loader.is_empty(), "{comparison:?}");
        assert_eq!(comparison.only_engine, vec!["lithium".to_string()]);
        assert!(!comparison.agrees());
    }

    #[test]
    fn ping_stats_are_sane() {
        let stats = PingStats::from(&[
            Duration::from_millis(3),
            Duration::from_millis(9),
            Duration::from_millis(6),
        ]);
        assert_eq!(stats.count, 3);
        assert_eq!(stats.min, Duration::from_millis(3));
        assert_eq!(stats.max, Duration::from_millis(9));
        assert_eq!(stats.mean, Duration::from_millis(6));
        assert_eq!(PingStats::from(&[]), PingStats::default());
    }
}
