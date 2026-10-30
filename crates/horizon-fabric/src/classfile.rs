//! A Java class file's constant pool: the names a class refers to, read without loading it.
//!
//! This is what makes a compatibility report possible before a JVM exists: a jar that references
//! `net/minecraft/client/MinecraftClient` and ships a mixin config is a mod that expects the client
//! class, and one that references `com/mojang/blaze3d` expects the render stack. Only the constant
//! pool is parsed; the method bodies are not needed for that, and are not read.

/// The internal class names (as `a/b/C`) a class file's constant pool holds, in first-seen order.
pub fn class_references(bytes: &[u8]) -> Result<Vec<String>, String> {
    let mut reader = Reader { bytes, at: 0 };
    if reader.u4()? != 0xCAFE_BABE {
        return Err("not a class file (no 0xCAFEBABE)".to_string());
    }
    reader.u2()?; // minor
    reader.u2()?; // major
    let count = reader.u2()? as usize;
    if count == 0 {
        return Err("constant pool count is zero".to_string());
    }
    let mut pool: Vec<Entry> = Vec::with_capacity(count);
    // The pool is 1-based. Index 0 is unused, so push a placeholder and keep the indices aligned.
    pool.push(Entry::Other);
    let mut index = 1;
    while index < count {
        let tag = reader.u1()?;
        let entry = match tag {
            1 => {
                let length = reader.u2()? as usize;
                let text = reader.take(length)?;
                Entry::Utf8(String::from_utf8_lossy(text).into_owned())
            }
            7 => Entry::Class(reader.u2()?),
            8 | 16 | 19 | 20 => {
                reader.u2()?;
                Entry::Other
            }
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                reader.u4()?;
                Entry::Other
            }
            5 | 6 => {
                reader.u4()?;
                reader.u4()?;
                // Long and double take two pool slots.
                pool.push(Entry::Other);
                index += 1;
                Entry::Other
            }
            15 => {
                reader.u1()?;
                reader.u2()?;
                Entry::Other
            }
            other => return Err(format!("unknown constant pool tag {other}")),
        };
        pool.push(entry);
        index += 1;
    }

    let mut names = Vec::new();
    for entry in &pool {
        if let Entry::Class(name_index) = entry
            && let Some(Entry::Utf8(name)) = pool.get(*name_index as usize)
            && !name.starts_with('[')
        {
            // Array descriptors (`[Lfoo/Bar;`) are not useful as class references.
            names.push(name.clone());
        }
    }
    names.dedup();
    Ok(names)
}

enum Entry {
    Utf8(String),
    Class(u16),
    Other,
}

struct Reader<'a> {
    bytes: &'a [u8],
    at: usize,
}

impl<'a> Reader<'a> {
    fn take(&mut self, length: usize) -> Result<&'a [u8], String> {
        let end = self
            .at
            .checked_add(length)
            .ok_or_else(|| "class file is truncated".to_string())?;
        let slice = self
            .bytes
            .get(self.at..end)
            .ok_or_else(|| "class file is truncated".to_string())?;
        self.at = end;
        Ok(slice)
    }

    fn u1(&mut self) -> Result<u8, String> {
        Ok(self.take(1)?[0])
    }

    fn u2(&mut self) -> Result<u16, String> {
        let bytes = self.take(2)?;
        Ok(u16::from_be_bytes([bytes[0], bytes[1]]))
    }

    fn u4(&mut self) -> Result<u32, String> {
        let bytes = self.take(4)?;
        Ok(u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }
}

/// A class file with a constant pool naming the given UTF-8 strings, for tests. Only the header
/// and the pool are written: the parser stops there.
#[cfg(test)]
pub fn fixture_class(strings: &[&str]) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(&0xCAFE_BABEu32.to_be_bytes());
    out.extend_from_slice(&0u16.to_be_bytes()); // minor
    out.extend_from_slice(&65u16.to_be_bytes()); // major: Java 21
    out.extend_from_slice(&((1 + strings.len() * 2) as u16).to_be_bytes());
    let mut index = 1u16;
    for text in strings {
        out.push(1); // Utf8
        out.extend_from_slice(&(text.len() as u16).to_be_bytes());
        out.extend_from_slice(text.as_bytes());
        let utf8 = index;
        index += 1;
        out.push(7); // Class, naming the Utf8 before it
        out.extend_from_slice(&utf8.to_be_bytes());
        index += 1;
    }
    let _ = index;
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_class_names_from_the_pool() {
        let bytes = fixture_class(&[
            "java/lang/Object",
            "net/minecraft/world/item/ItemStack",
            "net/minecraft/client/renderer/LevelRenderer",
        ]);
        let names = class_references(&bytes).unwrap();
        assert_eq!(
            names,
            [
                "java/lang/Object",
                "net/minecraft/world/item/ItemStack",
                "net/minecraft/client/renderer/LevelRenderer"
            ]
        );
    }

    #[test]
    fn a_truncated_or_foreign_file_is_an_error_not_a_panic() {
        assert!(class_references(b"not a class").is_err());
        let mut bytes = fixture_class(&["a/B"]);
        bytes.truncate(12);
        assert!(class_references(&bytes).is_err());
    }
}
