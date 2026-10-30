//! The pinned Fabric libraries, found on disk and verified by SHA-256.
//!
//! Horizon does not download anything at runtime. The Loader and the libraries it needs are the
//! ones in the player's install (`tools/benchmark-deps/fabric.lock.json` pins exactly which
//! bytes are acceptable). This module locates those files and refuses to hand the JVM a jar whose
//! hash does not match: a wrong `asm` on the class path is a `NoSuchMethodError` in the middle of
//! discovery, which is much worse to diagnose than "this file is not the one Horizon expects".

use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::{self, Read};
use std::path::{Path, PathBuf};

/// One jar the host class path needs, pinned by `tools/benchmark-deps/fabric.lock.json`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct PinnedLib {
    /// The Maven coordinate, for messages a player can search for.
    pub coordinate: &'static str,
    /// The file name the official launcher and the lock file use.
    pub file: &'static str,
    /// The SHA-256 of the exact bytes, lowercase hex.
    pub sha256: &'static str,
}

impl PinnedLib {
    /// Whether `sha256` (lowercase hex) is the pinned digest.
    pub fn matches(&self, sha256: &str) -> bool {
        self.sha256.eq_ignore_ascii_case(sha256)
    }
}

/// Everything the Java host needs on its class path, in load order.
pub const PINNED: &[PinnedLib] = &[
    PinnedLib {
        coordinate: "org.ow2.asm:asm:9.10.1",
        file: "asm-9.10.1.jar",
        sha256: "ed825d10ab1399c8c0cb669e688cf0c8c82629b4c8399b58352b68e92ca10fcb",
    },
    PinnedLib {
        coordinate: "org.ow2.asm:asm-analysis:9.10.1",
        file: "asm-analysis-9.10.1.jar",
        sha256: "dede75a21306b65974ecd8f87114ff6970f09fb794157a4ca09ab25c888c2bfc",
    },
    PinnedLib {
        coordinate: "org.ow2.asm:asm-commons:9.10.1",
        file: "asm-commons-9.10.1.jar",
        sha256: "6d0abefb7cbf972ea16edb37ec14835372505063a45f976ab7ea889ed9497895",
    },
    PinnedLib {
        coordinate: "org.ow2.asm:asm-tree:9.10.1",
        file: "asm-tree-9.10.1.jar",
        sha256: "3dfb0d5b6a106cd40b5b250e39935fbf2f927f4477546a5369a3ac609cf0506b",
    },
    PinnedLib {
        coordinate: "org.ow2.asm:asm-util:9.10.1",
        file: "asm-util-9.10.1.jar",
        sha256: "1bb99d091fba2597dc6d51193e9bbcf0d8447e7ed96bd8f0198b18152f09655c",
    },
    PinnedLib {
        coordinate: "net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7",
        file: "sponge-mixin-0.17.4+mixin.0.8.7.jar",
        sha256: "1f0ae44db7295f8626f33b1dc0ad7f043d8954a8d6847247875fcc5dfcecc934",
    },
    PinnedLib {
        coordinate: "net.fabricmc:fabric-loader:0.19.5",
        file: "fabric-loader-0.19.5.jar",
        sha256: "93044e4dd46de5d8136701292f05e868da096d2c9fddb4793e4fdbcc63efc695",
    },
];

/// A verified set of libraries: every [`PINNED`] entry, found and hash-checked.
#[derive(Clone, Debug)]
pub struct LibSet {
    /// The folder the jars were found in.
    pub dir: PathBuf,
    /// The jars in [`PINNED`] order (the host class path is appended to this).
    pub jars: Vec<PathBuf>,
}

impl LibSet {
    /// The Loader jar, which is always the last pinned entry.
    pub fn loader(&self) -> &Path {
        self.jars
            .last()
            .map(PathBuf::as_path)
            .expect("a LibSet always contains the loader")
    }
}

/// What was wrong with a folder that should hold the pinned libraries.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum LibsError {
    #[error("cannot read the Fabric library folder {}: {message}", dir.display())]
    Unreadable { dir: PathBuf, message: String },
    #[error("{0}")]
    Incomplete(Box<LibsFailure>),
}

/// Missing and mismatched jars, all listed at once so one fix run is enough.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct LibsFailure {
    pub dir: PathBuf,
    /// Pinned jars no file in the folder matched, by name or by hash.
    pub missing: Vec<PinnedLib>,
    /// Files whose name matched but whose bytes did not.
    pub mismatched: Vec<Mismatch>,
    /// Files that could not be read while hashing.
    pub unreadable: Vec<(PathBuf, String)>,
}

/// A file found under a pinned name with the wrong contents.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Mismatch {
    pub path: PathBuf,
    pub pinned: PinnedLib,
    /// The SHA-256 actually on disk, or why it could not be computed.
    pub actual: Result<String, String>,
}

impl std::fmt::Display for LibsFailure {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        writeln!(
            f,
            "the Fabric libraries in {} are not the pinned set (Horizon never downloads them; \
             copy them from the official launcher's install):",
            self.dir.display()
        )?;

        for lib in &self.missing {
            writeln!(
                f,
                "  missing   {} (expected file {}, sha256 {})",
                lib.coordinate, lib.file, lib.sha256
            )?;
        }

        for mismatch in &self.mismatched {
            match &mismatch.actual {
                Ok(actual) => writeln!(
                    f,
                    "  wrong     {} (expected sha256 {}, found {})",
                    mismatch.path.display(),
                    mismatch.pinned.sha256,
                    actual
                )?,
                Err(e) => writeln!(f, "  unreadable {} ({e})", mismatch.path.display())?,
            }
        }

        for (path, e) in &self.unreadable {
            writeln!(f, "  unreadable {} ({e})", path.display())?;
        }

        Ok(())
    }
}

/// Finds and verifies the pinned libraries in `dir`.
///
/// A jar is accepted by its pinned file name or, if a player renamed it, by its pinned SHA-256.
/// A file that carries a pinned name but the wrong bytes is reported as wrong rather than
/// silently replaced by a hash match elsewhere, so the message names the file to fix.
pub fn locate(dir: &Path) -> Result<LibSet, LibsError> {
    let entries = std::fs::read_dir(dir).map_err(|e| LibsError::Unreadable {
        dir: dir.to_path_buf(),
        message: e.to_string(),
    })?;

    let mut files: Vec<PathBuf> = entries
        .flatten()
        .map(|entry| entry.path())
        .filter(|path| {
            path.is_file()
                && path
                    .extension()
                    .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
        })
        .collect();
    files.sort();

    let mut failure = LibsFailure {
        dir: dir.to_path_buf(),
        ..LibsFailure::default()
    };
    let mut jars = Vec::with_capacity(PINNED.len());
    let mut used = vec![false; files.len()];

    for pinned in PINNED {
        let named = files
            .iter()
            .position(|path| path.file_name().is_some_and(|name| name == pinned.file));

        if let Some(index) = named {
            used[index] = true;
            let path = files[index].clone();
            match digest(&path) {
                Ok(actual) if pinned.matches(&actual) => {
                    jars.push(path);
                    continue;
                }
                Ok(actual) => failure.mismatched.push(Mismatch {
                    path,
                    pinned: *pinned,
                    actual: Ok(actual),
                }),
                Err(e) => failure.mismatched.push(Mismatch {
                    path,
                    pinned: *pinned,
                    actual: Err(e.to_string()),
                }),
            }

            continue;
        }

        // Renamed file: still the pinned bytes, so accept it. The search is linear over a handful
        // of jars and only runs when a name is missing.
        let mut found = None;
        for (index, path) in files.iter().enumerate() {
            if used[index] {
                continue;
            }

            match digest(path) {
                Ok(actual) if pinned.matches(&actual) => {
                    found = Some(index);
                    break;
                }
                Ok(_) => {}
                Err(e) => failure.unreadable.push((path.clone(), e.to_string())),
            }
        }

        match found {
            Some(index) => {
                used[index] = true;
                jars.push(files[index].clone());
            }
            None => failure.missing.push(*pinned),
        }
    }

    if failure.missing.is_empty() && failure.mismatched.is_empty() {
        Ok(LibSet {
            dir: dir.to_path_buf(),
            jars,
        })
    } else {
        Err(LibsError::Incomplete(Box::new(failure)))
    }
}

/// The SHA-256 of a file, lowercase hex.
pub fn digest(path: &Path) -> io::Result<String> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0u8; 64 * 1024];

    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }

    Ok(hex(&hasher.finalize()))
}

/// Lowercase hex, the form the lock file and [`PinnedLib::sha256`] use.
pub fn hex(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        out.push_str(&format!("{byte:02x}"));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pinned_set_is_complete_and_unique() {
        assert_eq!(PINNED.len(), 7);
        let mut names: Vec<&str> = PINNED.iter().map(|lib| lib.file).collect();
        names.sort_unstable();
        names.dedup();
        assert_eq!(
            names.len(),
            PINNED.len(),
            "two pinned jars share a file name"
        );

        for lib in PINNED {
            assert_eq!(lib.sha256.len(), 64, "{} is not a SHA-256", lib.coordinate);
            assert!(
                lib.sha256.chars().all(|c| c.is_ascii_hexdigit()),
                "{} has a non-hex digest",
                lib.coordinate
            );
        }
    }

    #[test]
    fn missing_folder_is_an_error_that_names_the_folder() {
        let dir = std::env::temp_dir().join("horizon-jvm-does-not-exist-3f9a1c");
        let error = locate(&dir).expect_err("a missing folder cannot hold the libraries");
        assert!(error.to_string().contains("cannot read"), "{error}");
    }

    #[test]
    fn a_named_file_with_wrong_bytes_is_reported() {
        let dir = std::env::temp_dir().join(format!("horizon-jvm-libs-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(dir.join(PINNED[0].file), b"not asm").unwrap();

        let error = locate(&dir).expect_err("a fake jar must not pass");
        let text = error.to_string();
        assert!(text.contains("wrong"), "{text}");
        assert!(text.contains(PINNED[0].file), "{text}");
        assert_eq!(
            error.to_string().matches("missing").count(),
            PINNED.len() - 1,
            "the other pins are still missing: {text}"
        );

        std::fs::remove_dir_all(&dir).unwrap();
    }
}
