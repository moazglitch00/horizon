//! The Horizon Java host: Fabric Loader in a child JVM, and the pipe the engine talks over.
//!
//! [Phase 2](https://github.com/horizon/horizon/blob/main/docs/fabric-compatibility/ROADMAP.md)
//! of Fabric compatibility. The Rust engine is authoritative and the JVM is a supervised child
//! (`docs/fabric-compatibility/ARCHITECTURE.md` §6.1): a mod that crashes, hangs or calls
//! `System.exit` takes down the host, never the game, and the engine sees an exit or a missed
//! heartbeat instead of a frozen frame.
//!
//! This crate does one thing so far: it verifies the pinned Fabric libraries on disk, compiles and
//! starts the host, and asks Fabric Loader's own discovery pass what it thinks of a `mods/`
//! folder. It does not launch a game. [`protocol`] documents the wire format; [`host`] supervises
//! the process; [`discovery`] is the cross-check against `horizon-fabric`'s JVM-free report.

pub mod discovery;
pub mod host;
pub mod libs;
pub mod protocol;

pub use discovery::{
    Comparison, LoaderMod, LoaderReport, PingStats, Probe, ProbeConfig, ProbeError, Timings,
    compare,
};
pub use host::{HelloInfo, HostBuild, HostConfig, HostError, JavaHost};
pub use libs::{LibSet, LibsError, PinnedLib};
pub use protocol::{Frame, Side};
