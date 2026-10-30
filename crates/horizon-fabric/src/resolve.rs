//! From mod jars to a load plan: what Fabric Loader's discovery and resolution would decide, done
//! without the loader so a player (and CI) can see it before a JVM starts.
//!
//! The rules follow Loader's: a mod's `depends` are hard, `breaks`/`conflicts` veto a version,
//! `recommends` only warn, `environment` removes a mod from the other side, and a duplicate id is
//! resolved rather than refused (a root jar outranks a nested copy; otherwise the newer version
//! wins, as `ModPrioSorter` orders candidates in Fabric Loader 0.19.5). The order is deterministic
//! (dependencies first, then id) so a report is reproducible.

use crate::jar::JarMod;
use crate::metadata::Side;
use crate::version::{Predicates, Version};
use serde::Serialize;
use std::cmp::Ordering;
use std::collections::{BTreeMap, BTreeSet};

/// The Fabric Loader this build's reports are pinned to (`tools/benchmark-deps/fabric.lock.json`).
pub const PINNED_LOADER: &str = "0.19.5";

/// Mods inside the pinned loader jar, which a player therefore never installs. Verified against
/// `fabric-loader-0.19.5.jar`: its `fabric.mod.json` lists
/// `META-INF/jars/mixinextras-fabric-0.5.5.jar`, whose own metadata is `id: mixinextras`,
/// `version: 0.5.5`. Lithium >= 0.26 declares `mixinextras >=0.5.5`, so without this a valid mod
/// set looks broken.
pub const PINNED_LOADER_BUNDLED: &[(&str, &str)] = &[("mixinextras", "0.5.5")];

/// What the plan is for.
#[derive(Clone, Debug)]
pub struct Options {
    /// The game version mods are checked against (`horizon_core::version::VERSION_ID`).
    pub game_version: String,
    pub side: Side,
    /// The Fabric Loader version that will run the mods, when known. Without it, `fabricloader`
    /// dependencies are reported as unverifiable instead of failing.
    pub loader_version: Option<String>,
    /// The Java major version the game runs on.
    pub java_version: Option<String>,
}

impl Default for Options {
    fn default() -> Options {
        Options {
            game_version: horizon_core::version::VERSION_ID.to_string(),
            side: Side::Client,
            loader_version: None,
            java_version: Some(horizon_core::version::JAVA_VERSION.to_string()),
        }
    }
}

/// One mod's place in the plan.
#[derive(Clone, Debug, Serialize)]
pub struct ModStatus {
    pub id: String,
    pub version: String,
    pub name: String,
    pub file: String,
    pub environment: String,
    pub status: Status,
    /// Why, when `status` is not `Loads`.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
    pub depends: Vec<String>,
    pub provides: Vec<String>,
    pub mixin_configs: usize,
    pub access_widener: bool,
    pub nested_jars: usize,
    pub game_classes: usize,
    pub graphics_classes: usize,
    pub kotlin: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Status {
    /// In the load plan.
    Loads,
    /// Not on this side (`environment`), or a duplicate of a mod that is loaded instead.
    Skipped,
    /// Would load, but something it needs is missing or broken.
    Blocked,
}

/// A message with the mod it is about, when there is one.
#[derive(Clone, Debug, Serialize)]
pub struct Diagnostic {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    pub message: String,
}

impl Diagnostic {
    pub fn error(id: Option<&str>, message: impl Into<String>) -> Diagnostic {
        Diagnostic {
            id: id.map(str::to_string),
            message: message.into(),
        }
    }
}

/// The whole answer: mods, the order they load in, and everything wrong.
#[derive(Clone, Debug, Serialize)]
pub struct Report {
    /// No error. Warnings and skipped mods do not stop a game.
    pub ok: bool,
    pub game_version: String,
    pub side: String,
    pub mods_folder: String,
    pub jars: usize,
    pub mods: Vec<ModStatus>,
    /// The ids in load order (dependencies before dependents).
    pub order: Vec<String>,
    pub errors: Vec<Diagnostic>,
    pub warnings: Vec<Diagnostic>,
}

/// Resolves mods, plus the jars that could not be read at all (`Failures` from `jar::read_jar`).
pub fn resolve(jars: Vec<JarMod>, failures: Vec<String>, options: &Options) -> Report {
    let mut errors: Vec<Diagnostic> = failures
        .iter()
        .map(|failure| Diagnostic::error(None, failure.clone()))
        .collect();
    let mut warnings = Vec::new();
    let mut reasons: BTreeMap<usize, String> = BTreeMap::new();

    // Mods not on this side are not present at all, as far as dependency checks go.
    let mut skipped: BTreeSet<usize> = BTreeSet::new();
    for (index, jar) in jars.iter().enumerate() {
        if !jar.metadata.environment.runs_on(options.side) {
            skipped.insert(index);
            warnings.push(Diagnostic::error(
                Some(&jar.metadata.id),
                format!(
                    "`{}` is for the {} side only; left out of this {}",
                    jar.metadata.id,
                    jar.metadata.environment.name(),
                    options.side.name()
                ),
            ));
        }
    }

    // A jar may hold several mods (nested jars), and two jars may name one id. Fabric Loader
    // selects one of them (`ModPrioSorter`): a root jar first, then the newer version, then the
    // less nested copy. The others are inactive, and this is normal: Sodium bundles its own
    // Fabric API jars, which appear beside the same modules from a separate Fabric API download.
    let mut groups: BTreeMap<&str, Vec<usize>> = BTreeMap::new();
    for (index, jar) in jars.iter().enumerate() {
        if !skipped.contains(&index) {
            groups
                .entry(jar.metadata.id.as_str())
                .or_default()
                .push(index);
        }
    }
    let mut by_id: BTreeMap<String, usize> = BTreeMap::new();
    let mut inactive: BTreeSet<usize> = BTreeSet::new();
    for (id, group) in &groups {
        let winner = group
            .iter()
            .copied()
            .min_by(|a, b| priority(&jars, *a, *b))
            .expect("a group has at least one mod");
        for loser in group {
            if *loser != winner {
                inactive.insert(*loser);
                reasons.insert(*loser, duplicate_reason(&jars, *loser, winner));
            }
        }
        by_id.insert((*id).to_string(), winner);
    }

    // `provides`: a mod can answer to extra ids (`mixinextras` provides
    // `com_github_llamalad7_mixinextras`). A real mod with that id takes precedence; Loader lets
    // the SAT solver decide between overlapping candidates, which is more than a report needs.
    let mut alias_groups: BTreeMap<&str, Vec<usize>> = BTreeMap::new();
    for (index, jar) in jars.iter().enumerate() {
        if skipped.contains(&index) || inactive.contains(&index) {
            continue;
        }
        for provided in &jar.metadata.provides {
            if !by_id.contains_key(provided) {
                alias_groups
                    .entry(provided.as_str())
                    .or_default()
                    .push(index);
            }
        }
    }
    let alias: BTreeMap<String, usize> = alias_groups
        .iter()
        .map(|(id, group)| {
            let winner = group
                .iter()
                .copied()
                .min_by(|a, b| priority(&jars, *a, *b))
                .expect("a group has at least one mod");
            ((*id).to_string(), winner)
        })
        .collect();

    // What the game, the JDK and the loader provide; a mod is never asked to ship these.
    let mut provided: BTreeMap<&str, Option<String>> = BTreeMap::new();
    provided.insert("minecraft", Some(options.game_version.clone()));
    provided.insert("fabricloader", options.loader_version.clone());
    provided.insert("java", options.java_version.clone());
    match &options.loader_version {
        Some(version) if Version::parse(version) == Version::parse(PINNED_LOADER) => {
            for &(id, version) in PINNED_LOADER_BUNDLED {
                provided.insert(id, Some(version.to_string()));
            }
        }
        Some(version) => warnings.push(Diagnostic::error(
            None,
            format!(
                "this report knows the mods bundled in Fabric Loader {PINNED_LOADER}; for {version} \
                 a bundled dependency such as `mixinextras` is not checked"
            ),
        )),
        None => {}
    }

    for (index, jar) in jars.iter().enumerate() {
        if skipped.contains(&index) || inactive.contains(&index) {
            continue;
        }
        for missing in &jar.census.missing_entrypoints {
            errors.push(Diagnostic::error(
                Some(&jar.metadata.id),
                format!(
                    "`{}` names the entrypoint `{missing}`, but {} has no class file for it",
                    jar.metadata.id, jar.label
                ),
            ));
        }
    }

    let mut unusable: BTreeSet<usize> = BTreeSet::new();
    let mut unknown_loader = false;

    for (index, jar) in jars.iter().enumerate() {
        if skipped.contains(&index) || inactive.contains(&index) {
            continue;
        }
        let meta = &jar.metadata;
        let mut blocked = false;

        let mut check =
            |depends: &BTreeMap<String, crate::metadata::VersionValue>, hard: bool| -> bool {
                let mut ok = true;
                for (name, value) in depends {
                    let predicates = match Predicates::parse(&value.values()) {
                        Ok(predicates) => predicates,
                        Err(e) => {
                            errors.push(Diagnostic::error(
                                Some(&meta.id),
                                format!(
                                    "`{}` needs `{name}` with an unreadable predicate: {e}",
                                    meta.id
                                ),
                            ));
                            return false;
                        }
                    };
                    let found: Option<Option<String>> = match provided.get(name.as_str()) {
                        Some(version) => Some(version.clone()),
                        None => find_mod(&by_id, &alias, name)
                            .map(|other| Some(jars[other].metadata.version.clone())),
                    };
                    match found {
                        Some(Some(version)) => {
                            if !predicates.matches(&version) {
                                let message = format!(
                                    "`{}` {} needs `{name}` {}, but {} is installed",
                                    meta.id,
                                    meta.version,
                                    describe(&value.values()),
                                    version
                                );
                                if hard {
                                    errors.push(Diagnostic::error(Some(&meta.id), message));
                                    ok = false;
                                } else {
                                    warnings.push(Diagnostic::error(Some(&meta.id), message));
                                }
                            }
                        }
                        Some(None) => {
                            // A built-in whose version this build was not told (the loader).
                            if name == "fabricloader" {
                                unknown_loader = true;
                            }
                        }
                        None => {
                            let message = format!(
                                "`{}` {} needs `{name}` {}, which is not installed",
                                meta.id,
                                meta.version,
                                describe(&value.values())
                            );
                            if hard {
                                errors.push(Diagnostic::error(Some(&meta.id), message));
                                ok = false;
                            } else {
                                warnings.push(Diagnostic::error(Some(&meta.id), message));
                            }
                        }
                    }
                }
                ok
            };

        if !check(&meta.depends, true) {
            blocked = true;
        }
        check(&meta.recommends, false);

        for (name, value) in meta.breaks.iter().chain(meta.conflicts.iter()) {
            let predicates = match Predicates::parse(&value.values()) {
                Ok(predicates) => predicates,
                Err(_) => continue,
            };
            let version = match provided.get(name.as_str()) {
                Some(Some(version)) => Some(version.clone()),
                Some(None) => None,
                None => {
                    find_mod(&by_id, &alias, name).map(|other| jars[other].metadata.version.clone())
                }
            };
            if let Some(version) = version
                && predicates.matches(&version)
            {
                errors.push(Diagnostic::error(
                    Some(&meta.id),
                    format!(
                        "`{}` {} breaks `{name}` {}: {} is installed",
                        meta.id,
                        meta.version,
                        describe(&value.values()),
                        version
                    ),
                ));
                blocked = true;
            }
        }

        if blocked {
            unusable.insert(index);
            reasons.insert(index, "a dependency is missing or broken".to_string());
        }
    }

    if unknown_loader {
        warnings.push(Diagnostic::error(
            None,
            "`fabricloader` dependencies were not checked: no loader version was given",
        ));
    }

    // Anything that needs a mod which is not loading is blocked too, until nothing changes.
    loop {
        let mut changed = false;
        for (index, jar) in jars.iter().enumerate() {
            if skipped.contains(&index) || inactive.contains(&index) || unusable.contains(&index) {
                continue;
            }
            for name in jar.metadata.depends.keys() {
                if let Some(other) = find_mod(&by_id, &alias, name)
                    && unusable.contains(&other)
                {
                    unusable.insert(index);
                    reasons.insert(index, format!("it needs `{name}`, which is not loading"));
                    changed = true;
                    break;
                }
            }
        }
        if !changed {
            break;
        }
    }

    // Kahn's algorithm over the mods that load; `BTreeSet` makes the order deterministic.
    let mut edges: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();
    let mut indegree: BTreeMap<String, usize> = BTreeMap::new();
    for (index, jar) in jars.iter().enumerate() {
        if skipped.contains(&index) || inactive.contains(&index) || unusable.contains(&index) {
            continue;
        }
        indegree.entry(jar.metadata.id.clone()).or_insert(0);
        edges.entry(jar.metadata.id.clone()).or_default();
    }
    for (index, jar) in jars.iter().enumerate() {
        if skipped.contains(&index) || inactive.contains(&index) || unusable.contains(&index) {
            continue;
        }
        for name in jar.metadata.depends.keys() {
            if let Some(other) = find_mod(&by_id, &alias, name) {
                let other = &jars[other].metadata.id;
                if edges
                    .get(other)
                    .is_some_and(|set| set.contains(&jar.metadata.id))
                {
                    continue;
                }
                if edges
                    .get_mut(other)
                    .is_some_and(|set| set.insert(jar.metadata.id.clone()))
                {
                    *indegree.entry(jar.metadata.id.clone()).or_insert(0) += 1;
                }
            }
        }
    }
    let mut ready: BTreeSet<String> = indegree
        .iter()
        .filter(|(_, degree)| **degree == 0)
        .map(|(id, _)| id.clone())
        .collect();
    let mut order = Vec::new();
    while let Some(id) = ready.pop_first() {
        order.push(id.clone());
        for next in edges.get(&id).cloned().unwrap_or_default() {
            if let Some(degree) = indegree.get_mut(&next) {
                *degree -= 1;
                if *degree == 0 {
                    ready.insert(next);
                }
            }
        }
    }
    let circular: Vec<String> = indegree
        .iter()
        .filter(|(_, degree)| **degree > 0)
        .map(|(id, _)| id.clone())
        .collect();
    if !circular.is_empty() {
        errors.push(Diagnostic::error(
            None,
            format!(
                "circular dependency between {}; none of them can load",
                circular.join(", ")
            ),
        ));
        for id in &circular {
            if let Some(index) = by_id.get(id) {
                unusable.insert(*index);
                reasons.insert(*index, "in a dependency cycle".to_string());
            }
        }
    }

    let mut mods: Vec<ModStatus> = jars
        .iter()
        .enumerate()
        .map(|(index, jar)| {
            let status = if skipped.contains(&index) || inactive.contains(&index) {
                Status::Skipped
            } else if unusable.contains(&index) {
                Status::Blocked
            } else {
                Status::Loads
            };
            let reason = if skipped.contains(&index) {
                Some(format!(
                    "`{}` mods do not run on the {}",
                    jar.metadata.environment.name(),
                    options.side.name()
                ))
            } else {
                reasons.get(&index).cloned()
            };
            ModStatus {
                id: jar.metadata.id.clone(),
                version: jar.metadata.version.clone(),
                name: jar.metadata.display_name().to_string(),
                file: jar.label.clone(),
                environment: jar.metadata.environment.name().to_string(),
                status,
                reason,
                depends: jar.metadata.depends.keys().cloned().collect(),
                provides: jar.metadata.provides.clone(),
                mixin_configs: jar.census.mixin_configs.len(),
                access_widener: !jar.census.access_wideners.is_empty(),
                nested_jars: jar.census.nested_jars.len(),
                game_classes: jar.census.game_classes.len(),
                graphics_classes: jar.census.graphics_classes.len(),
                kotlin: jar.census.kotlin,
            }
        })
        .collect();
    mods.sort_by(|a, b| a.id.cmp(&b.id).then_with(|| a.file.cmp(&b.file)));

    Report {
        ok: errors.is_empty(),
        game_version: options.game_version.clone(),
        side: options.side.name().to_string(),
        mods_folder: String::new(),
        jars: 0,
        mods,
        order,
        errors,
        warnings,
    }
}

/// The mod a dependency id names: a real mod first, else a mod that provides the id.
fn find_mod(
    by_id: &BTreeMap<String, usize>,
    alias: &BTreeMap<String, usize>,
    name: &str,
) -> Option<usize> {
    by_id
        .get(name)
        .copied()
        .or_else(|| alias.get(name).copied())
}

/// `true` for a jar directly in `mods/`; nested jars have `!/` in their label.
fn is_root(jar: &JarMod) -> bool {
    !jar.label.contains("!/")
}

/// Fabric Loader's candidate order (`ModPrioSorter`), as `Ordering::Less` for the higher priority:
/// root jars first, then the newer version, then the less nested copy, then file name so the
/// answer is stable.
fn priority(jars: &[JarMod], a: usize, b: usize) -> Ordering {
    let (a, b) = (&jars[a], &jars[b]);
    match (is_root(a), is_root(b)) {
        (true, false) => return Ordering::Less,
        (false, true) => return Ordering::Greater,
        _ => {}
    }
    match Version::parse(&b.metadata.version).cmp(&Version::parse(&a.metadata.version)) {
        Ordering::Equal => {}
        other => return other,
    }
    let depth = |jar: &JarMod| jar.label.matches("!/").count();
    match depth(a).cmp(&depth(b)) {
        Ordering::Equal => {}
        other => return other,
    }
    a.label.cmp(&b.label)
}

fn duplicate_reason(jars: &[JarMod], loser: usize, winner: usize) -> String {
    let (loser, winner) = (&jars[loser], &jars[winner]);
    if is_root(winner) && !is_root(loser) {
        format!("the root jar {} is loaded instead", winner.label)
    } else if Version::parse(&loser.metadata.version) == Version::parse(&winner.metadata.version) {
        format!("the same version is loaded from {}", winner.label)
    } else if Version::parse(&loser.metadata.version) < Version::parse(&winner.metadata.version) {
        format!("a newer version is loaded from {}", winner.label)
    } else {
        format!("{} takes priority over this copy", winner.label)
    }
}

fn describe(values: &[String]) -> String {
    match values {
        [] => "*".to_string(),
        [one] => one.clone(),
        many => format!("one of {}", many.join(", ")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::jar::Census;
    use crate::metadata::ModJson;

    fn jar(id: &str, version: &str, json_extra: &str) -> JarMod {
        let json = format!(
            r#"{{"schemaVersion":1,"id":"{id}","version":"{version}","entrypoints":{{"main":["a.B"]}}{json_extra}}}"#
        );
        let mut metadata = ModJson::parse(json.as_bytes(), id).unwrap();
        metadata.name = None;
        JarMod {
            label: format!("{id}-{version}.jar"),
            file: Default::default(),
            metadata,
            census: Census {
                entrypoints: [("main".to_string(), 1)].into(),
                ..Census::default()
            },
        }
    }

    /// The same mod as a nested jar inside `parent.jar`, which is how Sodium ships Fabric API.
    fn nested_jar(id: &str, version: &str, parent: &str) -> JarMod {
        let mut jar = jar(id, version, "");
        jar.label = format!("{parent}.jar!/META-INF/jars/{id}-{version}.jar");
        jar
    }

    fn options() -> Options {
        Options {
            loader_version: Some(PINNED_LOADER.to_string()),
            ..Options::default()
        }
    }

    fn ids(report: &Report) -> Vec<&str> {
        report.mods.iter().map(|m| m.id.as_str()).collect()
    }

    fn status(report: &Report, id: &str) -> Status {
        report.mods.iter().find(|m| m.id == id).unwrap().status
    }

    #[test]
    fn dependencies_load_first_in_a_stable_order() {
        let report = resolve(
            vec![
                jar("zebra", "1.0.0", r#","depends":{"alpha":">=1.0"}"#),
                jar("beta", "1.0.0", ""),
                jar("alpha", "1.2.0", ""),
            ],
            Vec::new(),
            &options(),
        );
        assert!(report.ok, "{:?}", report.errors);
        assert_eq!(report.order, ["alpha", "beta", "zebra"]);
        assert_eq!(ids(&report), ["alpha", "beta", "zebra"]);
    }

    #[test]
    fn a_missing_dependency_blocks_everything_that_needs_it() {
        let report = resolve(
            vec![
                jar("needy", "1.0.0", r#","depends":{"absent":">=1.0"}"#),
                jar("needy2", "1.0.0", r#","depends":{"needy":"*"}"#),
            ],
            Vec::new(),
            &options(),
        );
        assert!(!report.ok);
        assert_eq!(status(&report, "needy"), Status::Blocked);
        assert_eq!(status(&report, "needy2"), Status::Blocked);
        assert!(report.order.is_empty());
        assert!(report.errors.iter().any(|e| e.message.contains("`absent`")));
    }

    #[test]
    fn a_version_mismatch_names_both_versions() {
        let report = resolve(
            vec![
                jar("dep", "1.0.0", ""),
                jar("needy", "2.0", r#","depends":{"dep":">=2.0"}"#),
            ],
            Vec::new(),
            &options(),
        );
        assert!(!report.ok);
        let message = &report.errors[0].message;
        assert!(message.contains("needs `dep` >=2.0"), "{message}");
        assert!(message.contains("1.0.0 is installed"), "{message}");
    }

    #[test]
    fn the_game_version_is_checked_without_a_mod_for_it() {
        let report = resolve(
            vec![jar("future", "1", r#","depends":{"minecraft":">=27.0"}"#)],
            Vec::new(),
            &options(),
        );
        assert!(!report.ok);
        assert!(report.errors[0].message.contains("minecraft"));
        let good = resolve(
            vec![jar(
                "today",
                "1",
                r#","depends":{"minecraft":">=26.1 <26.4","java":">=25"}"#,
            )],
            Vec::new(),
            &options(),
        );
        assert!(good.ok, "{:?}", good.errors);
    }

    #[test]
    fn breaks_veto_an_installed_version() {
        let report = resolve(
            vec![
                jar("old", "1.5.0", ""),
                jar("newer", "2.0.0", r#","breaks":{"old":"<2.0"}"#),
            ],
            Vec::new(),
            &options(),
        );
        assert!(!report.ok);
        assert_eq!(status(&report, "newer"), Status::Blocked);
        assert!(report.errors[0].message.contains("breaks `old`"));
    }

    #[test]
    fn a_client_only_mod_is_left_out_of_a_server() {
        let options = Options {
            side: Side::Server,
            ..options()
        };
        let report = resolve(
            vec![jar("hud", "1", r#","environment":"client""#)],
            Vec::new(),
            &options,
        );
        assert!(report.ok);
        assert_eq!(status(&report, "hud"), Status::Skipped);
        assert!(report.warnings[0].message.contains("client side only"));
    }

    #[test]
    fn the_newer_copy_of_an_id_is_the_one_that_loads() {
        let report = resolve(
            vec![jar("same", "1", ""), jar("same", "2", "")],
            Vec::new(),
            &options(),
        );
        assert!(report.ok, "{:?}", report.errors);
        assert_eq!(report.order, ["same"]);

        let rows: Vec<&ModStatus> = report.mods.iter().filter(|m| m.id == "same").collect();
        assert_eq!(rows.len(), 2);
        let loading = rows.iter().find(|m| m.status == Status::Loads).unwrap();
        assert_eq!(loading.version, "2");
        let skipped = rows.iter().find(|m| m.status == Status::Skipped).unwrap();
        assert_eq!(skipped.version, "1");
        assert!(
            skipped
                .reason
                .as_deref()
                .unwrap()
                .contains("newer version is loaded"),
            "{:?}",
            skipped.reason
        );
    }

    #[test]
    fn a_root_jar_outranks_a_nested_copy() {
        let report = resolve(
            vec![jar("same", "1", ""), nested_jar("same", "2", "sodium")],
            Vec::new(),
            &options(),
        );
        assert!(report.ok, "{:?}", report.errors);
        assert_eq!(report.order, ["same"]);

        let rows: Vec<&ModStatus> = report.mods.iter().filter(|m| m.id == "same").collect();
        let loading = rows.iter().find(|m| m.status == Status::Loads).unwrap();
        assert_eq!(loading.version, "1", "Loader keeps the root jar");
        let skipped = rows.iter().find(|m| m.status == Status::Skipped).unwrap();
        assert!(
            skipped.reason.as_deref().unwrap().contains("root jar"),
            "{:?}",
            skipped.reason
        );
    }

    #[test]
    fn a_provided_id_satisfies_a_dependency() {
        let report = resolve(
            vec![
                jar("provider", "3.0", r#","provides":["virtual-api"]"#),
                jar("user", "1", r#","depends":{"virtual-api":">=2.0"}"#),
            ],
            Vec::new(),
            &options(),
        );
        assert!(report.ok, "{:?}", report.errors);
        assert_eq!(report.order, ["provider", "user"]);
    }

    #[test]
    fn the_pinned_loader_bundles_mixinextras() {
        let report = resolve(
            vec![jar(
                "lithium",
                "1",
                r#","depends":{"mixinextras":">=0.5.5"}"#,
            )],
            Vec::new(),
            &options(),
        );
        assert!(report.ok, "{:?}", report.errors);
        assert!(report.warnings.is_empty(), "{:?}", report.warnings);
    }

    #[test]
    fn an_unpinned_loader_leaves_bundled_mods_unchecked() {
        let options = Options {
            loader_version: Some("0.16.0".to_string()),
            ..options()
        };
        let report = resolve(
            vec![jar(
                "lithium",
                "1",
                r#","depends":{"mixinextras":">=0.5.5"}"#,
            )],
            Vec::new(),
            &options,
        );
        assert!(!report.ok);
        assert!(
            report
                .warnings
                .iter()
                .any(|warning| warning.message.contains("bundled")),
            "{:?}",
            report.warnings
        );
    }

    #[test]
    fn a_cycle_blocks_its_members() {
        let report = resolve(
            vec![
                jar("aa", "1", r#","depends":{"bb":"*"}"#),
                jar("bb", "1", r#","depends":{"aa":"*"}"#),
            ],
            Vec::new(),
            &options(),
        );
        assert!(!report.ok);
        assert!(report.errors[0].message.contains("circular dependency"));
        assert!(report.order.is_empty());
    }

    #[test]
    fn recommended_mods_only_warn() {
        let report = resolve(
            vec![jar("aa", "1", r#","recommends":{"nice":"*"}"#)],
            Vec::new(),
            &options(),
        );
        assert!(report.ok);
        assert_eq!(status(&report, "aa"), Status::Loads);
        assert!(report.warnings[0].message.contains("`nice`"));
    }
}
