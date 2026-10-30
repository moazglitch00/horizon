//! `horizon-registry-dump`: prints a JSON registry snapshot from a prepared resources folder.
//!
//! Phase 3 bridge: the Rust engine owns the block/item registries; this binary turns the prepared
//! `facts.json` into the JSON payload that travels over the `registry-snapshot` frame to the Java
//! host. It is intentionally a reader, not a second registry: it never invents identities.

use serde_json::Value;
use std::fs;
use std::path::PathBuf;

fn main() {
    let mut args = std::env::args().skip(1);
    let mut resources: Option<PathBuf> = None;
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--resources" => resources = args.next().map(PathBuf::from),
            other => {
                eprintln!("unknown option `{other}`");
                std::process::exit(2);
            }
        }
    }

    let resources = match resources {
        Some(path) => path,
        None => {
            eprintln!("usage: horizon-registry-dump --resources DIR");
            std::process::exit(2);
        }
    };

    let facts_path = resources.join("facts.json");
    let bytes = match fs::read(&facts_path) {
        Ok(b) => b,
        Err(e) => {
            eprintln!("cannot read {}: {e}", facts_path.display());
            std::process::exit(1);
        }
    };

    let facts: Value = match serde_json::from_slice(&bytes) {
        Ok(v) => v,
        Err(e) => {
            eprintln!("{} is not valid JSON: {e}", facts_path.display());
            std::process::exit(1);
        }
    };

    let states = facts.get("states").and_then(|s| s.as_array()).cloned().unwrap_or_default();

    // Collect distinct block-state names in registry order.
    let mut names: Vec<String> = Vec::new();
    let mut seen = std::collections::BTreeSet::new();
    for row in &states {
        let state = row.as_array().and_then(|r| r.get(4));
        if let Some(state) = state {
            if let Some(id) = state.get("id").and_then(|v| v.as_str()) {
                if seen.insert(id.to_string()) {
                    names.push(id.to_string());
                }
            }
        }
    }

    let snapshot = serde_json::json!({
        "kind": "block-states",
        "count": states.len(),
        "distinctBlocks": names.len(),
        "blocks": names,
    });

    println!("{}", serde_json::to_string(&snapshot).unwrap());
}
