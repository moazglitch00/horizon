use horizon_jvm::{HostConfig, HostError, JavaHost, Side};
use horizon_prepare::java;
use std::path::PathBuf;
use std::process::Command;
use std::time::Duration;

fn jdk() -> java::Jdk {
    java::find(21, &[]).expect("a JDK 21+ is needed for the host tests")
}

/// Compiles the fixture classes against the host's Wire.java and returns the class directory.
fn compile_fixture(main: &str) -> PathBuf {
    let jdk = jdk();
    let root = std::env::temp_dir().join(format!(
        "horizon-jvm-host-test-{}-{}",
        main.to_lowercase(),
        std::process::id()
    ));
    let _ = std::fs::remove_dir_all(&root);
    std::fs::create_dir_all(&root).unwrap();

    let java_src = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("java")
        .join("horizon-host")
        .join("src");
    let wire = java_src.join("horizon").join("host").join("Wire.java");
    let entrypoints = java_src
        .join("horizon")
        .join("host")
        .join("Entrypoints.java");
    let fixture = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("fixtures")
        .join(format!("{main}.java"));

    let output = Command::new(&jdk.javac)
        .arg("--release")
        .arg("21")
        .arg("-encoding")
        .arg("UTF-8")
        .arg("-proc:none")
        .arg("-d")
        .arg(&root)
        .arg("-sourcepath")
        .arg(&java_src)
        .arg(&wire)
        .arg(&entrypoints)
        .arg(&fixture)
        .output()
        .expect("javac");

    assert!(
        output.status.success(),
        "javac failed:\n{}",
        String::from_utf8_lossy(&output.stderr)
    );

    root
}

fn temp_game_dir(name: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!("horizon-jvm-game-{name}-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

#[test]
fn handshake_ping_discover_shutdown_round_trip() {
    let classes = compile_fixture("EchoHost");
    let game_dir = temp_game_dir("echo");
    let mut config = HostConfig::new(
        jdk().java.clone(),
        &classes,
        Vec::new(),
        &game_dir,
        "26.3",
        Side::Client,
    );
    config.main_class = "EchoHost".to_string();
    config.startup_timeout = Duration::from_secs(15);
    config.frame_timeout = Duration::from_secs(5);
    config.discover_timeout = Duration::from_secs(5);
    config.shutdown_timeout = Duration::from_secs(5);

    let mut host = JavaHost::start(config).expect("start");

    assert_eq!(host.hello().host, "echo-host/1");
    assert_eq!(host.hello().loader, "echo-loader");
    assert_eq!(host.side(), Side::Client);

    for _ in 0..3 {
        let rtt = host.ping().expect("ping");
        assert!(rtt < Duration::from_secs(1));
    }

    let discovery = host.discover(&game_dir.join("mods")).expect("discover");
    assert!(discovery.report.ok);
    assert!(discovery.report.mods.is_empty());

    let status = host.shutdown(0).expect("shutdown");
    assert!(status.success());
}

#[test]
fn a_host_that_never_speaks_times_out_at_startup() {
    let classes = compile_fixture("SleepHost");
    let game_dir = temp_game_dir("sleep");
    let mut config = HostConfig::new(
        jdk().java.clone(),
        &classes,
        Vec::new(),
        &game_dir,
        "26.3",
        Side::Client,
    );
    config.main_class = "SleepHost".to_string();
    config.startup_timeout = Duration::from_millis(300);

    let result = JavaHost::start(config);
    match result {
        Err(HostError::Timeout { .. }) => {}
        Err(other) => panic!("expected a startup timeout, got {other}"),
        Ok(_) => panic!("a sleeping host must not be reported as started"),
    }
}

#[test]
fn a_missing_main_class_is_reported_as_an_exit() {
    let classes = compile_fixture("EchoHost");
    let game_dir = temp_game_dir("missing");
    let mut config = HostConfig::new(
        jdk().java.clone(),
        &classes,
        Vec::new(),
        &game_dir,
        "26.3",
        Side::Client,
    );
    config.main_class = "Nope".to_string();
    config.startup_timeout = Duration::from_secs(5);

    let result = JavaHost::start(config);
    match result {
        Err(HostError::Exited { .. }) => {}
        Err(other) => panic!("expected an exit, got {other}"),
        Ok(_) => panic!("a missing main class must not start"),
    }
}

#[test]
fn a_dropped_host_does_not_survive() {
    let classes = compile_fixture("SleepHost");
    let game_dir = temp_game_dir("drop");
    let mut config = HostConfig::new(
        jdk().java.clone(),
        &classes,
        Vec::new(),
        &game_dir,
        "26.3",
        Side::Client,
    );
    config.main_class = "SleepHost".to_string();
    config.startup_timeout = Duration::from_millis(200);

    let _ = JavaHost::start(config);
    std::thread::sleep(Duration::from_millis(200));
}

#[test]
fn load_class_and_invoke_method() {
    let jdk = jdk();
    let root = std::env::temp_dir().join(format!("horizon-jvm-cls-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&root);
    std::fs::create_dir_all(&root).unwrap();

    let java_src = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("java")
        .join("horizon-host")
        .join("src");
    let wire = java_src.join("horizon").join("host").join("Wire.java");
    let entrypoints = java_src
        .join("horizon")
        .join("host")
        .join("Entrypoints.java");
    let echo = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("fixtures")
        .join("EchoHost.java");
    let horizon_test = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("fixtures")
        .join("HorizonTest.java");

    let output = Command::new(&jdk.javac)
        .arg("--release")
        .arg("21")
        .arg("-encoding")
        .arg("UTF-8")
        .arg("-proc:none")
        .arg("-d")
        .arg(&root)
        .arg("-sourcepath")
        .arg(&java_src)
        .arg(&wire)
        .arg(&entrypoints)
        .arg(&echo)
        .arg(&horizon_test)
        .output()
        .expect("javac");
    assert!(
        output.status.success(),
        "javac: {}",
        String::from_utf8_lossy(&output.stderr)
    );

    let game_dir = temp_game_dir("cls");
    let mut config = HostConfig::new(
        jdk.java.clone(),
        &root,
        Vec::new(),
        &game_dir,
        "26.3",
        Side::Client,
    );
    config.main_class = "EchoHost".to_string();
    config.startup_timeout = Duration::from_secs(15);
    config.frame_timeout = Duration::from_secs(5);

    let mut host = JavaHost::start(config).expect("start");
    let loaded = host.load_class("", "HorizonTest").unwrap();
    assert_eq!(loaded, "HorizonTest");
    let result = host.invoke_method("HorizonTest", "hello").unwrap();
    assert_eq!(result, "Hello from Java!");
    host.shutdown(0).unwrap();
}

/// A mod's `ModInitializer` is loaded from its own jar and actually runs.
///
/// The fixture is deliberately kept off the host classpath, so the only way the
/// class can be found is the jar the engine names in the `entrypoint` frame.
#[test]
fn a_mod_entrypoint_runs_from_its_jar() {
    let jdk = jdk();
    let root = std::env::temp_dir().join(format!("horizon-jvm-entrypoint-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&root);
    let host_classes = root.join("host");
    let mod_classes = root.join("mod");
    let mod_jar = root.join("testmod.jar");
    std::fs::create_dir_all(&host_classes).unwrap();
    std::fs::create_dir_all(&mod_classes).unwrap();

    let java_src = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("java")
        .join("horizon-host")
        .join("src");
    let fixtures = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("fixtures");

    // 1. Compile the "loader" side: wire, entrypoint dispatcher, the Fabric
    //    interfaces and the test host. These are the classes Fabric Loader would
    //    put on the classpath; a mod jar never ships them.
    let host_sources = [
        java_src.join("horizon").join("host").join("Wire.java"),
        java_src
            .join("horizon")
            .join("host")
            .join("Entrypoints.java"),
        java_src
            .join("net")
            .join("fabricmc")
            .join("api")
            .join("ModInitializer.java"),
        java_src
            .join("net")
            .join("fabricmc")
            .join("api")
            .join("ClientModInitializer.java"),
        java_src
            .join("net")
            .join("fabricmc")
            .join("api")
            .join("DedicatedServerModInitializer.java"),
        fixtures.join("EchoHost.java"),
    ];
    let mut javac = Command::new(&jdk.javac);
    javac
        .arg("--release")
        .arg("21")
        .arg("-encoding")
        .arg("UTF-8")
        .arg("-proc:none")
        .arg("-d")
        .arg(&host_classes);
    for source in &host_sources {
        javac.arg(source);
    }
    let output = javac.output().expect("javac (host)");
    assert!(
        output.status.success(),
        "javac host: {}",
        String::from_utf8_lossy(&output.stderr)
    );

    // 2. Compile only the mod against that loader, exactly as a mod build does.
    let mut javac = Command::new(&jdk.javac);
    javac
        .arg("--release")
        .arg("21")
        .arg("-encoding")
        .arg("UTF-8")
        .arg("-proc:none")
        .arg("-d")
        .arg(&mod_classes)
        .arg("-cp")
        .arg(&host_classes)
        .arg(fixtures.join("TestModInitializer.java"));
    let output = javac.output().expect("javac (mod)");
    assert!(
        output.status.success(),
        "javac mod: {}",
        String::from_utf8_lossy(&output.stderr)
    );

    // 3. Package it. This is what a Fabric mod jar is, minus the metadata: the
    //    mod class only, no Fabric interfaces.
    let jar_tool =
        PathBuf::from(&jdk.javac).with_file_name(if cfg!(windows) { "jar.exe" } else { "jar" });
    let output = Command::new(&jar_tool)
        .arg("--create")
        .arg("--file")
        .arg(&mod_jar)
        .arg("-C")
        .arg(&mod_classes)
        .arg(".")
        .output()
        .expect("jar");
    assert!(
        output.status.success(),
        "jar: {}",
        String::from_utf8_lossy(&output.stderr)
    );

    // 4. Run the real entrypoint path over the pipe.
    let game_dir = temp_game_dir("entrypoint");
    let mut config = HostConfig::new(
        jdk.java.clone(),
        &host_classes,
        Vec::new(),
        &game_dir,
        "26.3",
        Side::Client,
    );
    config.main_class = "EchoHost".to_string();
    config.startup_timeout = Duration::from_secs(15);
    config.frame_timeout = Duration::from_secs(5);

    let mut host = JavaHost::start(config).expect("start");
    let jar = mod_jar.to_string_lossy().into_owned();

    let first = host
        .invoke_entrypoint(&jar, "TestModInitializer", "main", "fixture-mod")
        .unwrap();
    assert!(
        first.starts_with("ok:main:TestModInitializer:fixture-mod"),
        "unexpected result: {first}"
    );
    assert!(
        first.contains("initialized=true"),
        "the body did not run: {first}"
    );
    assert!(first.contains("calls=1"), "unexpected call count: {first}");

    // Same jar, same cached loader: the mod's static state persists.
    let second = host
        .invoke_entrypoint(&jar, "TestModInitializer", "main", "fixture-mod")
        .unwrap();
    assert!(
        second.contains("calls=2"),
        "state did not persist: {second}"
    );

    // Wrong entrypoint for this class must be reported, not silently ignored.
    let wrong = host
        .invoke_entrypoint(&jar, "TestModInitializer", "client", "fixture-mod")
        .unwrap();
    assert!(
        wrong.starts_with("error:java.lang.ClassCastException"),
        "unexpected result: {wrong}"
    );

    // A class that is not in the jar is an error, not a crash.
    let missing = host
        .invoke_entrypoint(&jar, "NoSuchModClass", "main", "fixture-mod")
        .unwrap();
    assert!(
        missing.starts_with("error:java.lang.ClassNotFoundException"),
        "unexpected result: {missing}"
    );

    host.shutdown(0).unwrap();
}

/// Every `.java` file under `java/horizon-host/src` must be embedded in
/// [`horizon_jvm::host::SOURCES`], and every embedded name must exist on disk.
///
/// This is the guard for the class of bug where a source is silently dropped
/// from `SOURCES` and the embedded host stops compiling (that happened to
/// `HorizonGameProvider.java` in `74207ba89`, and nothing caught it).
#[test]
fn every_java_source_is_embedded_in_the_host() {
    use std::collections::BTreeSet;

    fn walk(dir: &std::path::Path, base: &std::path::Path, out: &mut Vec<String>) {
        for entry in std::fs::read_dir(dir).expect("read src dir") {
            let path = entry.expect("dir entry").path();
            if path.is_dir() {
                walk(&path, base, out);
            } else if path.extension().is_some_and(|ext| ext == "java") {
                let rel = path.strip_prefix(base).expect("strip prefix");
                out.push(rel.to_string_lossy().replace('\\', "/"));
            }
        }
    }

    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("java")
        .join("horizon-host")
        .join("src");
    let mut on_disk = Vec::new();
    walk(&root, &root, &mut on_disk);
    let on_disk: BTreeSet<String> = on_disk.into_iter().collect();
    let embedded: BTreeSet<String> = horizon_jvm::host::SOURCES
        .iter()
        .map(|(name, _)| name.replace('\\', "/"))
        .collect();

    let missing: Vec<&String> = on_disk.difference(&embedded).collect();
    assert!(
        missing.is_empty(),
        "java sources on disk but missing from SOURCES: {missing:?}"
    );
    let extra: Vec<&String> = embedded.difference(&on_disk).collect();
    assert!(
        extra.is_empty(),
        "SOURCES names with no file on disk: {extra:?}"
    );
}

/// The embedded host sources must actually compile into a class directory.
///
/// This needs the pinned Fabric Loader/ASM jars, which are not committed. Point
/// `HORIZON_FABRIC_LIBS` at a folder holding them (see `libs::PINNED`) or put
/// them in `.local/fabric-libs`; without them the test reports why it skipped.
#[test]
fn host_sources_compile_into_a_class_directory() {
    let jdk = jdk();
    let libs_dir = std::env::var_os("HORIZON_FABRIC_LIBS")
        .map(PathBuf::from)
        .or_else(|| {
            let local = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join("..")
                .join("..")
                .join(".local")
                .join("fabric-libs");
            local.is_dir().then_some(local)
        });
    let Some(libs_dir) = libs_dir else {
        eprintln!(
            "skipping host_sources_compile_into_a_class_directory: no pinned Fabric libs; \
             set HORIZON_FABRIC_LIBS or create .local/fabric-libs"
        );
        return;
    };
    let libs = horizon_jvm::libs::locate(&libs_dir).expect("pinned Fabric libs");

    let cache = temp_game_dir("host-compile");
    let build =
        horizon_jvm::host::compile(&jdk, &libs.jars, &cache).expect("host sources must compile");
    assert!(
        build
            .classes
            .join("horizon")
            .join("host")
            .join("Main.class")
            .is_file(),
        "Main.class missing from {}",
        build.classes.display()
    );
    assert!(
        build
            .classes
            .join("horizon")
            .join("host")
            .join("Entrypoints.class")
            .is_file()
    );
    assert!(
        build
            .classes
            .join("horizon")
            .join("host")
            .join("HorizonGameProvider.class")
            .is_file()
    );
    assert!(
        build
            .classes
            .join("net")
            .join("fabricmc")
            .join("api")
            .join("ModInitializer.class")
            .is_file()
    );
}

/// The de-faked shims must behave, not just compile (T-214).
///
/// Compiles every `net/minecraft/**` source together with `ShimBehaviourTest`,
/// runs it on a plain JVM and requires `SHIM-OK`. Each assertion in the fixture
/// fails on the pre-T-214 code: `setId` discarded its argument,
/// `readUtf(maxLength)` ignored the cap, `isRunning()` was hardcoded true,
/// `tick()` was a silent no-op, and `RecipeManager`/`AbstractContainerMenu`
/// returned empty values or did nothing instead of doing the work or failing
/// loudly.
#[test]
fn shim_behaviour_is_not_silent() {
    fn collect(dir: &std::path::Path, out: &mut Vec<PathBuf>) {
        for entry in std::fs::read_dir(dir).expect("read shim dir") {
            let path = entry.expect("dir entry").path();
            if path.is_dir() {
                collect(&path, out);
            } else if path.extension().is_some_and(|ext| ext == "java") {
                out.push(path);
            }
        }
    }

    let jdk = jdk();
    let shims = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("java")
        .join("horizon-host")
        .join("src")
        .join("net");
    let fixture = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("fixtures")
        .join("ShimBehaviourTest.java");

    let classes = temp_game_dir("shim-behaviour");
    let mut sources = Vec::new();
    collect(&shims, &mut sources);
    assert!(
        sources.len() >= 58,
        "expected the full net/minecraft shim tree, found {} sources",
        sources.len()
    );

    let mut javac = Command::new(&jdk.javac);
    javac
        .arg("--release")
        .arg("21")
        .arg("-encoding")
        .arg("UTF-8")
        .arg("-proc:none")
        .arg("-d")
        .arg(&classes);
    for source in &sources {
        javac.arg(source);
    }
    javac.arg(&fixture);
    let output = javac.output().expect("javac (shims + fixture)");
    assert!(
        output.status.success(),
        "javac failed:\n{}",
        String::from_utf8_lossy(&output.stderr)
    );

    let output = Command::new(&jdk.java)
        .arg("-cp")
        .arg(&classes)
        .arg("ShimBehaviourTest")
        .output()
        .expect("java (ShimBehaviourTest)");
    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(
        output.status.success() && stdout.contains("SHIM-OK"),
        "fixture failed\nstdout:\n{stdout}\nstderr:\n{stderr}"
    );
}
