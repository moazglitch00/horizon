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

    let wire = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("java")
        .join("horizon-host")
        .join("src")
        .join("horizon")
        .join("host")
        .join("Wire.java");
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
        .arg(&wire)
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

    let wire = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..").join("..").join("java").join("horizon-host").join("src")
        .join("horizon").join("host").join("Wire.java");
    let echo = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests").join("fixtures").join("EchoHost.java");
    let horizon_test = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests").join("fixtures").join("HorizonTest.java");

    let output = Command::new(&jdk.javac)
        .arg("--release").arg("21").arg("-encoding").arg("UTF-8")
        .arg("-proc:none").arg("-d").arg(&root)
        .arg(&wire).arg(&echo).arg(&horizon_test)
        .output().expect("javac");
    assert!(output.status.success(), "javac: {}", String::from_utf8_lossy(&output.stderr));

    let game_dir = temp_game_dir("cls");
    let mut config = HostConfig::new(
        jdk.java.clone(), &root, Vec::new(), &game_dir, "26.3", Side::Client,
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
