//! Opening Fabric mod jars without running them: `fabric.mod.json`, nested jars, mixin configs,
//! access wideners and a census of the class names the jar references.
//!
//! Nothing here executes a class. Everything a report says about a jar comes from reading its
//! bytes, which is why a report can be produced on a machine with no JVM.

use crate::classfile::class_references;
use crate::metadata::ModJson;
use serde::Serialize;
use std::collections::{BTreeMap, BTreeSet};
use std::fs::File;
use std::io::{Cursor, Read, Seek};
use std::path::{Path, PathBuf};
use zip::ZipArchive;

/// The metadata file, at the root of a mod jar.
pub const METADATA: &str = "fabric.mod.json";
/// Quilt's, which this build does not read (Phase 8 in the roadmap).
pub const QUILT_METADATA: &str = "quilt.mod.json";

/// How deep nested jars may nest. Loader follows one level of `jars`; two is already unusual and a
/// deeper chain is a mod bug, so it is reported rather than followed forever.
const MAX_NESTING: u8 = 3;
/// The largest `fabric.mod.json` read (Loader refuses anything near this too).
const MAX_METADATA: u64 = 1 << 20;
/// The largest single class file whose constant pool is parsed.
const MAX_CLASS: u64 = 8 << 20;
/// The largest nested jar read into memory.
const MAX_NESTED: u64 = 128 << 20;

/// One mod found in a jar (the jar itself, or a jar nested in it).
#[derive(Clone, Debug, Serialize)]
pub struct JarMod {
    /// `foo.jar` or `foo.jar!/META-INF/jars/bar.jar`.
    pub label: String,
    /// The file on disk the mod came from.
    #[serde(skip)]
    pub file: PathBuf,
    pub metadata: ModJson,
    pub census: Census,
}

/// What a jar references, for the compatibility database and for diagnostics. A census is not a
/// verdict: `docs/fabric-compatibility/COMPATIBILITY.md` defines what each verdict needs.
#[derive(Clone, Debug, Default, Serialize)]
pub struct Census {
    pub classes: usize,
    pub bytes: u64,
    /// `net/minecraft/**` classes the jar names.
    pub game_classes: BTreeSet<String>,
    /// The subset that is rendering or GUI: what a Rust renderer cannot host (ARCHITECTURE.md §4).
    pub graphics_classes: BTreeSet<String>,
    pub mixin_configs: Vec<String>,
    pub access_wideners: Vec<String>,
    pub nested_jars: Vec<String>,
    /// The jar names Kotlin (`kotlin/**`), or declares a language adapter.
    pub kotlin: bool,
    /// Entrypoint classes whose `.class` file is not in the jar.
    pub missing_entrypoints: Vec<String>,
    /// Entrypoint point → how many classes it names (`main`, `client`, …).
    pub entrypoints: BTreeMap<String, usize>,
}

/// The result of opening one file.
#[derive(Debug)]
pub enum JarRead {
    /// One or more mods (a nested jar is a mod of its own).
    Mods(Vec<JarMod>),
    /// A jar with no `fabric.mod.json`: a plain library, which Loader ignores.
    NotAMod,
    /// A jar this build refuses, with the reason to show the player.
    Failed(String),
}

/// Reads one file from the mods folder.
pub fn read_jar(path: &Path) -> JarRead {
    let label = path
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_else(|| path.display().to_string());
    let file = match File::open(path) {
        Ok(file) => file,
        Err(e) => return JarRead::Failed(format!("{label}: cannot open: {e}")),
    };
    let mut archive = match ZipArchive::new(file) {
        Ok(archive) => archive,
        Err(e) => return JarRead::Failed(format!("{label}: not a readable jar: {e}")),
    };
    let names: Vec<String> = archive.file_names().map(str::to_string).collect();
    if names.iter().any(|name| name == QUILT_METADATA) {
        return JarRead::Failed(format!(
            "{label}: a Quilt mod (quilt.mod.json); Horizon reads Fabric mods only, for now"
        ));
    }
    if !names.iter().any(|name| name == METADATA) {
        return JarRead::NotAMod;
    }
    let mut mods = Vec::new();
    match read_archive(&mut archive, path, &label, 0, &mut mods) {
        Ok(_) => JarRead::Mods(mods),
        Err(e) => JarRead::Failed(format!("{label}: {e}")),
    }
}

fn read_archive<R: Read + Seek>(
    archive: &mut ZipArchive<R>,
    file: &Path,
    label: &str,
    depth: u8,
    out: &mut Vec<JarMod>,
) -> Result<BTreeSet<String>, String> {
    if depth > MAX_NESTING {
        return Err(format!("nested jars are more than {MAX_NESTING} deep"));
    }
    let names: Vec<String> = archive.file_names().map(str::to_string).collect();
    let classes_here: BTreeSet<String> = names
        .iter()
        .filter(|name| name.ends_with(".class"))
        .cloned()
        .collect();
    let Some(metadata) = read_entry(archive, METADATA, MAX_METADATA)? else {
        // A nested jar without metadata is a library (`jars` may carry those), and its classes are
        // still on the classpath of the jar that names it.
        return Ok(classes_here);
    };
    let metadata = ModJson::parse(&metadata, label)?;
    let mut census = Census {
        mixin_configs: metadata
            .mixins
            .iter()
            .map(|mixin| mixin.config().to_string())
            .collect(),
        access_wideners: metadata.access_widener.iter().cloned().collect(),
        nested_jars: metadata.jars.iter().map(|jar| jar.file.clone()).collect(),
        ..Census::default()
    };
    census.kotlin = !metadata.language_adapters.is_empty()
        || metadata
            .entrypoint_classes()
            .any(|(_, entry)| entry.adapter() != "default");

    for name in &classes_here {
        let Some(bytes) = read_entry(archive, name, MAX_CLASS)? else {
            continue;
        };
        census.classes += 1;
        census.bytes += bytes.len() as u64;
        let Ok(references) = class_references(&bytes) else {
            continue;
        };
        for reference in references {
            if reference.starts_with("net/minecraft/") {
                census.game_classes.insert(reference.clone());
                if is_graphics(&reference) {
                    census.graphics_classes.insert(reference);
                }
            } else if reference.starts_with("kotlin/") {
                census.kotlin = true;
            }
        }
    }

    let entrypoints: BTreeMap<String, usize> = metadata
        .entrypoints
        .iter()
        .map(|(point, entries)| (point.clone(), entries.len()))
        .collect();
    census.entrypoints = entrypoints;
    let index = out.len();
    out.push(JarMod {
        label: label.to_string(),
        file: file.to_path_buf(),
        metadata,
        census,
    });
    let nested_files = out[index].census.nested_jars.clone();

    // Nested jars share the loader's class path with their parent, so an entrypoint may name a
    // class in a nested jar. The check therefore runs after the whole tree has been read.
    let mut classes = classes_here;
    for nested in nested_files {
        let Some(bytes) = read_entry(archive, &nested, MAX_NESTED)? else {
            return Err(format!(
                "`jars` names `{nested}`, which the jar does not contain"
            ));
        };
        let mut inner = ZipArchive::new(Cursor::new(bytes))
            .map_err(|e| format!("`{nested}` is not a readable jar: {e}"))?;
        let inner_label = format!("{label}!/{nested}");
        let inner_classes = read_archive(&mut inner, file, &inner_label, depth + 1, out)?;
        classes.extend(inner_classes);
    }

    let missing: Vec<String> = out[index]
        .metadata
        .entrypoint_classes()
        .filter(|(_, entry)| {
            let path = format!("{}.class", entry.class_name().replace('.', "/"));
            !classes.contains(&path)
        })
        .map(|(_, entry)| entry.class_name().to_string())
        .collect();
    out[index].census.missing_entrypoints = missing;
    Ok(classes)
}

/// Reads one entry, or `None` when it is absent. Entries over `limit` are refused rather than
/// silently truncated: a truncated class would produce a wrong census.
fn read_entry<R: Read + Seek>(
    archive: &mut ZipArchive<R>,
    name: &str,
    limit: u64,
) -> Result<Option<Vec<u8>>, String> {
    let mut entry = match archive.by_name(name) {
        Ok(entry) => entry,
        Err(zip::result::ZipError::FileNotFound) => return Ok(None),
        Err(e) => return Err(format!("`{name}`: {e}")),
    };
    if entry.size() > limit {
        return Err(format!("`{name}` is larger than {limit} bytes"));
    }
    let mut bytes = Vec::with_capacity(entry.size() as usize);
    entry
        .read_to_end(&mut bytes)
        .map_err(|e| format!("`{name}`: {e}"))?;
    Ok(Some(bytes))
}

/// The parts of `net/minecraft` that are the platform's own rendering and GUI stack, which a mod
/// that expects them cannot be served by a Rust renderer.
fn is_graphics(class: &str) -> bool {
    class.starts_with("net/minecraft/client/gui/")
        || class.starts_with("net/minecraft/client/render")
        || class.starts_with("com/mojang/blaze3d/")
        || class.starts_with("net/minecraft/client/Minecraft")
}
