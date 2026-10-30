//! Fabric's version predicates and version order (`net.fabricmc.loader.impl.util.version`).
//!
//! Fabric compares versions as dot-separated parts, numerically where both parts are numbers and
//! textually otherwise, with a `-prerelease` suffix sorting below the release it belongs to. This
//! module reproduces that for the strings a `fabric.mod.json` can hold: Minecraft's own versions
//! (`26.3`, `1.21.4-pre1`) and mod versions (`0.19.5`, `1.2.3+build.7`).
//!
//! Compared against Fabric Loader 0.19.5, the pinned version
//! (`tools/benchmark-deps/fabric.lock.json`), in Phase 2 of the roadmap: a predicate this module
//! accepts and the loader rejects is a bug here, and the cross-check is what makes that statement
//! testable.

use std::cmp::Ordering;
use std::fmt;

/// A version, ordered as Fabric orders them.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Version {
    /// The dotted parts before a `-prerelease`. Build metadata after `+` is ignored, as semver
    /// says and as Fabric does.
    parts: Vec<String>,
    /// The parts after the first `-`: `1.20.1-pre2` has `["pre2"]`.
    pre: Vec<String>,
}

impl Version {
    /// Parses a version; never fails, because Fabric's parser accepts any non-empty string and
    /// `version::parse`'s only errors are for predicates.
    pub fn parse(text: &str) -> Version {
        let text = text.trim();
        let (rest, pre) = match text.split_once('-') {
            Some((main, pre)) => (main, split_parts(pre)),
            None => (text, Vec::new()),
        };
        let main = rest.split('+').next().unwrap_or(rest);
        Version {
            parts: split_parts(main),
            pre,
        }
    }
}

fn split_parts(text: &str) -> Vec<String> {
    text.split('.')
        .filter(|part| !part.is_empty())
        .map(str::to_string)
        .collect()
}

/// A part compared as a number when it is digits only, else as text; a number sorts above text
/// (`1.0.1` above `1.0-beta`), and text compares without regard to case, as Fabric's does.
fn compare_part(a: &str, b: &str) -> Ordering {
    match (a.parse::<u64>(), b.parse::<u64>()) {
        (Ok(a), Ok(b)) => a.cmp(&b),
        (Ok(_), Err(_)) => Ordering::Greater,
        (Err(_), Ok(_)) => Ordering::Less,
        (Err(_), Err(_)) => a.to_ascii_lowercase().cmp(&b.to_ascii_lowercase()),
    }
}

fn is_zero(part: &str) -> bool {
    part.parse::<u64>() == Ok(0)
}

fn compare_parts(a: &[String], b: &[String]) -> Ordering {
    for i in 0..a.len().max(b.len()) {
        match (a.get(i), b.get(i)) {
            (None, None) => break,
            (None, Some(part)) => {
                if !is_zero(part) {
                    return Ordering::Less;
                }
            }
            (Some(part), None) => {
                if !is_zero(part) {
                    return Ordering::Greater;
                }
            }
            (Some(a), Some(b)) => match compare_part(a, b) {
                Ordering::Equal => {}
                other => return other,
            },
        }
    }
    Ordering::Equal
}

impl Ord for Version {
    fn cmp(&self, other: &Self) -> Ordering {
        match compare_parts(&self.parts, &other.parts) {
            Ordering::Equal => {}
            other => return other,
        }
        match (self.pre.is_empty(), other.pre.is_empty()) {
            (true, true) => Ordering::Equal,
            (true, false) => Ordering::Greater,
            (false, true) => Ordering::Less,
            (false, false) => compare_parts(&self.pre, &other.pre),
        }
    }
}

impl PartialOrd for Version {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl fmt::Display for Version {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.parts.join("."))?;
        if !self.pre.is_empty() {
            write!(f, "-{}", self.pre.join("-"))?;
        }
        Ok(())
    }
}

/// The comparison a predicate's clause applies.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Op {
    Eq,
    Gt,
    Ge,
    Lt,
    Le,
    /// `~1.2.3`: `>=1.2.3 <1.3.0`.
    Tilde,
    /// `^1.2.3`: `>=1.2.3 <2.0.0` (`<0.3.0` for `^0.2.3`).
    Caret,
}

/// One side of a predicate: a version whose last dotted part may be a wildcard (`1.20.x`, `*`).
#[derive(Clone, Debug)]
pub struct Pattern {
    /// The fixed dotted parts. For `1.20.x`, `["1", "20"]`.
    parts: Vec<String>,
    wildcard: bool,
    /// The prerelease of a fixed version (`>=1.20.5-pre1`).
    pre: Vec<String>,
}

impl Pattern {
    fn parse(text: &str) -> Result<Pattern, String> {
        let text = text.trim();
        if text == "*" || text.eq_ignore_ascii_case("x") {
            return Ok(Pattern {
                parts: Vec::new(),
                wildcard: true,
                pre: Vec::new(),
            });
        }
        let (main, pre) = match text.split_once('-') {
            Some((main, pre)) => (main, split_parts(pre)),
            None => (text, Vec::new()),
        };
        let all = split_parts(main.split('+').next().unwrap_or(main));
        if all.is_empty() {
            return Err(format!("`{text}` names no version"));
        }
        let fixed = all
            .iter()
            .position(|part| part.eq_ignore_ascii_case("x") || part == "*")
            .unwrap_or(all.len());
        // A wildcard in the middle (`1.x.3`) is not a version prefix and Fabric rejects it.
        if all[fixed..]
            .iter()
            .any(|part| !(part.eq_ignore_ascii_case("x") || part == "*"))
        {
            return Err(format!(
                "`{text}` has a wildcard before a fixed part; only a trailing `x` is a range"
            ));
        }
        Ok(Pattern {
            parts: all[..fixed].to_vec(),
            wildcard: fixed < all.len(),
            pre,
        })
    }

    /// The version the pattern starts at: wildcards read as `.0`.
    fn floor(&self) -> Version {
        Version {
            parts: self.parts.clone(),
            pre: self.pre.clone(),
        }
    }

    /// `1.20.x` elides to the prefix `1.20`, which is what a range's upper bound is built from.
    fn prefix(&self) -> &[String] {
        &self.parts
    }

    fn matches(&self, version: &Version) -> bool {
        if self.parts.is_empty() {
            // `*`
            return true;
        }
        if version.parts.len() < self.parts.len() {
            return false;
        }
        for (i, part) in self.parts.iter().enumerate() {
            if compare_part(part, &version.parts[i]) != Ordering::Equal {
                return false;
            }
        }
        // `1.20.x` matches `1.20.3` and, as Fabric's prefix form does, `1.20`.
        if self.wildcard {
            return true;
        }
        compare_parts(&self.parts, &version.parts) == Ordering::Equal && version.pre == self.pre
    }

    /// `~`: the last written part may change: `1.2.3` → `1.3`, `1.2` → `1.3`, `1` → `2`.
    fn tilde_ceiling(&self) -> Version {
        let mut parts = self.prefix().to_vec();
        // `~1.2.3` holds `1.2` fixed; `~1.2` and `~1` change their last part.
        let keep = parts.len().saturating_sub(1).max(2).min(parts.len());
        parts.truncate(keep);
        if let Some(last) = parts.last_mut() {
            let bumped = last.parse::<u64>().unwrap_or(0) + 1;
            *last = bumped.to_string();
        }
        Version {
            parts,
            pre: Vec::new(),
        }
    }

    /// `^`: semver's rule, the first non-zero part may not change.
    fn caret_ceiling(&self) -> Version {
        let mut parts = self.prefix().to_vec();
        let index = parts
            .iter()
            .position(|part| part.parse::<u64>() != Ok(0))
            .unwrap_or(parts.len().saturating_sub(1));
        parts.truncate(index + 1);
        if let Some(last) = parts.last_mut() {
            let bumped = last.parse::<u64>().unwrap_or(0) + 1;
            *last = bumped.to_string();
        }
        Version {
            parts,
            pre: Vec::new(),
        }
    }
}

/// One comparison of a predicate.
#[derive(Clone, Debug)]
pub struct Clause {
    op: Op,
    pattern: Pattern,
}

/// A predicate: clauses separated by spaces all have to hold (AND).
#[derive(Clone, Debug)]
pub struct Predicate {
    clauses: Vec<Clause>,
}

impl Predicate {
    /// Parses one predicate, as one string of a `depends` value.
    pub fn parse(text: &str) -> Result<Predicate, String> {
        let mut clauses = Vec::new();
        for word in text.split_whitespace() {
            let (op, version) = if let Some(rest) = word.strip_prefix(">=") {
                (Op::Ge, rest)
            } else if let Some(rest) = word.strip_prefix("<=") {
                (Op::Le, rest)
            } else if let Some(rest) = word.strip_prefix('>') {
                (Op::Gt, rest)
            } else if let Some(rest) = word.strip_prefix('<') {
                (Op::Lt, rest)
            } else if let Some(rest) = word.strip_prefix('=') {
                (Op::Eq, rest)
            } else if let Some(rest) = word.strip_prefix('~') {
                (Op::Tilde, rest)
            } else if let Some(rest) = word.strip_prefix('^') {
                (Op::Caret, rest)
            } else {
                (Op::Eq, word)
            };
            if version.trim().is_empty() {
                return Err(format!("`{word}` in `{text}` has no version"));
            }
            let pattern = Pattern::parse(version)?;
            // A wildcard with an ordering operator has no agreed meaning; Fabric's parser rejects
            // it and guessing here would quietly widen a mod's requirement.
            if pattern.wildcard && matches!(op, Op::Gt | Op::Ge | Op::Lt | Op::Le) {
                return Err(format!(
                    "`{word}`: a wildcard cannot be used with `{}`",
                    match op {
                        Op::Gt => ">",
                        Op::Ge => ">=",
                        Op::Lt => "<",
                        _ => "<=",
                    }
                ));
            }
            clauses.push(Clause { op, pattern });
        }
        if clauses.is_empty() {
            return Err("an empty predicate matches nothing".to_string());
        }
        Ok(Predicate { clauses })
    }

    /// Whether `version` satisfies every clause.
    pub fn matches(&self, version: &str) -> bool {
        let version = Version::parse(version);
        self.clauses.iter().all(|clause| {
            let pattern = &clause.pattern;
            let order = if pattern.matches(&version) {
                Ordering::Equal
            } else {
                version.cmp(&pattern.floor())
            };
            match clause.op {
                Op::Eq => pattern.matches(&version),
                Op::Gt => order == Ordering::Greater,
                Op::Ge => order != Ordering::Less,
                Op::Lt => version < pattern.floor(),
                Op::Le => version <= pattern.floor(),
                Op::Tilde => {
                    pattern.matches(&version)
                        || (order != Ordering::Less && version < pattern.tilde_ceiling())
                }
                Op::Caret => {
                    pattern.matches(&version)
                        || (order != Ordering::Less && version < pattern.caret_ceiling())
                }
            }
        })
    }
}

/// The alternatives a `depends` value lists: a string is one predicate, an array is OR.
#[derive(Clone, Debug, Default)]
pub struct Predicates(Vec<Predicate>);

impl Predicates {
    /// Parses a JSON-schema `depends` value: one predicate string, or several as alternatives.
    pub fn parse(values: &[String]) -> Result<Predicates, String> {
        let mut predicates = Vec::new();
        for value in values {
            predicates.push(Predicate::parse(value)?);
        }
        // The loader treats a missing or empty requirement as `*`.
        if predicates.is_empty() {
            predicates.push(Predicate::parse("*").expect("`*` parses"));
        }
        Ok(Predicates(predicates))
    }

    pub fn matches(&self, version: &str) -> bool {
        self.0.iter().any(|predicate| predicate.matches(version))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn yes(predicate: &str, version: &str) -> bool {
        Predicate::parse(predicate).unwrap().matches(version)
    }

    #[test]
    fn a_bare_version_is_equality() {
        assert!(yes("26.3", "26.3"));
        assert!(!yes("26.3", "26.4"));
        assert!(yes("1.20", "1.20.0"), "trailing zero parts are equal");
        assert!(
            !yes("1.20.1", "1.20"),
            "a longer version is above the shorter one"
        );
    }

    #[test]
    fn ranges_bind_on_the_written_side() {
        assert!(yes(">=1.20", "1.20"));
        assert!(yes(">=1.20", "1.21.4"));
        assert!(!yes(">=1.20", "1.19.4"));
        assert!(yes(">1.20", "1.20.1"));
        assert!(!yes(">1.20", "1.20"));
        assert!(yes("<1.21", "1.20.6"));
        assert!(!yes("<1.21", "1.21"));
        assert!(yes("<=1.21", "1.21"));
        assert!(yes(">=1.20 <1.21", "1.20.4"));
        assert!(!yes(">=1.20 <1.21", "1.21"));
        assert!(yes(">=26.1", "26.3"));
    }

    #[test]
    fn tilde_and_caret_follow_semver() {
        assert!(yes("~1.2.3", "1.2.9"));
        assert!(!yes("~1.2.3", "1.3"));
        assert!(yes("~1.2", "1.2.9"), "`~1.2` is 1.2.x");
        assert!(!yes("~1.2", "1.3"));
        assert!(yes("~1", "1.9"));
        assert!(!yes("~1", "2.0"));
        assert!(yes("~26.3", "26.3.4"));
        assert!(!yes("~26.3", "26.4"));
        assert!(yes("^1.2.3", "1.9"));
        assert!(!yes("^1.2.3", "2.0"));
        assert!(yes("^0.2.3", "0.2.9"));
        assert!(!yes("^0.2.3", "0.3.0"));
        assert!(yes("^0.0.3", "0.0.3"));
        assert!(!yes("^0.0.3", "0.0.4"));
    }

    #[test]
    fn wildcards_are_prefixes() {
        assert!(yes("*", "26.3"));
        assert!(yes("1.20.x", "1.20.4"));
        assert!(yes("1.20.x", "1.20"));
        assert!(!yes("1.20.x", "1.21"));
        assert!(yes("1.x", "1.21.4"));
        assert!(!yes("1.x", "2.0"));
        assert_eq!(
            Predicate::parse(">=1.20.x").unwrap_err(),
            "`>=1.20.x`: a wildcard cannot be used with `>=`"
        );
        assert!(Predicate::parse("1.x.3").is_err());
    }

    #[test]
    fn prereleases_sort_below_their_release() {
        assert!(!yes(">=1.21", "1.21-pre1"));
        assert!(yes("<1.21", "1.21-pre1"));
        assert!(yes(">=1.20.5-pre1", "1.20.5-pre2"));
        assert!(Version::parse("1.20.5") > Version::parse("1.20.5-pre2"));
        assert!(Version::parse("1.20.5-rc1") > Version::parse("1.20.5-pre2"));
    }

    #[test]
    fn alternatives_are_a_json_array() {
        let predicates = Predicates::parse(&["1.20".into(), ">=1.21".into()]).unwrap();
        assert!(predicates.matches("1.20"));
        assert!(predicates.matches("1.21.4"));
        assert!(!predicates.matches("1.20.4"));
        assert!(Predicates::parse(&[]).unwrap().matches("anything"));
    }

    #[test]
    fn empty_and_broken_predicates_are_errors() {
        assert!(Predicate::parse("").is_err());
        assert!(Predicate::parse(">=").is_err());
        assert!(Predicate::parse(">= ").is_err());
        assert!(Predicate::parse("1 . 2").is_err());
    }
}
