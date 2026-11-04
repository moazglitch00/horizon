//! `horizon-registry-dump`: prints a JSON registry snapshot from a prepared resources folder.
//!
//! Phase 3 bridge: the Rust engine owns the block/item registries; this binary turns the prepared
//! `facts.json` into the JSON payload that travels over the `registry-snapshot` frame to the Java
//! host. It reads through `horizon_world::registry::BlockRegistry`, so the snapshot is the same
//! identity the game uses, not a second parser.

use horizon_world::registry::BlockRegistry;
use std::fs::File;
use std::io::BufReader;
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
    let file = match File::open(&facts_path) {
        Ok(f) => f,
        Err(e) => {
            eprintln!("cannot read {}: {e}", facts_path.display());
            std::process::exit(1);
        }
    };

    let registry = match BlockRegistry::from_facts_json(BufReader::new(file)) {
        Ok(r) => r,
        Err(e) => {
            eprintln!(
                "{} is not a valid block registry: {e}",
                facts_path.display()
            );
            std::process::exit(1);
        }
    };

    let names = registry.block_names();
    let snapshot = serde_json::json!({
        "kind": "block-states",
        "stateCount": registry.len(),
        "distinctBlocks": names.len(),
        "blocks": names,
    });

    println!("{}", serde_json::to_string(&snapshot).unwrap());
}
