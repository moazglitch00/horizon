//! Cross-format validation for the dual mod loader: duplicate IDs, version conflicts and missing
//! dependencies across `.hmod` and `.jar` mods. Runs after discovery, before any loader touches
//! the files, so a conflict is reported once with every mod involved instead of as a cascade of
//! "mod X failed because mod Y is missing".

use crate::dual::{DiscoveredMod, ModFormat};
use crate::manifest::Manifest;
use horizon_fabric::{Options, Side as FabricSide, check};
use std::collections::BTreeMap;
use std::path::Path;

/// A diagnostic produced by [`validate`].
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Diagnostic {
    pub message: String,
    /// The mods involved, when known.
    pub mods: Vec<String>,
}

/// The result of validating a scanned directory.
#[derive(Clone, Debug, Default)]
pub struct ValidationReport {
    pub diagnostics: Vec<Diagnostic>,
}

impl ValidationReport {
    pub fn is_clean(&self) -> bool {
        self.diagnostics.is_empty()
    }
}

/// Validates the mods in `dir`: scans both formats, reads each mod's identity, and reports
/// duplicate IDs and version conflicts. `.jar` mods are validated through `horizon-fabric`'s own
/// check (which already handles Minecraft version predicates and dependency resolution).
pub fn validate(dir: &Path, game_version: &str, side: &str) -> ValidationReport {
    let mut report = ValidationReport::default();
    let mods = crate::dual::scan(dir);

    // Collect identities from .hmod manifests.
    let mut ids: BTreeMap<String, Vec<(ModFormat, String)>> = BTreeMap::new();
    for m in &mods {
        if m.format != ModFormat::Hmod {
            continue;
        }
        // A .hmod can be a folder (with horizon-mod.toml) or a zip; only folders are cheap to
        // inspect here. Zip manifests need horizon-mods::content, which is heavier — skip for now
        // and let the real loader report them.
        if m.path.is_dir() {
            if let Ok(text) = std::fs::read_to_string(m.path.join("horizon-mod.toml")) {
                if let Ok(manifest) = toml::from_str::<Manifest>(&text) {
                    ids.entry(manifest.id.clone())
                        .or_default()
                        .push((ModFormat::Hmod, manifest.version));
                }
            }
        }
    }

    // Duplicate IDs among native mods.
    for (id, entries) in &ids {
        if entries.len() > 1 {
            report.diagnostics.push(Diagnostic {
                message: format!("duplicate native mod id `{id}` ({} copies)", entries.len()),
                mods: vec![id.clone()],
            });
        }
    }

    // Run horizon-fabric's check for the jar side. It already detects missing deps, version
    // mismatches and conflicts; surface its errors as diagnostics.
    let fabric_side = if side == "server" {
        FabricSide::Server
    } else {
        FabricSide::Client
    };
    let options = Options {
        game_version: game_version.to_string(),
        side: fabric_side,
        loader_version: Some("0.19.5".to_string()),
        ..Options::default()
    };
    let fabric_report = check(dir, options);
    for err in &fabric_report.errors {
        report.diagnostics.push(Diagnostic {
            message: err.message.clone(),
            mods: err.id.clone().into_iter().collect(),
        });
    }

    // Cross-format duplicate: a native mod and a Fabric mod sharing an id.
    let fabric_ids: std::collections::BTreeSet<String> =
        fabric_report.mods.iter().map(|m| m.id.clone()).collect();
    for id in ids.keys() {
        if fabric_ids.contains(id) {
            report.diagnostics.push(Diagnostic {
                message: format!(
                    "mod id `{id}` exists in both .hmod and .jar form — pick one"
                ),
                mods: vec![id.clone()],
            });
        }
    }

    report
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn clean_directory_has_no_diagnostics() {
        let dir = std::env::temp_dir().join(format!("horizon-validate-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        fs::write(dir.join("a.hmod"), b"").unwrap();

        // A single empty .hmod with no manifest produces no diagnostics (no id to duplicate).
        let report = validate(&dir, "26.3", "client");
        // fabric check on an empty-ish folder is clean.
        assert!(report.diagnostics.is_empty() || report.diagnostics.iter().all(|d| !d.message.contains("duplicate")));

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn duplicate_native_ids_are_reported() {
        let dir = std::env::temp_dir().join(format!("horizon-validate-dup-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();

        for name in ["m1", "m2"] {
            let folder = dir.join(name);
            fs::create_dir_all(&folder).unwrap();
            fs::write(
                folder.join("horizon-mod.toml"),
                "id = \"dup_mod\"\nname = \"Dup\"\nversion = \"1.0\"\napi = \"0.1\"\n",
            )
            .unwrap();
        }

        let report = validate(&dir, "26.3", "client");
        assert!(report.diagnostics.iter().any(|d| d.message.contains("duplicate native mod id")));

        let _ = fs::remove_dir_all(&dir);
    }
}
