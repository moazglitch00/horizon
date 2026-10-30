//! `horizon-fabric-probe`: run Fabric Loader's discovery over a `mods/` folder in the Java host
//! and compare the answer with Horizon's JVM-free reader.
//!
//! ```text
//! horizon-fabric-probe --libs DIR [--mods DIR] [--game-dir DIR] [--game-version V]
//!                      [--side client|server] [--cache DIR] [--java PATH] [--pings N]
//!                      [--log FILE] [--no-compare] [--json] [--write-report FILE]
//! ```
//!
//! Exit code 0 when Loader accepted the folder and both readers agree, 1 when either says no, 2
//! on a usage or setup error (missing libraries, no Java 25). Same conventions as
//! `horizon-fabric check`.

use horizon_fabric::Options;
use horizon_jvm::discovery::{Comparison, compare};
use horizon_jvm::{Probe, ProbeConfig, Side, libs};
use horizon_prepare::java::{self, Jdk};
use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::time::Duration;

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();

    match run(&args) {
        Ok(code) => code,
        Err(message) => {
            eprintln!("horizon-fabric-probe: {message}");
            eprintln!(
                "usage: horizon-fabric-probe --libs DIR [--mods DIR] [--game-dir DIR] \
                 [--game-version V] [--side client|server] [--cache DIR] [--java PATH] \
                 [--pings N] [--log FILE] [--no-compare] [--json] [--write-report FILE]"
            );
            ExitCode::from(2)
        }
    }
}

struct Args {
    libs: PathBuf,
    mods: Option<PathBuf>,
    game_dir: PathBuf,
    game_version: String,
    side: Side,
    cache: PathBuf,
    java: Option<PathBuf>,
    pings: usize,
    log: Option<PathBuf>,
    compare: bool,
    json: bool,
    write_report: Option<PathBuf>,
}

fn run(args: &[String]) -> Result<ExitCode, String> {
    let parsed = parse(args)?;
    let set = libs::locate(&parsed.libs).map_err(|e| e.to_string())?;
    let jdk = find_jdk(parsed.java.as_deref())?;

    let mods = parsed
        .mods
        .clone()
        .unwrap_or_else(|| parsed.game_dir.join("mods"));

    if !mods.exists() {
        eprintln!(
            "horizon-fabric-probe: {} does not exist; Fabric Loader will report an empty folder",
            mods.display()
        );
    } else if !mods.is_dir() {
        return Err(format!("{} is not a directory", mods.display()));
    }

    let mut config = ProbeConfig::new(
        jdk,
        set,
        &parsed.cache,
        &mods,
        &parsed.game_dir,
        &parsed.game_version,
        parsed.side,
    );
    config.pings = parsed.pings;
    config.log = parsed.log.clone();

    let probe = Probe::run(config).map_err(|e| e.to_string())?;

    let engine = parsed.compare.then(|| {
        horizon_fabric::check(
            &mods,
            Options {
                game_version: parsed.game_version.clone(),
                side: match parsed.side {
                    Side::Client => horizon_fabric::Side::Client,
                    Side::Server => horizon_fabric::Side::Server,
                },
                loader_version: Some(probe.report.loader.clone()),
                ..Options::default()
            },
        )
    });
    let comparison = engine.as_ref().map(|report| compare(&probe.report, report));

    if parsed.json {
        println!("{}", json(&probe, comparison.as_ref()));
    } else {
        print_text(&probe, comparison.as_ref(), &mods, engine.as_ref());
    }

    if let Some(path) = &parsed.write_report {
        let text = serde_json::to_string_pretty(&probe.report)
            .map_err(|e| format!("cannot write the Loader report as JSON: {e}"))?;
        std::fs::write(path, text).map_err(|e| format!("cannot write {}: {e}", path.display()))?;
    }

    let healthy = probe.report.ok
        && probe.exit_code == Some(0)
        && comparison.as_ref().is_none_or(Comparison::agrees);

    Ok(if healthy {
        ExitCode::SUCCESS
    } else {
        ExitCode::FAILURE
    })
}

fn parse(args: &[String]) -> Result<Args, String> {
    let game_version = horizon_core::version::VERSION_ID.to_string();
    let mut parsed = Args {
        libs: PathBuf::new(),
        mods: None,
        game_dir: horizon_jvm::discovery::default_game_dir(),
        game_version,
        side: Side::Client,
        cache: horizon_jvm::discovery::default_cache(),
        java: None,
        pings: 5,
        log: None,
        compare: true,
        json: false,
        write_report: None,
    };

    let mut index = 0;
    while let Some(arg) = args.get(index) {
        match arg.as_str() {
            "--libs" => parsed.libs = next(args, &mut index)?.into(),
            "--mods" => parsed.mods = Some(next(args, &mut index)?.into()),
            "--game-dir" => parsed.game_dir = next(args, &mut index)?.into(),
            "--game-version" => parsed.game_version = next(args, &mut index)?,
            "--side" => {
                parsed.side =
                    Side::parse(&next(args, &mut index)?).ok_or("--side is client or server")?;
            }
            "--cache" => parsed.cache = next(args, &mut index)?.into(),
            "--java" => parsed.java = Some(next(args, &mut index)?.into()),
            "--pings" => {
                parsed.pings = next(args, &mut index)?
                    .parse()
                    .map_err(|_| "--pings needs a number")?;
            }
            "--log" => parsed.log = Some(next(args, &mut index)?.into()),
            "--no-compare" => parsed.compare = false,
            "--json" => parsed.json = true,
            "--write-report" => parsed.write_report = Some(next(args, &mut index)?.into()),
            other => return Err(format!("unknown option `{other}`")),
        }
        index += 1;
    }

    if parsed.libs.as_os_str().is_empty() {
        return Err("--libs DIR is required (see tools/benchmark-deps/fabric.lock.json)".into());
    }

    Ok(parsed)
}

fn next(args: &[String], index: &mut usize) -> Result<String, String> {
    *index += 1;
    args.get(*index)
        .cloned()
        .ok_or_else(|| format!("{} needs a value", args[*index - 1]))
}

/// Java 25 is what a Fabric API mod set needs at runtime, so the probe uses a JDK that new. A
/// `--java` path selects that install (its sibling `javac` compiles the host); otherwise
/// `horizon_prepare::java::find` checks `JAVA_HOME`, `PATH` and the installer folders.
fn find_jdk(java: Option<&Path>) -> Result<Jdk, String> {
    let major = i64::from(horizon_core::version::JAVA_VERSION);

    if let Some(java) = java {
        let javac = match java.file_name().and_then(|name| name.to_str()) {
            Some("java.exe") => java.with_file_name("javac.exe"),
            Some("java") => java.with_file_name("javac"),
            _ => {
                return Err(format!(
                    "--java must name a java executable, not {}",
                    java.display()
                ));
            }
        };

        if !java.is_file() {
            return Err(format!("--java {} does not exist", java.display()));
        }
        if !javac.is_file() {
            return Err(format!(
                "--java {} has no sibling javac ({}); use a JDK, not a JRE",
                java.display(),
                javac.display()
            ));
        }

        return Ok(Jdk {
            javac,
            java: java.to_path_buf(),
        });
    }

    java::find(major, &[])
        .map_err(|e| format!("{e}\nSet JAVA_HOME to a JDK {major}, or pass --java."))
}

fn print_text(
    probe: &Probe,
    comparison: Option<&Comparison>,
    mods: &Path,
    engine: Option<&horizon_fabric::Report>,
) {
    let report = &probe.report;
    println!("Horizon Fabric probe");
    println!(
        "  host       {} (Fabric Loader {}, Java {})",
        probe.hello.host, probe.hello.loader, report.java_version
    );
    println!(
        "  game       {} on {} ({})",
        report.game_version,
        report.side,
        mods.display()
    );
    println!(
        "  loader     {}, {} mods, {} in load order, {} disabled, {} error(s), {} warning(s), {} ms",
        if report.ok { "ok" } else { "FAILED" },
        report.mods.len(),
        report.order.len(),
        report.disabled.len(),
        report.errors.len(),
        report.warnings.len(),
        report.elapsed_millis
    );

    if let Some(engine) = engine {
        println!(
            "  engine     {}, {} mods, {} in load order, {} error(s), {} warning(s)",
            if engine.ok { "ok" } else { "FAILED" },
            engine.mods.len(),
            engine.order.len(),
            engine.errors.len(),
            engine.warnings.len()
        );
    }

    if let Some(comparison) = comparison {
        if comparison.agrees() {
            if comparison.order_matches {
                println!(
                    "  compare    agree: {} mod id(s), same load order",
                    comparison.engine_order.len() + comparison.only_engine.len()
                );
            } else {
                println!(
                    "  compare    agree on the mod set ({} id(s)); load order differs (topological order is not unique)",
                    comparison.engine_order.len() + comparison.only_engine.len()
                );
            }
        } else {
            println!("  compare    DIFFERS:");
            if !comparison.only_loader.is_empty() {
                println!(
                    "             only Loader: {}",
                    comparison.only_loader.join(", ")
                );
            }
            if !comparison.only_engine.is_empty() {
                println!(
                    "             only engine: {}",
                    comparison.only_engine.join(", ")
                );
            }
            if !comparison.order_matches {
                println!(
                    "             order: engine {} / loader {}",
                    comparison.engine_order.len(),
                    comparison.loader_order.len()
                );
            }
        }
    }

    for error in &report.errors {
        println!("  error      {error}");
    }
    for warning in &report.warnings {
        println!("  warning    {warning}");
    }

    println!(
        "  timings    compile {} ({}), startup {}, discover {}, shutdown {}",
        millis(probe.timings.compile),
        if probe.timings.compile.is_zero() {
            "cached"
        } else {
            "javac"
        },
        millis(probe.timings.startup),
        millis(probe.timings.discover),
        millis(probe.timings.shutdown)
    );

    if probe.pings.count > 0 {
        println!(
            "  ping       n={} min {} mean {} max {}",
            probe.pings.count,
            micros(probe.pings.min),
            micros(probe.pings.mean),
            micros(probe.pings.max)
        );
    }
}

fn json(probe: &Probe, comparison: Option<&Comparison>) -> String {
    serde_json::json!({
        "hello": {
            "protocol": probe.hello.protocol,
            "host": probe.hello.host,
            "loader": probe.hello.loader,
            "gameVersion": probe.hello.game_version,
            "side": probe.hello.side.as_str(),
        },
        "report": probe.report,
        "comparison": comparison,
        "pings": {
            "count": probe.pings.count,
            "minMicros": probe.pings.min.as_micros(),
            "meanMicros": probe.pings.mean.as_micros(),
            "maxMicros": probe.pings.max.as_micros(),
        },
        "timings": {
            "compileMicros": probe.timings.compile.as_micros(),
            "startupMicros": probe.timings.startup.as_micros(),
            "discoverMicros": probe.timings.discover.as_micros(),
            "shutdownMicros": probe.timings.shutdown.as_micros(),
        },
        "host": {
            "hash": probe.host_build.hash,
            "classes": probe.host_build.classes,
        },
        "exitCode": probe.exit_code,
    })
    .to_string()
}

fn millis(duration: Duration) -> String {
    format!("{:.1} ms", duration.as_secs_f64() * 1_000.0)
}

fn micros(duration: Duration) -> String {
    format!("{:.2} ms", duration.as_secs_f64() * 1_000.0)
}
