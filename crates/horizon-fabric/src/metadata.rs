//! `fabric.mod.json`, the metadata Fabric Loader reads before any class is loaded.
//!
//! The schema is Loader's own (`schemaVersion` 1 and 2). Unknown keys are accepted on purpose:
//! Loader ignores them, and a mod that adds a key for a newer loader must not fail here, where we
//! only need the parts that decide whether a mod can load.

use serde::{Deserialize, Deserializer, Serialize, de::Error as _};
use std::collections::BTreeMap;

/// Where a mod says it runs.
#[derive(Clone, Copy, Debug, Default, Deserialize, Serialize, PartialEq, Eq)]
pub enum Environment {
    /// `"*"`: both sides.
    #[default]
    #[serde(rename = "*")]
    Both,
    #[serde(rename = "client")]
    Client,
    #[serde(rename = "server")]
    Server,
}

impl Environment {
    pub fn name(self) -> &'static str {
        match self {
            Environment::Both => "*",
            Environment::Client => "client",
            Environment::Server => "server",
        }
    }

    /// Whether the mod loads on `side`.
    pub fn runs_on(self, side: Side) -> bool {
        matches!(
            (self, side),
            (Environment::Both, _)
                | (Environment::Client, Side::Client)
                | (Environment::Server, Side::Server)
        )
    }
}

/// The side a game instance is.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Side {
    Client,
    Server,
}

impl Side {
    pub fn name(self) -> &'static str {
        match self {
            Side::Client => "client",
            Side::Server => "server",
        }
    }
}

/// A `depends` / `breaks` / `recommends` / `suggests` value: one predicate, or alternatives.
#[derive(Clone, Debug, Serialize)]
#[serde(untagged)]
pub enum VersionValue {
    One(String),
    Many(Vec<String>),
}

impl VersionValue {
    pub fn values(&self) -> Vec<String> {
        match self {
            VersionValue::One(value) => vec![value.clone()],
            VersionValue::Many(values) => values.clone(),
        }
    }
}

/// An author: a name, or a name with contact details.
#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(untagged)]
pub enum Author {
    Name(String),
    Detailed {
        name: String,
        #[serde(default)]
        contact: BTreeMap<String, String>,
    },
}

impl Author {
    pub fn name(&self) -> &str {
        match self {
            Author::Name(name) => name,
            Author::Detailed { name, .. } => name,
        }
    }
}

/// An entrypoint: a class name, or a class with a language adapter.
#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(untagged)]
pub enum Entrypoint {
    Class(String),
    Detailed { adapter: String, value: String },
}

impl Entrypoint {
    pub fn value(&self) -> &str {
        match self {
            Entrypoint::Class(value) => value,
            Entrypoint::Detailed { value, .. } => value,
        }
    }

    /// The class the entrypoint names. A value may be `com.example.Mod::init`, a method reference
    /// (Fabric Loader calls that method instead of `onInitialize`); the class is the part before
    /// `::`. Fabric API's networking module uses this form, so treating the whole string as a
    /// class name reports a class that does not exist.
    pub fn class_name(&self) -> &str {
        self.value()
            .split("::")
            .next()
            .unwrap_or(self.value())
            .trim()
    }

    /// The method a `Class::method` entrypoint invokes, when it names one.
    pub fn method_name(&self) -> Option<&str> {
        self.value()
            .split_once("::")
            .map(|(_, method)| method.trim())
            .filter(|method| !method.is_empty())
    }

    /// The adapter that instantiates it (`kotlin` for a Kotlin mod).
    pub fn adapter(&self) -> &str {
        match self {
            Entrypoint::Class(_) => "default",
            Entrypoint::Detailed { adapter, .. } => adapter,
        }
    }
}

/// A mixin config a mod ships.
#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(untagged)]
pub enum MixinEntry {
    Config(String),
    Detailed {
        config: String,
        #[serde(default)]
        environment: Option<Environment>,
    },
}

impl MixinEntry {
    pub fn config(&self) -> &str {
        match self {
            MixinEntry::Config(config) => config,
            MixinEntry::Detailed { config, .. } => config,
        }
    }

    pub fn environment(&self) -> Option<Environment> {
        match self {
            MixinEntry::Config(_) => None,
            MixinEntry::Detailed { environment, .. } => *environment,
        }
    }
}

/// A nested jar: another mod, or a library the mod needs.
#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct NestedJar {
    pub file: String,
}

/// One jar's `fabric.mod.json`.
#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ModJson {
    #[serde(rename = "schemaVersion")]
    pub schema_version: u32,
    pub id: String,
    pub version: String,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub authors: Vec<Author>,
    #[serde(default)]
    pub environment: Environment,
    #[serde(default)]
    pub entrypoints: BTreeMap<String, Vec<Entrypoint>>,
    #[serde(default, deserialize_with = "one_or_many")]
    pub mixins: Vec<MixinEntry>,
    #[serde(default, rename = "accessWidener")]
    pub access_widener: Option<String>,
    #[serde(default)]
    pub jars: Vec<NestedJar>,
    #[serde(default)]
    pub depends: BTreeMap<String, VersionValue>,
    #[serde(default)]
    pub recommends: BTreeMap<String, VersionValue>,
    #[serde(default)]
    pub suggests: BTreeMap<String, VersionValue>,
    #[serde(default)]
    pub breaks: BTreeMap<String, VersionValue>,
    /// The schema-1 spelling of `breaks`; newer loaders read both.
    #[serde(default)]
    pub conflicts: BTreeMap<String, VersionValue>,
    /// Extra ids this mod answers to, so a dependency may name any of them (Loader's `provides`).
    #[serde(default)]
    pub provides: Vec<String>,
    #[serde(default, rename = "languageAdapters")]
    pub language_adapters: BTreeMap<String, String>,
    #[serde(default)]
    pub custom: BTreeMap<String, serde_json::Value>,
}

fn one_or_many<'de, D, T>(deserializer: D) -> Result<Vec<T>, D::Error>
where
    D: Deserializer<'de>,
    T: Deserialize<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum OneOrMany<T> {
        One(T),
        Many(Vec<T>),
    }
    Ok(match OneOrMany::deserialize(deserializer)? {
        OneOrMany::One(one) => vec![one],
        OneOrMany::Many(many) => many,
    })
}

impl ModJson {
    /// Parses and validates one jar's metadata. `source` names the file in every error, since the
    /// point of a check is to tell the player which mod is broken.
    pub fn parse(json: &[u8], source: &str) -> Result<ModJson, String> {
        let text = std::str::from_utf8(json)
            .map_err(|_| format!("{source}: fabric.mod.json is not UTF-8"))?;
        let meta: ModJson =
            serde_json::from_str(text).map_err(|e| format!("{source}: fabric.mod.json: {e}"))?;
        meta.validate(source)?;
        Ok(meta)
    }

    fn validate(&self, source: &str) -> Result<(), String> {
        if !(1..=2).contains(&self.schema_version) {
            return Err(format!(
                "{source}: fabric.mod.json schemaVersion {} is newer than this build reads (1 or 2)",
                self.schema_version
            ));
        }
        if !valid_mod_id(&self.id) {
            return Err(format!(
                "{source}: mod id `{}` is not `[a-z][a-z0-9-_]{{1,63}}`",
                self.id
            ));
        }
        if self.version.trim().is_empty() {
            return Err(format!("{source}: mod `{}` has an empty version", self.id));
        }
        if let Some(path) = &self.access_widener {
            check_relative(path, "accessWidener", &self.id, source)?;
        }
        for mixin in &self.mixins {
            check_relative(mixin.config(), "mixins", &self.id, source)?;
        }
        for jar in &self.jars {
            check_relative(&jar.file, "jars", &self.id, source)?;
        }
        for provided in &self.provides {
            if !valid_mod_id(provided) {
                return Err(format!(
                    "{source}: mod `{}` provides `{provided}`, which is not a mod id",
                    self.id
                ));
            }
            if provided == &self.id {
                return Err(format!(
                    "{source}: mod `{}` lists its own id in `provides`",
                    self.id
                ));
            }
        }
        for (point, entry) in self.entrypoint_classes() {
            let class = entry.class_name();
            if class.is_empty() {
                return Err(format!(
                    "{source}: mod `{}` has an empty `{point}` entrypoint",
                    self.id
                ));
            }
            if class.contains('/') || class.contains(' ') {
                return Err(format!(
                    "{source}: mod `{}` names the `{point}` entrypoint `{}`, which is not a class name",
                    self.id,
                    entry.value()
                ));
            }
            if entry.value().contains("::") && entry.method_name().is_none() {
                return Err(format!(
                    "{source}: mod `{}` names the `{point}` entrypoint `{}` with an empty method after `::`",
                    self.id,
                    entry.value()
                ));
            }
        }
        for (key, value) in &self.depends {
            if key.trim().is_empty() {
                return Err(format!(
                    "{source}: mod `{}` depends on an unnamed mod",
                    self.id
                ));
            }
            for predicate in value.values() {
                crate::version::Predicate::parse(&predicate).map_err(|e| {
                    format!(
                        "{source}: mod `{}` depends on `{key}` with `{predicate}`: {e}",
                        self.id
                    )
                })?;
            }
        }
        Ok(())
    }

    /// The display name: `name`, else the id.
    pub fn display_name(&self) -> &str {
        self.name.as_deref().unwrap_or(&self.id)
    }

    /// Every entrypoint class the mod names, for the load-time "class present" check.
    pub fn entrypoint_classes(&self) -> impl Iterator<Item = (&str, &Entrypoint)> {
        self.entrypoints
            .iter()
            .flat_map(|(point, entries)| entries.iter().map(move |entry| (point.as_str(), entry)))
    }
}

/// `[a-z][a-z0-9-_]{1,63}`: Loader's rule. `fabric-api` and `architectury_api` are both legal
/// here; Horizon's own content-mod ids are stricter and do not allow `-` (`docs/mods.md`).
pub fn valid_mod_id(id: &str) -> bool {
    let mut chars = id.chars();
    match chars.next() {
        Some(first) if first.is_ascii_lowercase() => {}
        _ => return false,
    }
    let rest: Vec<char> = chars.collect();
    (1..=63).contains(&rest.len())
        && rest
            .iter()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || *c == '-' || *c == '_')
}

/// A path inside a jar: relative, `/`-separated, no `..` and no drive or leading separator. The
/// JVM will read these paths, so a value that escapes the jar is refused, not normalised.
fn check_relative(path: &str, field: &str, id: &str, source: &str) -> Result<(), String> {
    let bad = path.is_empty()
        || path.starts_with('/')
        || path.starts_with('\\')
        || path.contains('\\')
        || path.contains(':')
        || path
            .split('/')
            .any(|part| part.is_empty() || part == "." || part == "..");
    if bad {
        return Err(format!(
            "{source}: mod `{id}`: {field} path `{path}` is not a path inside the jar"
        ));
    }
    Ok(())
}

impl<'de> Deserialize<'de> for VersionValue {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let value = serde_json::Value::deserialize(deserializer)?;
        match value {
            serde_json::Value::String(text) => Ok(VersionValue::One(text)),
            serde_json::Value::Array(items) => items
                .into_iter()
                .map(|item| {
                    item.as_str()
                        .map(str::to_string)
                        .ok_or_else(|| D::Error::custom("a version list holds only strings"))
                })
                .collect::<Result<Vec<_>, _>>()
                .map(VersionValue::Many),
            other => Err(D::Error::custom(format!(
                "a version predicate is a string or a list of strings, not {other}"
            ))),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = r#"{
        "schemaVersion": 1,
        "id": "example-mod",
        "version": "1.2.3+build.4",
        "name": "Example",
        "environment": "client",
        "entrypoints": {
            "main": ["com.example.Example"],
            "client": [{ "adapter": "kotlin", "value": "com.example.ClientKt" }],
            "server": ["com.example.Server::start"]
        },
        "mixins": "example.mixins.json",
        "accessWidener": "example.accesswidener",
        "jars": [{ "file": "META-INF/jars/library.jar" }],
        "provides": ["example_api"],
        "depends": {
            "fabricloader": ">=0.19.0",
            "minecraft": [">=26.1", "<26.4"],
            "java": ">=21"
        },
        "recommends": { "fabric-api": "*" },
        "breaks": { "old-mod": "<2.0" },
        "authors": ["A", { "name": "B", "contact": { "homepage": "https://example.com" } }],
        "custom": { "modmenu": { "badges": ["library"] } },
        "somethingNew": true
    }"#;

    #[test]
    fn parses_every_shape_the_schema_allows() {
        let meta = ModJson::parse(SAMPLE.as_bytes(), "example.jar").unwrap();
        assert_eq!(meta.id, "example-mod");
        assert_eq!(meta.environment, Environment::Client);
        assert_eq!(meta.mixins.len(), 1);
        assert_eq!(meta.mixins[0].config(), "example.mixins.json");
        assert_eq!(meta.jars[0].file, "META-INF/jars/library.jar");
        assert_eq!(
            meta.access_widener.as_deref(),
            Some("example.accesswidener")
        );
        assert_eq!(meta.depends["minecraft"].values(), [">=26.1", "<26.4"]);
        assert_eq!(meta.authors[1].name(), "B");
        assert_eq!(meta.entrypoints["main"][0].value(), "com.example.Example");
        assert_eq!(meta.entrypoints["client"][0].adapter(), "kotlin");
        assert_eq!(
            meta.entrypoints["server"][0].class_name(),
            "com.example.Server"
        );
        assert_eq!(meta.entrypoints["server"][0].method_name(), Some("start"));
        assert_eq!(meta.entrypoints["main"][0].method_name(), None);
        assert_eq!(meta.provides, ["example_api"]);
        assert_eq!(meta.display_name(), "Example");
        assert!(meta.custom.contains_key("modmenu"));
    }

    #[test]
    fn a_mixin_list_and_object_forms_both_parse() {
        let json = r#"{"schemaVersion":2,"id":"mm","version":"1",
            "mixins":[{"config":"a.mixins.json","environment":"server"},"b.mixins.json"]}"#;
        let meta = ModJson::parse(json.as_bytes(), "m.jar").unwrap();
        assert_eq!(meta.mixins.len(), 2);
        assert_eq!(meta.mixins[0].environment(), Some(Environment::Server));
        assert_eq!(meta.mixins[1].config(), "b.mixins.json");
    }

    #[test]
    fn ids_follow_the_loaders_rule() {
        assert!(valid_mod_id("fabric-api"));
        assert!(valid_mod_id("architectury_api"));
        assert!(valid_mod_id("a1"));
        assert!(!valid_mod_id("Upper"));
        assert!(!valid_mod_id("-leading"));
        assert!(!valid_mod_id("a"));
        assert!(!valid_mod_id("has space"));
    }

    #[test]
    fn broken_metadata_names_the_file_and_the_reason() {
        let bad_id = r#"{"schemaVersion":1,"id":"Not Valid","version":"1"}"#;
        let error = ModJson::parse(bad_id.as_bytes(), "x.jar").unwrap_err();
        assert!(error.starts_with("x.jar: mod id"));
        assert!(error.contains("Not Valid"));

        let future = r#"{"schemaVersion":3,"id":"a-b","version":"1"}"#;
        assert!(
            ModJson::parse(future.as_bytes(), "x.jar")
                .unwrap_err()
                .contains("newer than this build reads")
        );

        let escape = r#"{"schemaVersion":1,"id":"a-b","version":"1",
            "jars":[{"file":"../../evil.jar"}]}"#;
        assert!(
            ModJson::parse(escape.as_bytes(), "x.jar")
                .unwrap_err()
                .contains("not a path inside the jar")
        );

        let predicate = r#"{"schemaVersion":1,"id":"a-b","version":"1",
            "depends":{"minecraft":">=1.20.x"}}"#;
        let error = ModJson::parse(predicate.as_bytes(), "x.jar").unwrap_err();
        assert!(error.contains("depends on `minecraft`"), "{error}");
        assert!(error.contains("wildcard"), "{error}");

        let own_provides = r#"{"schemaVersion":1,"id":"a-b","version":"1","provides":["a-b"]}"#;
        assert!(
            ModJson::parse(own_provides.as_bytes(), "x.jar")
                .unwrap_err()
                .contains("lists its own id")
        );

        let empty_entrypoint = r#"{"schemaVersion":1,"id":"a-b","version":"1",
            "entrypoints":{"main":["com.example.Mod::"]}}"#;
        let error = ModJson::parse(empty_entrypoint.as_bytes(), "x.jar").unwrap_err();
        assert!(error.contains("empty method after `::`"), "{error}");
    }
}
