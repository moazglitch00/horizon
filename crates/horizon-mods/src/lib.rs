//! Horizon's mod files (`docs/mods.md`): content mods (`.hmod`: blocks, items, data and
//! assets, no code), modpacks and, with `index`, the mod registry's index. Code mods are
//! native (docs/native-mods.md).
pub mod api_level;
pub mod content;
pub mod dual;
#[cfg(feature = "index")]
pub mod index;
pub mod manifest;
pub mod modpack;
pub mod validate;

pub use content::Content;
pub use dual::{DiscoveredMod, ModFormat, scan};
pub use validate::{ValidationReport, validate};
pub use manifest::{Capability, Manifest, Package, Side, find};
