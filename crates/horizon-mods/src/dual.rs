//! Unified mod discovery: both native `.hmod` content mods and Java Fabric `.jar` mods.
//!
//! Horizon's own mod system (`.hmod`, `docs/mods.md`) and the Fabric compatibility layer
//! (`crates/horizon-fabric`, `docs/fabric-compatibility/`) stay separate at runtime — a `.hmod`
//! is data and WASM, a `.jar` is Java bytecode — but a single directory scan must find both,
//! and a single UI must list both. This module is that scan: it does not load anything, it only
//! reports what is on disk and which loader owns it, so neither loader ever receives the other's
//! files (the spec's "do not send JAR files through the HMOD loader" rule).

use std::path::{Path, PathBuf};

/// Which loader owns a discovered mod.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ModFormat {
    /// Horizon native content mod (`.hmod` or a folder with `horizon-mod.toml`).
    Hmod,
    /// Java Fabric mod (`.jar`).
    FabricJar,
}

impl ModFormat {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Hmod => "hmod",
            Self::FabricJar => "fabric-jar",
        }
    }
}

/// A mod found on disk, before any loading or validation.
#[derive(Clone, Debug)]
pub struct DiscoveredMod {
    pub path: PathBuf,
    pub format: ModFormat,
}

impl DiscoveredMod {
    pub fn is_native(&self) -> bool {
        self.format == ModFormat::Hmod
    }

    pub fn is_fabric(&self) -> bool {
        self.format == ModFormat::FabricJar
    }
}

/// Scans `dir` for both native (`.hmod` / folder with `horizon-mod.toml`) and Fabric (`.jar`)
/// mods. Returns them sorted by path. A missing directory is an empty list, not an error — a
/// player with no mods is not doing anything wrong.
pub fn scan(dir: &Path) -> Vec<DiscoveredMod> {
    let mut found: Vec<DiscoveredMod> = Vec::new();

    let Ok(read) = std::fs::read_dir(dir) else {
        return found;
    };

    for entry in read.flatten() {
        let path = entry.path();
        if !path.is_file() && !path.is_dir() {
            continue;
        }

        if path.is_dir() && path.join("horizon-mod.toml").is_file() {
            found.push(DiscoveredMod {
                path,
                format: ModFormat::Hmod,
            });
            continue;
        }

        match path.extension().and_then(|e| e.to_str()) {
            Some("hmod") => found.push(DiscoveredMod {
                path,
                format: ModFormat::Hmod,
            }),
            Some("jar") if path.is_file() => found.push(DiscoveredMod {
                path,
                format: ModFormat::FabricJar,
            }),
            _ => {}
        }
    }

    found.sort_by(|a, b| a.path.cmp(&b.path));
    found
}

/// Partitions a scanned list into native and Fabric mods, so each loader only ever sees its own.
pub fn partition(mods: &[DiscoveredMod]) -> (Vec<&DiscoveredMod>, Vec<&DiscoveredMod>) {
    let mut native = Vec::new();
    let mut fabric = Vec::new();
    for m in mods {
        if m.is_native() {
            native.push(m);
        } else {
            fabric.push(m);
        }
    }
    (native, fabric)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn scan_finds_both_formats_and_partitions_them() {
        let dir = std::env::temp_dir().join(format!("horizon-dual-scan-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        fs::write(dir.join("native.hmod"), b"").unwrap();
        fs::write(dir.join("fabric.jar"), b"").unwrap();
        fs::create_dir_all(dir.join("folder_mod")).unwrap();
        fs::write(dir.join("folder_mod").join("horizon-mod.toml"), b"id = \"x\"\n").unwrap();
        fs::write(dir.join("readme.txt"), b"").unwrap();

        let found = scan(&dir);
        assert_eq!(found.len(), 3);
        let (native, fabric) = partition(&found);
        assert_eq!(native.len(), 2);
        assert_eq!(fabric.len(), 1);

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn missing_directory_is_empty() {
        let dir = std::env::temp_dir().join("horizon-dual-scan-nope-xyz");
        assert!(scan(&dir).is_empty());
    }
}
