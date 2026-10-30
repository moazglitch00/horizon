//! `horizon-fabric check <dir>`: the load plan for a `mods/` folder, printed for a player or as
//! JSON for a harness.
//!
//! Exit code 0 when nothing is wrong (warnings are fine), 1 when a mod cannot load, 2 on a usage
//! error. Same conventions as `horizon-port-pack check`.

use horizon_fabric::{ModStatus, Options, Report, Side, Status, check};
use std::path::PathBuf;
use std::process::ExitCode;

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match run(&args) {
        Ok(code) => code,
        Err(message) => {
            eprintln!("horizon-fabric: {message}");
            eprintln!(
                "usage: horizon-fabric check [DIR] [--game-version V] [--side client|server] \
                 [--loader-version V] [--json]"
            );
            ExitCode::from(2)
        }
    }
}

fn run(args: &[String]) -> Result<ExitCode, String> {
    let Some(command) = args.first() else {
        return Err("no command".to_string());
    };
    if command != "check" {
        return Err(format!("unknown command `{command}`"));
    }
    let mut dir = PathBuf::from(horizon_fabric::MODS_DIR);
    let mut options = Options {
        // The game ships the pinned loader (`tools/benchmark-deps/fabric.lock.json`), so a plain
        // `horizon-fabric check mods/` checks against the loader that will actually run.
        loader_version: Some(horizon_fabric::resolve::PINNED_LOADER.to_string()),
        ..Options::default()
    };
    let mut json = false;
    let mut index = 1;
    while let Some(arg) = args.get(index) {
        match arg.as_str() {
            "--json" => json = true,
            "--game-version" => {
                options.game_version = next(args, &mut index)?;
            }
            "--loader-version" => {
                options.loader_version = Some(next(args, &mut index)?);
            }
            "--side" => {
                options.side = match next(args, &mut index)?.as_str() {
                    "client" => Side::Client,
                    "server" => Side::Server,
                    other => return Err(format!("--side is client or server, not `{other}`")),
                };
            }
            other if other.starts_with('-') => {
                return Err(format!("unknown option `{other}`"));
            }
            other => dir = PathBuf::from(other),
        }
        index += 1;
    }

    let report = check(&dir, options);
    if json {
        let text = serde_json::to_string_pretty(&report)
            .map_err(|e| format!("cannot write the report as JSON: {e}"))?;
        println!("{text}");
    } else {
        print_report(&report);
    }
    Ok(if report.ok {
        ExitCode::SUCCESS
    } else {
        ExitCode::FAILURE
    })
}

fn next(args: &[String], index: &mut usize) -> Result<String, String> {
    *index += 1;
    args.get(*index)
        .filter(|value| !value.starts_with('-'))
        .cloned()
        .ok_or_else(|| format!("`{}` needs a value", args[*index - 1]))
}

fn print_report(report: &Report) {
    println!(
        "{} ({} jars, {} mods) for Minecraft {}, {} side",
        report.mods_folder,
        report.jars,
        report.mods.len(),
        report.game_version,
        report.side
    );
    for status in &report.mods {
        println!("  {}", line(status));
    }
    if !report.order.is_empty() {
        println!("load order: {}", report.order.join(", "));
    }
    for warning in &report.warnings {
        println!("warning: {}", prefixed(warning));
    }
    for error in &report.errors {
        println!("error: {}", prefixed(error));
    }
    if report.ok {
        println!("ok: every mod can load in this plan (no Java has run)");
    } else {
        println!("failed: at least one mod cannot load");
    }
}

fn line(status: &ModStatus) -> String {
    let verdict = match status.status {
        Status::Loads => "loads  ",
        Status::Skipped => "skipped",
        Status::Blocked => "blocked",
    };
    let mut details: Vec<String> = Vec::new();
    if status.mixin_configs > 0 {
        details.push(format!("{} mixin config(s)", status.mixin_configs));
    }
    if status.access_widener {
        details.push("access widener".to_string());
    }
    if status.nested_jars > 0 {
        details.push(format!("{} nested jar(s)", status.nested_jars));
    }
    if status.kotlin {
        details.push("kotlin".to_string());
    }
    if status.game_classes > 0 {
        details.push(format!("{} game classes", status.game_classes));
    }
    let reason = status
        .reason
        .as_deref()
        .map(|reason| format!("  ({reason})"))
        .unwrap_or_default();
    format!(
        "{verdict}  {} {}  {}{reason}",
        status.id,
        status.version,
        details.join(", ")
    )
    .trim_end()
    .to_string()
}

fn prefixed(diagnostic: &horizon_fabric::Diagnostic) -> String {
    match &diagnostic.id {
        Some(id) => format!("[{id}] {}", diagnostic.message),
        None => diagnostic.message.clone(),
    }
}
