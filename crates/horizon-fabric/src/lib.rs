//! Reading a Fabric `mods/` folder without a JVM: what each jar is, what it expects, and the order
//! it would load in.
//!
//! This is Phase 1 of [the roadmap](../../docs/fabric-compatibility/ROADMAP.md). It does not run
//! Java, Fabric Loader or Minecraft, and it does not claim any mod works: it answers the questions
//! a player asks before a game starts ("why won't this load?") and the questions a compatibility
//! report needs ("what does this jar touch?"). The architecture and its limits are in
//! [ARCHITECTURE.md](../../docs/fabric-compatibility/ARCHITECTURE.md).

pub mod classfile;
pub mod jar;
pub mod metadata;
pub mod resolve;
pub mod version;

use std::path::{Path, PathBuf};

pub use jar::{Census, JarMod, JarRead};
pub use metadata::{Environment, Side};
pub use resolve::{Diagnostic, ModStatus, Options, Report, Status};

/// The Fabric convention: jars at the root of the game folder, beside `options.txt`.
pub const MODS_DIR: &str = "mods";

/// Reads every `.jar` in `dir` and resolves a load plan. A missing folder is an empty plan with a
/// warning, not an error: a player with no mods is not doing anything wrong.
pub fn check(dir: &Path, options: Options) -> Report {
    let mut jars = Vec::new();
    let mut failures = Vec::new();
    let mut ignored = Vec::new();
    let mut files: Vec<PathBuf> = Vec::new();

    match std::fs::read_dir(dir) {
        Ok(entries) => {
            for entry in entries.flatten() {
                let path = entry.path();
                let is_jar = path
                    .extension()
                    .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"));
                if is_jar && path.is_file() {
                    files.push(path);
                }
            }
        }
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
            let mut report = resolve::resolve(Vec::new(), Vec::new(), &options);
            report.mods_folder = dir.display().to_string();
            report.warnings.push(Diagnostic::error(
                None,
                format!("{} does not exist: no mods are installed", dir.display()),
            ));
            return report;
        }
        Err(e) => {
            let mut report = resolve::resolve(Vec::new(), Vec::new(), &options);
            report.ok = false;
            report.mods_folder = dir.display().to_string();
            report.errors.push(Diagnostic::error(
                None,
                format!("cannot read {}: {e}", dir.display()),
            ));
            return report;
        }
    }

    // The order the loader reads them in: by file name, so a report is reproducible.
    files.sort();
    for file in &files {
        match jar::read_jar(file) {
            JarRead::Mods(mods) => jars.extend(mods),
            JarRead::NotAMod => ignored.push(
                file.file_name()
                    .map(|name| name.to_string_lossy().into_owned())
                    .unwrap_or_else(|| file.display().to_string()),
            ),
            JarRead::Failed(reason) => failures.push(reason),
        }
    }

    let mut report = resolve::resolve(jars, failures, &options);
    report.mods_folder = dir.display().to_string();
    report.jars = files.len();
    if !ignored.is_empty() {
        report.warnings.push(Diagnostic::error(
            None,
            format!(
                "{} jar(s) have no `fabric.mod.json` and were ignored: {}",
                ignored.len(),
                ignored.join(", ")
            ),
        ));
    }
    report
}
