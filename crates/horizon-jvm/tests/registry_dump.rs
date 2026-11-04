use std::path::PathBuf;
use std::process::Command;

#[test]
fn registry_dump_binary_parses_facts_json() {
    let dir =
        std::env::temp_dir().join(format!("horizon-registry-dump-test-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();

    let facts = r#"{
        "states": [
            ["minecraft:air", false, false, "", {"id": "minecraft:air"}],
            ["minecraft:stone", false, false, "", {"id": "minecraft:stone"}]
        ],
        "defaults": {
            "minecraft:air": 0,
            "minecraft:stone": 1
        }
    }"#;

    std::fs::write(dir.join("facts.json"), facts).unwrap();

    let exe = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("target")
        .join("debug")
        .join(if cfg!(windows) {
            "horizon-registry-dump.exe"
        } else {
            "horizon-registry-dump"
        });

    if !exe.is_file() {
        // Binary not built yet; skip test.
        return;
    }

    let output = Command::new(&exe)
        .arg("--resources")
        .arg(&dir)
        .output()
        .expect("run binary");

    assert!(
        output.status.success(),
        "binary failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );

    let stdout = String::from_utf8_lossy(&output.stdout);
    let json: serde_json::Value = serde_json::from_str(&stdout).expect("parse json");

    assert_eq!(json["kind"], "block-states");
    assert_eq!(json["stateCount"], 2);
    assert_eq!(json["distinctBlocks"], 2);

    std::fs::remove_dir_all(&dir).unwrap();
}
