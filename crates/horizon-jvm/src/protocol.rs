//! The pipe between the engine and the Horizon Java host.
//!
//! The contract is deliberately small and explicit, because two languages implement it and a
//! mismatch must fail loudly rather than be guessed:
//!
//! ```text
//! frame   = u32-length (payload bytes, little endian) payload
//! payload = u8 tag, tag fields
//! string  = u32-length, UTF-8 bytes
//! u32/u64 = little endian
//! ```
//!
//! The Java half is `java/horizon-host/src/horizon/host/Wire.java`. Changing a tag, a field or a
//! limit means changing both files and [`PROTOCOL_VERSION`].
//!
//! Control frames (handshake, ping, pong, shutdown) are capped at [`MAX_CONTROL_FRAME`]; payload
//! frames (a discovery report) are capped at [`MAX_FRAME`]. A rejected frame is an error with the
//! length and limit in it, never a partial read.

use std::fmt;
use std::io::{self, Read, Write};

use thiserror::Error;

/// Bumped whenever a tag, a field or a limit changes. The handshake compares it.
pub const PROTOCOL_VERSION: u32 = 1;

/// The largest payload either side will read or write, without the 4-byte length prefix.
pub const MAX_FRAME: usize = 8 << 20;

/// Handshake, ping, pong and shutdown messages are control traffic, not payloads.
pub const MAX_CONTROL_FRAME: usize = 4 << 10;

/// Room for a string field inside a frame: the frame header and tag come out of [`MAX_FRAME`].
const MAX_STRING: usize = MAX_FRAME - 32;

pub const TAG_HELLO: u8 = 1;
pub const TAG_HELLO_ACK: u8 = 2;
pub const TAG_PING: u8 = 3;
pub const TAG_PONG: u8 = 4;
pub const TAG_DISCOVER: u8 = 5;
pub const TAG_DISCOVER_RESULT: u8 = 6;
pub const TAG_LOG: u8 = 7;
pub const TAG_FATAL: u8 = 8;
pub const TAG_SHUTDOWN: u8 = 9;
pub const TAG_REGISTRY_SNAPSHOT: u8 = 10;
pub const TAG_EVENT: u8 = 11;
pub const TAG_LOAD_CLASS: u8 = 12;
pub const TAG_CLASS_LOADED: u8 = 13;
pub const TAG_INVOKE_METHOD: u8 = 14;
pub const TAG_INVOKE_RESULT: u8 = 15;

/// Client or server, the same choice `--side` makes and Fabric's `EnvType` models.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Side {
    Client,
    Server,
}

impl Side {
    pub const fn wire(self) -> u8 {
        match self {
            Self::Client => 0,
            Self::Server => 1,
        }
    }

    pub const fn from_wire(byte: u8) -> Option<Self> {
        match byte {
            0 => Some(Self::Client),
            1 => Some(Self::Server),
            _ => None,
        }
    }

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Client => "client",
            Self::Server => "server",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "client" => Some(Self::Client),
            "server" => Some(Self::Server),
            _ => None,
        }
    }
}

impl fmt::Display for Side {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// One message on the pipe.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Frame {
    /// Host → engine, the first frame: what this host is and what it speaks.
    Hello {
        protocol: u32,
        host: String,
        loader: String,
        game_version: String,
        side: Side,
    },
    /// Engine → host: the protocol it accepted. A mismatch is a startup error naming both.
    HelloAck {
        protocol: u32,
    },
    Ping {
        nonce: u64,
    },
    Pong {
        nonce: u64,
    },
    /// Engine → host: read this folder and report Fabric Loader's verdict.
    Discover {
        mods_dir: String,
        game_dir: String,
        game_version: String,
        side: Side,
    },
    /// Host → engine: the report, as JSON (see `crates/horizon-jvm/src/discovery.rs`).
    DiscoverResult {
        json: String,
    },
    Log {
        level: u8,
        message: String,
    },
    Fatal {
        message: String,
    },
    Shutdown {
        code: i32,
    },
    /// Engine → host: the Rust block/item/entity registries, as JSON. Phase 3 bridge.
    RegistrySnapshot {
        json: String,
    },
    /// Engine → host: a named event with a JSON payload. Phase 3 bridge.
    Event {
        name: String,
        payload: String,
    },
    /// Engine → host: load a class from a JAR. Phase 1 of fabric-runtime.
    LoadClass {
        jar_path: String,
        class_name: String,
    },
    /// Host → engine: class loaded successfully.
    ClassLoaded {
        class_name: String,
    },
    /// Engine → host: invoke a static method. Phase 1 of fabric-runtime.
    InvokeMethod {
        class_name: String,
        method_name: String,
        args_json: String,
    },
    /// Host → engine: method invocation result. Phase 1 of fabric-runtime.
    InvokeResult {
        result_json: String,
    },
}

impl Frame {
    pub const fn tag(&self) -> u8 {
        match self {
            Self::Hello { .. } => TAG_HELLO,
            Self::HelloAck { .. } => TAG_HELLO_ACK,
            Self::Ping { .. } => TAG_PING,
            Self::Pong { .. } => TAG_PONG,
            Self::Discover { .. } => TAG_DISCOVER,
            Self::DiscoverResult { .. } => TAG_DISCOVER_RESULT,
            Self::Log { .. } => TAG_LOG,
            Self::Fatal { .. } => TAG_FATAL,
            Self::Shutdown { .. } => TAG_SHUTDOWN,
            Self::RegistrySnapshot { .. } => TAG_REGISTRY_SNAPSHOT,
            Self::Event { .. } => TAG_EVENT,
            Self::LoadClass { .. } => TAG_LOAD_CLASS,
            Self::ClassLoaded { .. } => TAG_CLASS_LOADED,
            Self::InvokeMethod { .. } => TAG_INVOKE_METHOD,
            Self::InvokeResult { .. } => TAG_INVOKE_RESULT,
        }
    }

    pub const fn name(&self) -> &'static str {
        match self {
            Self::Hello { .. } => "hello",
            Self::HelloAck { .. } => "hello-ack",
            Self::Ping { .. } => "ping",
            Self::Pong { .. } => "pong",
            Self::Discover { .. } => "discover",
            Self::DiscoverResult { .. } => "discover-result",
            Self::Log { .. } => "log",
            Self::Fatal { .. } => "fatal",
            Self::Shutdown { .. } => "shutdown",
            Self::RegistrySnapshot { .. } => "registry-snapshot",
            Self::Event { .. } => "event",
            Self::LoadClass { .. } => "load-class",
            Self::ClassLoaded { .. } => "class-loaded",
            Self::InvokeMethod { .. } => "invoke-method",
            Self::InvokeResult { .. } => "invoke-result",
        }
    }

    /// Handshake, liveness and exit: small messages with the strict size limit.
    pub const fn is_control(&self) -> bool {
        matches!(
            self,
            Self::Hello { .. }
                | Self::HelloAck { .. }
                | Self::Ping { .. }
                | Self::Pong { .. }
                | Self::Shutdown { .. }
        )
    }

    pub fn encode(&self) -> Result<Vec<u8>, ProtocolError> {
        let mut out = Encoder::default();
        out.u8(self.tag());

        match self {
            Self::Hello {
                protocol,
                host,
                loader,
                game_version,
                side,
            } => {
                out.u32(*protocol);
                out.string(host)?;
                out.string(loader)?;
                out.string(game_version)?;
                out.u8(side.wire());
            }
            Self::HelloAck { protocol } => out.u32(*protocol),
            Self::Ping { nonce } | Self::Pong { nonce } => out.u64(*nonce),
            Self::Discover {
                mods_dir,
                game_dir,
                game_version,
                side,
            } => {
                out.string(mods_dir)?;
                out.string(game_dir)?;
                out.string(game_version)?;
                out.u8(side.wire());
            }
            Self::DiscoverResult { json } => out.string(json)?,
            Self::Log { level, message } => {
                out.u8(*level);
                out.string(message)?;
            }
            Self::Fatal { message } => out.string(message)?,
            Self::Shutdown { code } => out.u32(*code as u32),
            Self::RegistrySnapshot { json } => out.string(json)?,
            Self::Event { name, payload } => {
                out.string(name)?;
                out.string(payload)?;
            }
            Self::LoadClass { jar_path, class_name } => {
                out.string(jar_path)?;
                out.string(class_name)?;
            }
            Self::ClassLoaded { class_name } => out.string(class_name)?,
            Self::InvokeMethod {
                class_name,
                method_name,
                args_json,
            } => {
                out.string(class_name)?;
                out.string(method_name)?;
                out.string(args_json)?;
            }
            Self::InvokeResult { result_json } => out.string(result_json)?,
        }

        Ok(out.bytes)
    }

    pub fn decode(payload: &[u8]) -> Result<Self, ProtocolError> {
        if payload.is_empty() {
            return Err(ProtocolError::Empty);
        }

        let mut input = Decoder::new(payload);
        let tag = input.u8()?;

        let frame = match tag {
            TAG_HELLO => {
                let protocol = input.u32()?;
                let host = input.string()?;
                let loader = input.string()?;
                let game_version = input.string()?;
                let side_byte = input.u8()?;
                let side = Side::from_wire(side_byte).ok_or(ProtocolError::Side(side_byte))?;
                Self::Hello {
                    protocol,
                    host,
                    loader,
                    game_version,
                    side,
                }
            }
            TAG_HELLO_ACK => Self::HelloAck {
                protocol: input.u32()?,
            },
            TAG_PING => Self::Ping {
                nonce: input.u64()?,
            },
            TAG_PONG => Self::Pong {
                nonce: input.u64()?,
            },
            TAG_DISCOVER => {
                let mods_dir = input.string()?;
                let game_dir = input.string()?;
                let game_version = input.string()?;
                let side_byte = input.u8()?;
                let side = Side::from_wire(side_byte).ok_or(ProtocolError::Side(side_byte))?;
                Self::Discover {
                    mods_dir,
                    game_dir,
                    game_version,
                    side,
                }
            }
            TAG_DISCOVER_RESULT => Self::DiscoverResult {
                json: input.string()?,
            },
            TAG_LOG => Self::Log {
                level: input.u8()?,
                message: input.string()?,
            },
            TAG_FATAL => Self::Fatal {
                message: input.string()?,
            },
            TAG_SHUTDOWN => Self::Shutdown {
                code: input.u32()? as i32,
            },
            TAG_REGISTRY_SNAPSHOT => Self::RegistrySnapshot {
                json: input.string()?,
            },
            TAG_EVENT => Self::Event {
                name: input.string()?,
                payload: input.string()?,
            },
            TAG_LOAD_CLASS => Self::LoadClass {
                jar_path: input.string()?,
                class_name: input.string()?,
            },
            TAG_CLASS_LOADED => Self::ClassLoaded {
                class_name: input.string()?,
            },
            TAG_INVOKE_METHOD => Self::InvokeMethod {
                class_name: input.string()?,
                method_name: input.string()?,
                args_json: input.string()?,
            },
            TAG_INVOKE_RESULT => Self::InvokeResult {
                result_json: input.string()?,
            },
            other => return Err(ProtocolError::UnknownTag(other)),
        };

        input.finish()?;
        Ok(frame)
    }
}

/// Why a frame could not be encoded or understood.
#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum ProtocolError {
    #[error("empty frame")]
    Empty,
    #[error("unknown frame tag {0}")]
    UnknownTag(u8),
    #[error("frame ended in the middle of {0}")]
    Truncated(&'static str),
    #[error("{0} trailing byte(s) after the last field")]
    Trailing(usize),
    #[error("{what} is {length} bytes, over the {limit} byte limit")]
    TooLarge {
        what: &'static str,
        length: usize,
        limit: usize,
    },
    #[error("invalid UTF-8 in {field}: {source}")]
    Utf8 {
        field: &'static str,
        source: std::str::Utf8Error,
    },
    #[error("side byte {0} is neither client (0) nor server (1)")]
    Side(u8),
}

/// Writes one frame, length prefix included. A control frame over the limit is refused before
/// anything reaches the pipe.
pub fn write_frame(writer: &mut impl Write, frame: &Frame) -> io::Result<()> {
    let payload = frame.encode().map_err(invalid_data)?;

    if frame.is_control() && payload.len() > MAX_CONTROL_FRAME {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!(
                "control frame {} is {} bytes, over the {} byte limit",
                frame.name(),
                payload.len(),
                MAX_CONTROL_FRAME
            ),
        ));
    }

    writer.write_all(&(payload.len() as u32).to_le_bytes())?;
    writer.write_all(&payload)?;
    writer.flush()
}

/// Reads one frame. `Ok(None)` means the other side closed the pipe between frames; a partial
/// length or payload is an error, because a truncated frame is a bug, not an exit.
pub fn read_frame(reader: &mut impl Read) -> io::Result<Option<Frame>> {
    let mut header = [0u8; 4];

    if reader.read(&mut header[..1])? == 0 {
        return Ok(None);
    }

    reader.read_exact(&mut header[1..])?;
    let length = u32::from_le_bytes(header) as usize;

    if length == 0 || length > MAX_FRAME {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("frame length {length} is outside 1..={MAX_FRAME}"),
        ));
    }

    let mut payload = vec![0u8; length];
    reader.read_exact(&mut payload)?;

    Frame::decode(&payload).map(Some).map_err(invalid_data)
}

fn invalid_data(error: ProtocolError) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, error)
}

#[derive(Default)]
struct Encoder {
    bytes: Vec<u8>,
}

impl Encoder {
    fn u8(&mut self, value: u8) {
        self.bytes.push(value);
    }

    fn u32(&mut self, value: u32) {
        self.bytes.extend_from_slice(&value.to_le_bytes());
    }

    fn u64(&mut self, value: u64) {
        self.bytes.extend_from_slice(&value.to_le_bytes());
    }

    fn string(&mut self, value: &str) -> Result<(), ProtocolError> {
        if value.len() > MAX_STRING {
            return Err(ProtocolError::TooLarge {
                what: "string",
                length: value.len(),
                limit: MAX_STRING,
            });
        }

        self.u32(value.len() as u32);
        self.bytes.extend_from_slice(value.as_bytes());
        Ok(())
    }
}

struct Decoder<'a> {
    bytes: &'a [u8],
    at: usize,
}

impl<'a> Decoder<'a> {
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, at: 0 }
    }

    fn take(&mut self, length: usize, what: &'static str) -> Result<&'a [u8], ProtocolError> {
        if self.at + length > self.bytes.len() {
            return Err(ProtocolError::Truncated(what));
        }

        let slice = &self.bytes[self.at..self.at + length];
        self.at += length;
        Ok(slice)
    }

    fn u8(&mut self) -> Result<u8, ProtocolError> {
        Ok(self.take(1, "a u8 field")?[0])
    }

    fn u32(&mut self) -> Result<u32, ProtocolError> {
        let bytes = self.take(4, "a u32 field")?;
        Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn u64(&mut self) -> Result<u64, ProtocolError> {
        let bytes = self.take(8, "a u64 field")?;
        let mut array = [0u8; 8];
        array.copy_from_slice(bytes);
        Ok(u64::from_le_bytes(array))
    }

    fn string(&mut self) -> Result<String, ProtocolError> {
        let length = self.u32()? as usize;

        if length > MAX_STRING {
            return Err(ProtocolError::TooLarge {
                what: "string",
                length,
                limit: MAX_STRING,
            });
        }

        let bytes = self.take(length, "a string")?;
        std::str::from_utf8(bytes)
            .map(str::to_owned)
            .map_err(|source| ProtocolError::Utf8 {
                field: "string",
                source,
            })
    }

    fn finish(&self) -> Result<(), ProtocolError> {
        if self.at == self.bytes.len() {
            Ok(())
        } else {
            Err(ProtocolError::Trailing(self.bytes.len() - self.at))
        }
    }
}
