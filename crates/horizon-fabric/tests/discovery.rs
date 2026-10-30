//! End-to-end: real zip files in a temporary `mods/` folder, read the way a player's folder is.
//!
//! The jars are built here, so the test runs with no network, no JDK and no Minecraft files.

use horizon_fabric::{Options, Side, Status, check};
use std::io::Write;
use std::path::{Path, PathBuf};
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

/// A class file whose constant pool names `names`; enough for the census, which reads the pool and
/// nothing else.
fn class(names: &[&str]) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(&0xCAFE_BABEu32.to_be_bytes());
    out.extend_from_slice(&0u16.to_be_bytes());
    out.extend_from_slice(&65u16.to_be_bytes());
    out.extend_from_slice(&((1 + names.len() * 2) as u16).to_be_bytes());
    let mut index = 1u16;
    for name in names {
        out.push(1);
        out.extend_from_slice(&(name.len() as u16).to_be_bytes());
        out.extend_from_slice(name.as_bytes());
        out.push(7);
        out.extend_from_slice(&index.to_be_bytes());
        index += 2;
    }
    out
}

fn write_jar(path: &Path, entries: &[(&str, &[u8])]) {
    let file = std::fs::File::create(path).unwrap();
    let mut zip = ZipWriter::new(file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
    for (name, bytes) in entries {
        zip.start_file(name, options).unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
}

fn nested_jar() -> Vec<u8> {
    let mut buffer = std::io::Cursor::new(Vec::new());
    {
        let options = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
        let mut zip = ZipWriter::new(&mut buffer);
        zip.start_file("fabric.mod.json", options).unwrap();
        zip.write_all(
            br#"{"schemaVersion":1,"id":"inner","version":"1.0.0",
                 "entrypoints":{"main":["inner.Main"]}}"#,
        )
        .unwrap();
        zip.start_file("inner/Main.class", options).unwrap();
        zip.write_all(&class(&["java/lang/Object"])).unwrap();
        zip.finish().unwrap();
    }
    buffer.into_inner()
}

fn mods_folder(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("horizon-fabric-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

#[test]
fn a_mods_folder_becomes_a_load_plan() {
    let dir = mods_folder("plan");

    write_jar(
        &dir.join("fabric-api.jar"),
        &[
            (
                "fabric.mod.json",
                br#"{"schemaVersion":1,"id":"fabric-api","version":"0.100.0+26.3",
                     "name":"Fabric API",
                     "entrypoints":{"main":["api.Main"]},
                     "mixins":["fabric-api.mixins.json"],
                     "depends":{"minecraft":">=26.1 <26.4","fabricloader":">=0.16"}}"#,
            ),
            (
                "api.Main.class",
                &class(&[
                    "java/lang/Object",
                    "net/minecraft/world/item/ItemStack",
                    "net/minecraft/client/Minecraft",
                    "net/minecraft/client/renderer/LevelRenderer",
                ]),
            ),
            ("fabric-api.mixins.json", b"{}"),
        ],
    );
    let nested = nested_jar();
    write_jar(
        &dir.join("outer.jar"),
        &[
            (
                "fabric.mod.json",
                br#"{"schemaVersion":1,"id":"outer","version":"2.0.0",
                     "entrypoints":{"main":["outer.Main"],"client":["outer.Client::init"]},
                     "jars":[{"file":"META-INF/jars/inner.jar"}],
                     "depends":{"inner":">=1.0"}}"#,
            ),
            ("outer/Main.class", &class(&["java/lang/Object"])),
            ("outer/Client.class", &class(&["java/lang/Object"])),
            ("META-INF/jars/inner.jar", &nested),
        ],
    );
    // A plain library: no metadata, ignored the way the loader ignores it.
    write_jar(&dir.join("library.jar"), &[("lib/Lib.class", b"class")]);
    // Two copies of one id, as Sodium and Fabric API both ship `fabric-api-base`. The newer one
    // loads; the older one is inactive, which is a normal folder, not an error.
    for (file, version) in [("dup.jar", "1.0.0"), ("dup-newer.jar", "2.0.0")] {
        write_jar(
            &dir.join(file),
            &[
                (
                    "fabric.mod.json",
                    format!(
                        r#"{{"schemaVersion":1,"id":"dup","version":"{version}",
                             "entrypoints":{{"main":["dup.Main"]}}}}"#
                    )
                    .as_bytes(),
                ),
                ("dup/Main.class", &class(&["java/lang/Object"])),
            ],
        );
    }
    // A jar the reader must refuse, with a reason naming the file.
    write_jar(
        &dir.join("broken.jar"),
        &[("fabric.mod.json", b"{ this is not json }")],
    );

    let report = check(&dir, Options::default());
    assert_eq!(report.jars, 6);
    assert!(!report.ok);
    assert!(
        report
            .errors
            .iter()
            .any(|error| error.message.contains("broken.jar")),
        "{:?}",
        report.errors
    );
    assert!(
        !report
            .errors
            .iter()
            .any(|error| error.message.contains("two mods")),
        "a duplicate id is resolved, not refused: {:?}",
        report.errors
    );

    let api = report.mods.iter().find(|m| m.id == "fabric-api").unwrap();
    assert_eq!(api.status, Status::Loads);
    assert_eq!(api.mixin_configs, 1);
    assert_eq!(api.nested_jars, 0);
    assert_eq!(api.game_classes, 3, "three net.minecraft classes");
    assert_eq!(api.graphics_classes, 2, "Minecraft and LevelRenderer");

    // A nested jar is a mod of its own, and it loads before what needs it. `outer.Client::init` is
    // a method-reference entrypoint: its class exists, so the report must not call it missing.
    assert!(report.mods.iter().any(|m| m.id == "inner"));
    assert_eq!(report.order, ["dup", "fabric-api", "inner", "outer"]);

    let dup: Vec<&horizon_fabric::ModStatus> =
        report.mods.iter().filter(|m| m.id == "dup").collect();
    assert_eq!(dup.len(), 2);
    assert!(
        dup.iter()
            .any(|m| m.status == Status::Loads && m.version == "2.0.0")
    );
    assert!(dup.iter().any(|m| m.status == Status::Skipped));

    // The library is named in a warning, not silently dropped.
    assert!(
        report
            .warnings
            .iter()
            .any(|warning| warning.message.contains("library.jar")),
        "{:?}",
        report.warnings
    );

    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_client_only_mod_is_skipped_on_a_server() {
    let dir = mods_folder("sides");
    write_jar(
        &dir.join("hud.jar"),
        &[
            (
                "fabric.mod.json",
                br#"{"schemaVersion":2,"id":"hud","version":"1.0.0","environment":"client",
                     "entrypoints":{"client":["hud.Main"]}}"#,
            ),
            ("hud/Main.class", &class(&["java/lang/Object"])),
        ],
    );

    let client = check(&dir, Options::default());
    assert!(client.ok);
    assert_eq!(client.order, ["hud"]);

    let server = check(
        &dir,
        Options {
            side: Side::Server,
            ..Options::default()
        },
    );
    assert!(server.ok, "a client mod is not an error on a server");
    assert!(server.order.is_empty());
    assert_eq!(server.mods[0].status, Status::Skipped);

    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_missing_folder_is_an_empty_plan_with_a_warning() {
    let report = check(Path::new("this-folder-is-not-here"), Options::default());
    assert!(report.ok);
    assert!(report.order.is_empty());
    assert!(report.warnings[0].message.contains("does not exist"));
}
