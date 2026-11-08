//! Regression tests for Mixin config discovery and transformation through the Java host.
//!
//! Every case builds a real Fabric mod jar with `javac`/`jar`, starts the real host (the same
//! `horizon.host.Main` the engine runs), asks Fabric Loader to discover it, then loads and
//! invokes a class and checks the *observed* behaviour. Nothing here asserts on "Mixin were
//! initialised": a case only passes when the bytecode a mod asked for actually ran.
//!
//! Coverage:
//! - `@Inject`, `@Overwrite`, `@Redirect`, `@Shadow` accessors in one config;
//! - several mods, and several mixin configs in one mod;
//! - a mod whose declared config is missing, one with invalid JSON, and one naming a mixin
//!   class that is not in the jar: each must be isolated, and the other mod's mixin must still
//!   apply;
//! - a mixin pointing at a target class that does not exist: the error must name it, and the
//!   class must still load and run untransformed;
//! - client-only and server-only mixin lists: only the host's own side is applied.
//!
//! Not covered yet (documented gaps, deliberately not faked):
//! - refmap remapping: the fixtures are not obfuscated, so a refmap is never exercised;
//! - Mixin plugins (`IMixinConfigPlugin`) and `mixinextras` entrypoints;
//! - duplicate config names declared by two mods (Fabric Loader itself rejects these during
//!   `FabricMixinBootstrap.init`, which the host treats as non-fatal).
//!
//! Needs the pinned Fabric libraries (see `horizon_jvm::libs::PINNED`) and a JDK 25 to run
//! Fabric Loader, plus `javac`/`jar` to build the fixtures. Without them the test reports why
//! it skipped instead of failing.

use horizon_jvm::libs::{self, LibSet};
use horizon_jvm::{HostConfig, JavaHost, Side};
use horizon_prepare::java;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{Duration, Instant};

const GAME_VERSION: &str = "26.3";

/// One mod fixture: its id, the mixin configs `fabric.mod.json` declares, the extra resources
/// (the config files themselves) and the Java sources.
struct Mod<'a> {
    id: &'a str,
    mixins: &'a [&'a str],
    resources: &'a [(&'a str, &'a str)],
    sources: &'a [(&'a str, &'a str)],
}

fn setup() -> Option<(java::Jdk, LibSet)> {
    let jdk = java::find(25, &[]).ok()?;
    let libs_dir = std::env::var_os("HORIZON_FABRIC_LIBS")
        .map(PathBuf::from)
        .or_else(|| {
            let local = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join("..")
                .join("..")
                .join(".local")
                .join("fabric-libs");
            local.is_dir().then_some(local)
        })?;
    let libs = libs::locate(&libs_dir).ok()?;
    Some((jdk, libs))
}

fn jar_tool(jdk: &java::Jdk) -> PathBuf {
    let name = if cfg!(windows) { "jar.exe" } else { "jar" };
    jdk.javac.with_file_name(name)
}

fn classpath(libs: &LibSet) -> String {
    java::classpath(libs.jars.iter().map(PathBuf::as_path))
}

/// Compiles one mod's sources into a jar rooted exactly as Fabric expects.
fn build_mod(
    jdk: &java::Jdk,
    libs: &LibSet,
    work: &Path,
    spec: &Mod<'_>,
    mods_dir: &Path,
) -> PathBuf {
    let staging = work.join("build").join(spec.id);
    let source_dir = work.join("src").join(spec.id);
    let _ = fs::remove_dir_all(&staging);
    let _ = fs::remove_dir_all(&source_dir);
    fs::create_dir_all(&staging).unwrap();
    fs::create_dir_all(&source_dir).unwrap();

    let mut javac = Command::new(&jdk.javac);
    javac
        .arg("--release")
        .arg("21")
        .arg("-proc:none")
        .arg("-encoding")
        .arg("UTF-8")
        .arg("-classpath")
        .arg(classpath(libs))
        .arg("-d")
        .arg(&staging);

    for (relative, content) in spec.sources {
        let file = source_dir.join(relative);
        if let Some(parent) = file.parent() {
            fs::create_dir_all(parent).unwrap();
        }
        fs::write(&file, content).unwrap();
        javac.arg(&file);
    }

    let output = javac.output().expect("javac runs");
    assert!(
        output.status.success(),
        "javac failed for mod {}:\n{}",
        spec.id,
        String::from_utf8_lossy(&output.stderr)
    );

    let mixins = spec
        .mixins
        .iter()
        .map(|name| format!("\"{name}\""))
        .collect::<Vec<_>>()
        .join(",");
    let metadata = format!(
        r#"{{"schemaVersion":1,"id":"{id}","version":"1.0.0","name":"{id}","environment":"*","mixins":[{mixins}]}}"#,
        id = spec.id,
    );
    fs::write(staging.join("fabric.mod.json"), metadata).unwrap();
    for (name, content) in spec.resources {
        if let Some(parent) = staging.join(name).parent() {
            fs::create_dir_all(parent).unwrap();
        }
        fs::write(staging.join(name), content).unwrap();
    }

    fs::create_dir_all(mods_dir).unwrap();
    let jar = mods_dir.join(format!("{}.jar", spec.id));
    let output = Command::new(jar_tool(jdk))
        .arg("--create")
        .arg("--file")
        .arg(&jar)
        .arg("-C")
        .arg(&staging)
        .arg(".")
        .output()
        .expect("jar runs");
    assert!(
        output.status.success(),
        "jar failed for mod {}:\n{}",
        spec.id,
        String::from_utf8_lossy(&output.stderr)
    );
    jar
}

fn work_dir(name: &str) -> PathBuf {
    let path =
        std::env::temp_dir().join(format!("horizon-jvm-mixin-{name}-{}", std::process::id()));
    let _ = fs::remove_dir_all(&path);
    fs::create_dir_all(&path).unwrap();
    path
}

struct Host {
    java: JavaHost,
    log: PathBuf,
    mods_dir: PathBuf,
    jars: Vec<(String, PathBuf)>,
}

/// Builds the mods, starts the host, and runs discovery over them.
fn start(
    name: &str,
    jdk: &java::Jdk,
    libs: &LibSet,
    host_classes: &Path,
    mods: &[Mod<'_>],
    side: Side,
) -> Host {
    let work = work_dir(name);
    let game_dir = work.join("game");
    // Fabric Loader re-discovers mods from `<game dir>/mods` inside `load()`, so the folder the
    // host is asked to discover must be that same folder; a mods dir elsewhere resolves here but
    // is never installed into the loader (and therefore never receives mixins or entrypoints).
    let mods_dir = game_dir.join("mods");
    fs::create_dir_all(&mods_dir).unwrap();

    let mut jars = Vec::new();
    for spec in mods {
        let jar = build_mod(jdk, libs, &work, spec, &mods_dir);
        jars.push((spec.id.to_string(), jar));
    }

    let log = work.join("host.log");
    let mut config = HostConfig::new(
        jdk.java.clone(),
        host_classes,
        libs.jars.clone(),
        &game_dir,
        GAME_VERSION,
        side,
    );
    config.log = Some(log.clone());
    config.discover_timeout = Duration::from_secs(180);

    let mut java = JavaHost::start(config).expect("host starts");
    let discovery = java.discover(&mods_dir).expect("discovery answers");
    assert!(
        discovery.report.ok,
        "discovery failed for {name}: {:?}",
        discovery.report.errors
    );

    Host {
        java,
        log,
        mods_dir,
        jars,
    }
}

impl Host {
    fn jar(&self, id: &str) -> String {
        self.jars
            .iter()
            .find(|(name, _)| name == id)
            .map(|(_, path)| path.to_string_lossy().into_owned())
            .unwrap_or_else(|| panic!("no jar for mod {id} in {}", self.mods_dir.display()))
    }

    /// Loads `class_name` from mod `id`'s jar and invokes its no-arg `method`.
    fn call(&mut self, id: &str, class_name: &str, method: &str) -> String {
        let jar = self.jar(id);
        self.java
            .load_class(&jar, class_name)
            .unwrap_or_else(|e| panic!("load {class_name} from {id}: {e}"));
        self.java
            .invoke_method(class_name, method)
            .unwrap_or_else(|e| panic!("invoke {class_name}.{method}: {e}"))
    }

    /// Shuts the host down and returns what it wrote to stderr.
    fn finish(self) -> String {
        self.java.shutdown(0).expect("host shuts down");
        read_log_soon(&self.log)
    }
}

fn read_log_soon(path: &Path) -> String {
    // The stderr pump is a thread; give it a moment to reach the end of the file.
    let deadline = Instant::now() + Duration::from_secs(2);
    loop {
        let text = fs::read_to_string(path).unwrap_or_default();
        if Instant::now() >= deadline {
            return text;
        }
        std::thread::sleep(Duration::from_millis(25));
    }
}

fn assert_log_contains(log: &str, needle: &str, what: &str) {
    assert!(
        log.contains(needle),
        "{what}: host log never mentioned {needle:?}\n--- host log ---\n{log}"
    );
}

#[test]
fn mixin_configs_transform_and_isolate_failures() {
    let Some((jdk, libs)) = setup() else {
        eprintln!(
            "skipping mixin_configs_transform_and_isolate_failures: needs a JDK 25 and the \
             pinned Fabric libs (set HORIZON_FABRIC_LIBS or create .local/fabric-libs)"
        );
        return;
    };

    let cache = std::env::temp_dir().join(format!("horizon-jvm-mixin-host-{}", std::process::id()));
    let build = horizon_jvm::host::compile(&jdk, &libs.jars, &cache).expect("host sources compile");
    let host_classes = build.classes;

    // ---- 1. All four injection types in one mod/config --------------------------------
    {
        let host = start(
            "all-types",
            &jdk,
            &libs,
            &host_classes,
            &[Mod {
                id: "alltypes",
                mixins: &["mixins.alltypes.json"],
                resources: &[("mixins.alltypes.json", ALL_TYPES_CONFIG)],
                sources: ALL_TYPES_SOURCES,
            }],
            Side::Client,
        );

        let mut host = host;
        for (class, method, expected, label) in [
            (
                "reg.target.InjectTarget",
                "sayHello",
                "Hello from target [mixin]",
                "@Inject",
            ),
            (
                "reg.target.OverwriteTarget",
                "getValue",
                "overwritten",
                "@Overwrite",
            ),
            (
                "reg.target.RedirectTarget",
                "compute",
                "redirected",
                "@Redirect",
            ),
            (
                "reg.target.ShadowTarget",
                "getSecret",
                "hidden",
                "@Shadow accessor",
            ),
        ] {
            let got = host.call("alltypes", class, method);
            assert_eq!(got, expected, "{label} did not transform {class}.{method}");
        }

        let log = host.finish();
        for class in [
            "reg.target.InjectTarget",
            "reg.target.OverwriteTarget",
            "reg.target.RedirectTarget",
            "reg.target.ShadowTarget",
        ] {
            assert_log_contains(&log, &format!("Mixin transformed {class}"), class);
        }
        assert_log_contains(
            &log,
            "registered mixin config mixins.alltypes.json",
            "alltypes",
        );
    }

    // ---- 2. Two mods, two configs: both apply ----------------------------------------
    {
        let mut host = start(
            "multi-mod",
            &jdk,
            &libs,
            &host_classes,
            &[
                Mod {
                    id: "alpha",
                    mixins: &["mixins.alpha.json"],
                    resources: &[("mixins.alpha.json", ALPHA_CONFIG)],
                    sources: ALPHA_SOURCES,
                },
                Mod {
                    id: "beta",
                    mixins: &["mixins.beta.json"],
                    resources: &[("mixins.beta.json", BETA_CONFIG)],
                    sources: BETA_SOURCES,
                },
            ],
            Side::Client,
        );

        assert_eq!(
            host.call("alpha", "reg.target.AlphaTarget", "say"),
            "alpha +alpha"
        );
        assert_eq!(
            host.call("beta", "reg.target.BetaTarget", "say"),
            "beta +beta"
        );

        let log = host.finish();
        assert_log_contains(&log, "registered mixin config mixins.alpha.json", "alpha");
        assert_log_contains(&log, "registered mixin config mixins.beta.json", "beta");
    }

    // ---- 3. Two configs in one mod: both apply ---------------------------------------
    {
        let mut host = start(
            "multi-config",
            &jdk,
            &libs,
            &host_classes,
            &[Mod {
                id: "twoconfigs",
                mixins: &["mixins.first.json", "mixins.second.json"],
                resources: &[
                    ("mixins.first.json", FIRST_CONFIG),
                    ("mixins.second.json", SECOND_CONFIG),
                ],
                sources: TWO_CONFIG_SOURCES,
            }],
            Side::Client,
        );

        assert_eq!(
            host.call("twoconfigs", "reg.target.FirstTarget", "say"),
            "first +first"
        );
        assert_eq!(
            host.call("twoconfigs", "reg.target.SecondTarget", "say"),
            "second +second"
        );

        let log = host.finish();
        assert_log_contains(&log, "registered mixin config mixins.first.json", "first");
        assert_log_contains(&log, "registered mixin config mixins.second.json", "second");
    }

    // ---- 4. Broken configs are isolated; the good mod still transforms ----------------
    {
        let mut host = start(
            "isolation",
            &jdk,
            &libs,
            &host_classes,
            &[
                Mod {
                    id: "missingconfig",
                    mixins: &["mixins.missing.json"],
                    resources: &[],
                    sources: PLAIN_BROKEN_SOURCES,
                },
                Mod {
                    id: "badjson",
                    mixins: &["mixins.badjson.json"],
                    resources: &[("mixins.badjson.json", "{ this is not json")],
                    sources: PLAIN_BROKEN_SOURCES,
                },
                Mod {
                    id: "badmixinclass",
                    mixins: &["mixins.badmixin.json"],
                    resources: &[("mixins.badmixin.json", MISSING_CLASS_CONFIG)],
                    sources: PLAIN_BROKEN_SOURCES,
                },
                Mod {
                    id: "good",
                    mixins: &["mixins.good.json"],
                    resources: &[("mixins.good.json", GOOD_CONFIG)],
                    sources: GOOD_SOURCES,
                },
            ],
            Side::Client,
        );

        assert_eq!(
            host.call("good", "reg.target.GoodTarget", "say"),
            "good +good",
            "a broken config in another mod must not stop this mod's mixin"
        );

        let log = host.finish();
        assert_log_contains(&log, "mixins.missing.json", "missing config is named");
        assert_log_contains(&log, "mixins.badjson.json", "invalid config is named");
        assert_log_contains(&log, "NoSuchMixinClass", "the missing mixin class is named");
    }

    // ---- 5. A mixin targeting a class that does not exist names it and does not spread -
    {
        let mut host = start(
            "invalid-target",
            &jdk,
            &libs,
            &host_classes,
            &[
                Mod {
                    id: "badtarget",
                    mixins: &["mixins.badtarget.json"],
                    resources: &[("mixins.badtarget.json", BAD_TARGET_CONFIG)],
                    sources: BAD_TARGET_SOURCES,
                },
                Mod {
                    id: "good",
                    mixins: &["mixins.good.json"],
                    resources: &[("mixins.good.json", GOOD_CONFIG)],
                    sources: GOOD_SOURCES,
                },
            ],
            Side::Client,
        );

        // The bad mod's own plain class still loads and runs untransformed.
        assert_eq!(
            host.call("badtarget", "reg.target.PlainTarget", "say"),
            "plain",
            "a broken mixin target must not corrupt unrelated classes"
        );
        // And the other mod's mixin still applies.
        assert_eq!(
            host.call("good", "reg.target.GoodTarget", "say"),
            "good +good",
            "a broken mixin target in another mod must not stop this mod's mixin"
        );

        let log = host.finish();
        assert_log_contains(
            &log,
            "reg.target.DoesNotExist",
            "the missing target is named",
        );
        assert_log_contains(&log, "WrongTargetMixin", "the responsible mixin is named");
    }

    // ---- 6. client-only and server-only mixin lists follow the host side --------------
    {
        let mut client = start(
            "sided-client",
            &jdk,
            &libs,
            &host_classes,
            &[Mod {
                id: "sided",
                mixins: &["mixins.sided.json"],
                resources: &[("mixins.sided.json", SIDED_CONFIG)],
                sources: SIDED_SOURCES,
            }],
            Side::Client,
        );
        assert_eq!(
            client.call("sided", "reg.target.ClientSideTarget", "say"),
            "client +client",
            "a client-only mixin must apply on a client host"
        );
        assert_eq!(
            client.call("sided", "reg.target.ServerSideTarget", "say"),
            "server",
            "a server-only mixin must not apply on a client host"
        );
        let log = client.finish();
        assert_log_contains(&log, "mixin side: CLIENT", "client host reports CLIENT");

        let mut server = start(
            "sided-server",
            &jdk,
            &libs,
            &host_classes,
            &[Mod {
                id: "sided",
                mixins: &["mixins.sided.json"],
                resources: &[("mixins.sided.json", SIDED_CONFIG)],
                sources: SIDED_SOURCES,
            }],
            Side::Server,
        );
        assert_eq!(
            server.call("sided", "reg.target.ServerSideTarget", "say"),
            "server +server",
            "a server-only mixin must apply on a server host"
        );
        assert_eq!(
            server.call("sided", "reg.target.ClientSideTarget", "say"),
            "client",
            "a client-only mixin must not apply on a server host"
        );
        let log = server.finish();
        assert_log_contains(&log, "mixin side: SERVER", "server host reports SERVER");
    }
}

// ---- Fixture sources and configs -------------------------------------------------------

const ALL_TYPES_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": [
    "InjectTargetMixin",
    "OverwriteTargetMixin",
    "RedirectTargetMixin",
    "ShadowTargetMixin"
  ]
}"#;

const ALL_TYPES_SOURCES: &[(&str, &str)] = &[
    (
        "reg/target/InjectTarget.java",
        r#"package reg.target;
public class InjectTarget {
    public String sayHello() { return "Hello from target"; }
}"#,
    ),
    (
        "reg/target/OverwriteTarget.java",
        r#"package reg.target;
public class OverwriteTarget {
    public String getValue() { return "original"; }
}"#,
    ),
    (
        "reg/target/RedirectTarget.java",
        r#"package reg.target;
public class RedirectTarget {
    public String compute() { return helper(); }
    String helper() { return "helper"; }
}"#,
    ),
    (
        "reg/target/ShadowTarget.java",
        r#"package reg.target;
public class ShadowTarget {
    private String secret = "hidden";
}"#,
    ),
    (
        "reg/mixin/InjectTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.InjectTarget;
@Mixin(InjectTarget.class)
public class InjectTargetMixin {
    @Inject(method = "sayHello", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " [mixin]");
    }
}"#,
    ),
    (
        "reg/mixin/OverwriteTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import reg.target.OverwriteTarget;
@Mixin(OverwriteTarget.class)
public class OverwriteTargetMixin {
    @Overwrite
    public String getValue() { return "overwritten"; }
}"#,
    ),
    (
        "reg/mixin/RedirectTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import reg.target.RedirectTarget;
@Mixin(RedirectTarget.class)
public class RedirectTargetMixin {
    @Redirect(
        method = "compute",
        at = @At(value = "INVOKE", target = "Lreg/target/RedirectTarget;helper()Ljava/lang/String;"))
    private static String redirectHelper(RedirectTarget instance) {
        return "redirected";
    }
}"#,
    ),
    (
        "reg/mixin/ShadowTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import reg.target.ShadowTarget;
@Mixin(ShadowTarget.class)
public class ShadowTargetMixin {
    @Shadow private String secret;
    public String getSecret() { return secret; }
}"#,
    ),
];

const ALPHA_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["AlphaTargetMixin"]
}"#;

const ALPHA_SOURCES: &[(&str, &str)] = &[
    (
        "reg/target/AlphaTarget.java",
        r#"package reg.target;
public class AlphaTarget {
    public String say() { return "alpha"; }
}"#,
    ),
    (
        "reg/mixin/AlphaTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.AlphaTarget;
@Mixin(AlphaTarget.class)
public class AlphaTargetMixin {
    @Inject(method = "say", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " +alpha");
    }
}"#,
    ),
];

const BETA_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["BetaTargetMixin"]
}"#;

const BETA_SOURCES: &[(&str, &str)] = &[
    (
        "reg/target/BetaTarget.java",
        r#"package reg.target;
public class BetaTarget {
    public String say() { return "beta"; }
}"#,
    ),
    (
        "reg/mixin/BetaTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.BetaTarget;
@Mixin(BetaTarget.class)
public class BetaTargetMixin {
    @Inject(method = "say", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " +beta");
    }
}"#,
    ),
];

const FIRST_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["FirstTargetMixin"]
}"#;

const SECOND_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["SecondTargetMixin"]
}"#;

const TWO_CONFIG_SOURCES: &[(&str, &str)] = &[
    (
        "reg/target/FirstTarget.java",
        r#"package reg.target;
public class FirstTarget {
    public String say() { return "first"; }
}"#,
    ),
    (
        "reg/target/SecondTarget.java",
        r#"package reg.target;
public class SecondTarget {
    public String say() { return "second"; }
}"#,
    ),
    (
        "reg/mixin/FirstTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.FirstTarget;
@Mixin(FirstTarget.class)
public class FirstTargetMixin {
    @Inject(method = "say", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " +first");
    }
}"#,
    ),
    (
        "reg/mixin/SecondTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.SecondTarget;
@Mixin(SecondTarget.class)
public class SecondTargetMixin {
    @Inject(method = "say", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " +second");
    }
}"#,
    ),
];

/// A plain class in the mods whose config is broken; it must still be loadable.
const PLAIN_BROKEN_SOURCES: &[(&str, &str)] = &[(
    "reg/target/PlainTarget.java",
    r#"package reg.target;
public class PlainTarget {
    public String say() { return "plain"; }
}"#,
)];

const MISSING_CLASS_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["NoSuchMixinClass"]
}"#;

const GOOD_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["GoodTargetMixin"]
}"#;

const GOOD_SOURCES: &[(&str, &str)] = &[
    (
        "reg/target/GoodTarget.java",
        r#"package reg.target;
public class GoodTarget {
    public String say() { return "good"; }
}"#,
    ),
    (
        "reg/mixin/GoodTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.GoodTarget;
@Mixin(GoodTarget.class)
public class GoodTargetMixin {
    @Inject(method = "say", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " +good");
    }
}"#,
    ),
];

const BAD_TARGET_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["WrongTargetMixin"]
}"#;

const BAD_TARGET_SOURCES: &[(&str, &str)] = &[
    (
        "reg/target/PlainTarget.java",
        r#"package reg.target;
public class PlainTarget {
    public String say() { return "plain"; }
}"#,
    ),
    (
        "reg/mixin/WrongTargetMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "reg.target.DoesNotExist")
public class WrongTargetMixin {
    @Inject(method = "anything", at = @At("HEAD"))
    private void anything(CallbackInfo ci) {
    }
}"#,
    ),
];

const SIDED_CONFIG: &str = r#"{
  "required": true,
  "minVersion": "0.8",
  "package": "reg.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": [],
  "client": ["ClientSideMixin"],
  "server": ["ServerSideMixin"]
}"#;

const SIDED_SOURCES: &[(&str, &str)] = &[
    (
        "reg/target/ClientSideTarget.java",
        r#"package reg.target;
public class ClientSideTarget {
    public String say() { return "client"; }
}"#,
    ),
    (
        "reg/target/ServerSideTarget.java",
        r#"package reg.target;
public class ServerSideTarget {
    public String say() { return "server"; }
}"#,
    ),
    (
        "reg/mixin/ClientSideMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.ClientSideTarget;
@Mixin(ClientSideTarget.class)
public class ClientSideMixin {
    @Inject(method = "say", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " +client");
    }
}"#,
    ),
    (
        "reg/mixin/ServerSideMixin.java",
        r#"package reg.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reg.target.ServerSideTarget;
@Mixin(ServerSideTarget.class)
public class ServerSideMixin {
    @Inject(method = "say", at = @At("RETURN"), cancellable = true)
    private void modify(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " +server");
    }
}"#,
    ),
];
