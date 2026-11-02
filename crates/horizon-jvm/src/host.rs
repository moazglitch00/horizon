//! Starting, feeding and supervising the Horizon Java host.
//!
//! The host is a child JVM (`java/horizon-host`), never a library inside this process. That is the
//! safety property in [`ARCHITECTURE.md`](../../../docs/fabric-compatibility/ARCHITECTURE.md): a
//! mod that crashes the JVM, calls `System.exit`, deadlocks or spins cannot take the engine with
//! it. The engine sees a closed pipe, an exit code or a missed heartbeat, and keeps rendering.
//!
//! Frames travel on stdin/stdout; everything the JVM prints (Loader's log, stack traces) goes to
//! stderr and is kept as a tail and an optional log file. The wire contract lives in
//! [`crate::protocol`] and `java/horizon-host/src/horizon/host/Wire.java`.
//!
//! Compilation is cached by a hash of the Java sources, so a build of Horizon that did not touch
//! the host starts it immediately, and a modified source can never be masked by a stale class
//! directory.

use crate::protocol::Frame::{
    Discover, DiscoverResult, Fatal, Hello, HelloAck, Log, Ping, Pong, Shutdown,
};
use crate::protocol::{Frame, PROTOCOL_VERSION, Side, read_frame, write_frame};
use horizon_prepare::java::Jdk;
use std::collections::VecDeque;
use std::fs;
use std::io::{self, BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, ExitStatus, Stdio};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};
use thiserror::Error;

/// How many host stderr lines are kept for an error message.
const TAIL_LINES: usize = 40;

/// The Java sources of the host. `include_str!` ties the class directory's hash to the bytes this
/// Rust crate was built from.
pub const SOURCES: &[(&str, &str)] = &[
    (
        "horizon/host/Wire.java",
        include_str!("../../../java/horizon-host/src/horizon/host/Wire.java"),
    ),
    (
        "horizon/host/Main.java",
        include_str!("../../../java/horizon-host/src/horizon/host/Main.java"),
    ),
    (
        "horizon/host/Discovery.java",
        include_str!("../../../java/horizon-host/src/horizon/host/Discovery.java"),
    ),
    (
        "horizon/host/HeadlessLauncher.java",
        include_str!("../../../java/horizon-host/src/horizon/host/HeadlessLauncher.java"),
    ),
    (
        "net/minecraft/util/Identifier.java",
        include_str!("../../../java/horizon-host/src/net/minecraft/util/Identifier.java"),
    ),
];

/// How the host is started.
#[derive(Clone, Debug)]
pub struct HostConfig {
    /// The `java` executable. Discovery of a real mod set needs Java 25.
    pub java: PathBuf,
    /// The compiled host classes (see [`compile`]).
    pub classes: PathBuf,
    /// The verified Fabric libraries, appended to the host class path.
    pub libs: Vec<PathBuf>,
    /// The game directory: the host's working directory and Loader's config root.
    pub game_dir: PathBuf,
    /// The Minecraft version mods are checked against.
    pub game_version: String,
    pub side: Side,
    /// A file that receives the host's stderr, so a crash can be read after the process is gone.
    pub log: Option<PathBuf>,
    /// The class with `main`, overridable so tests can run a fixture host.
    pub main_class: String,
    /// Spawn to the first `hello` frame.
    pub startup_timeout: Duration,
    /// A ping (`pong`) round trip.
    pub frame_timeout: Duration,
    /// Loader discovery reads and resolves every jar; this is the slow part.
    pub discover_timeout: Duration,
    /// Between `shutdown` and the process leaving.
    pub shutdown_timeout: Duration,
}

impl HostConfig {
    pub fn new(
        java: impl Into<PathBuf>,
        classes: impl Into<PathBuf>,
        libs: Vec<PathBuf>,
        game_dir: impl Into<PathBuf>,
        game_version: impl Into<String>,
        side: Side,
    ) -> Self {
        Self {
            java: java.into(),
            classes: classes.into(),
            libs,
            game_dir: game_dir.into(),
            game_version: game_version.into(),
            side,
            log: None,
            main_class: "horizon.host.Main".to_string(),
            startup_timeout: Duration::from_secs(30),
            frame_timeout: Duration::from_secs(10),
            discover_timeout: Duration::from_secs(120),
            shutdown_timeout: Duration::from_secs(10),
        }
    }
}

/// The first frame: what the host is, and the protocol it speaks.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct HelloInfo {
    pub protocol: u32,
    /// e.g. `horizon-host/0.1.0`.
    pub host: String,
    /// The Fabric Loader version on the host class path.
    pub loader: String,
    pub game_version: String,
    pub side: Side,
}

/// One `discover` answer: Loader's report plus the log lines it emitted while working.
#[derive(Clone, Debug)]
pub struct Discovery {
    pub report: crate::discovery::LoaderReport,
    pub logs: Vec<String>,
}

enum Incoming {
    Frame(Frame),
    Error(String),
    Closed,
}

/// A running Java host. Dropping it kills the child instead of leaving a JVM behind, which is the
/// difference between "closing the game closed everything" and "a Java process outlived the game".
pub struct JavaHost {
    child: Child,
    stdin: Option<ChildStdin>,
    frames: Receiver<Incoming>,
    tail: Arc<Mutex<VecDeque<String>>>,
    stderr: Option<JoinHandle<()>>,
    config: HostConfig,
    hello: Option<HelloInfo>,
    nonce: u64,
}

impl JavaHost {
    /// Starts the host and waits for its `hello` frame.
    pub fn start(config: HostConfig) -> Result<Self, HostError> {
        if let Some(parent) = config.log.as_deref().and_then(Path::parent) {
            fs::create_dir_all(parent).map_err(|e| HostError::Log {
                path: parent.to_path_buf(),
                message: e.to_string(),
            })?;
        }

        fs::create_dir_all(&config.game_dir).map_err(|e| HostError::Log {
            path: config.game_dir.clone(),
            message: e.to_string(),
        })?;

        let mut classpath: Vec<PathBuf> = Vec::with_capacity(config.libs.len() + 1);
        classpath.push(config.classes.clone());
        classpath.extend(config.libs.iter().cloned());

        let mut command = Command::new(&config.java);
        command
            .arg("-cp")
            .arg(horizon_prepare::java::classpath(
                classpath.iter().map(|p| p.as_path()),
            ))
            .arg(&config.main_class)
            .arg("--protocol")
            .arg(PROTOCOL_VERSION.to_string())
            .arg("--game-dir")
            .arg(&config.game_dir)
            .arg("--game-version")
            .arg(&config.game_version)
            .arg("--side")
            .arg(config.side.as_str())
            .current_dir(&config.game_dir)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        no_window(&mut command);

        let mut child = command.spawn().map_err(|source| HostError::Spawn {
            java: config.java.clone(),
            source,
        })?;

        let stdin = child.stdin.take();
        let stdout = child.stdout.take().expect("stdout is piped");
        let stderr = child.stderr.take().expect("stderr is piped");

        let (sender, frames) = mpsc::channel();
        thread::Builder::new()
            .name("horizon-host-frames".to_string())
            .spawn(move || {
                let mut stdout = stdout;
                loop {
                    match read_frame(&mut stdout) {
                        Ok(Some(frame)) => {
                            if sender.send(Incoming::Frame(frame)).is_err() {
                                return;
                            }
                        }
                        Ok(None) => {
                            let _ = sender.send(Incoming::Closed);
                            return;
                        }
                        Err(e) => {
                            let _ = sender.send(Incoming::Error(e.to_string()));
                            let _ = sender.send(Incoming::Closed);
                            return;
                        }
                    }
                }
            })
            .map_err(|e| HostError::Thread(e.to_string()))?;

        let tail = Arc::new(Mutex::new(VecDeque::with_capacity(TAIL_LINES)));
        let stderr_thread = {
            let tail = Arc::clone(&tail);
            let log = config.log.clone();
            thread::Builder::new()
                .name("horizon-host-stderr".to_string())
                .spawn(move || read_stderr(stderr, tail, log))
                .map_err(|e| HostError::Thread(e.to_string()))?
        };

        let mut host = Self {
            child,
            stdin,
            frames,
            tail,
            stderr: Some(stderr_thread),
            config,
            hello: None,
            nonce: 0,
        };

        let hello = match host.next_frame(host.config.startup_timeout, "the host's hello")? {
            Hello {
                protocol,
                host: name,
                loader,
                game_version,
                side,
            } => HelloInfo {
                protocol,
                host: name,
                loader,
                game_version,
                side,
            },
            Fatal { message } => return Err(HostError::Fatal(message)),
            other => {
                return Err(HostError::Unexpected {
                    expected: "hello",
                    got: other.name().to_string(),
                });
            }
        };

        if hello.protocol != PROTOCOL_VERSION {
            return Err(HostError::ProtocolMismatch {
                host: hello.protocol,
                engine: PROTOCOL_VERSION,
            });
        }

        host.send(&HelloAck {
            protocol: PROTOCOL_VERSION,
        })?;
        host.hello = Some(hello);
        Ok(host)
    }

    pub fn hello(&self) -> &HelloInfo {
        self.hello.as_ref().expect("start() waits for hello")
    }

    pub fn side(&self) -> Side {
        self.hello().side
    }

    /// One `ping`/`pong` round trip, timed. Used to prove the host is alive before a long
    /// discovery pass and to measure control latency ([`PERFORMANCE.md`](../../../docs/fabric-compatibility/PERFORMANCE.md)).
    pub fn ping(&mut self) -> Result<Duration, HostError> {
        self.nonce = self.nonce.wrapping_add(1);
        let nonce = self.nonce;
        let start = Instant::now();
        self.send(&Ping { nonce })?;

        loop {
            let frame = self.next_frame(self.config.frame_timeout, "a pong")?;

            match frame {
                Pong { nonce: pong } if pong == nonce => return Ok(start.elapsed()),
                Log { .. } => continue,
                Fatal { message } => return Err(HostError::Fatal(message)),
                other => {
                    return Err(HostError::Unexpected {
                        expected: "pong",
                        got: other.name().to_string(),
                    });
                }
            }
        }
    }

    /// Sends a registry snapshot to the host. Phase 3 bridge: Rust's registries become visible
    /// to Java mod code.
    pub fn send_registry_snapshot(&mut self, json: &str) -> Result<(), HostError> {
        self.send(&Frame::RegistrySnapshot {
            json: json.to_string(),
        })
    }

    /// Sends a named event with a JSON payload. Phase 3 bridge.
    pub fn send_event(&mut self, name: &str, payload: &str) -> Result<(), HostError> {
        self.send(&Frame::Event {
            name: name.to_string(),
            payload: payload.to_string(),
        })
    }

    /// Loads a class from a JAR. Phase 1 of fabric-runtime: real JVM integration.
    pub fn load_class(&mut self, jar_path: &str, class_name: &str) -> Result<String, HostError> {
        self.send(&Frame::LoadClass {
            jar_path: jar_path.to_string(),
            class_name: class_name.to_string(),
        })?;
        loop {
            let frame = self.next_frame(self.config.frame_timeout, "class-loaded")?;
            match frame {
                Frame::ClassLoaded { class_name } => return Ok(class_name),
                Frame::Fatal { message } => return Err(HostError::Fatal(message)),
                Frame::Log { .. } => continue,
                other => {
                    return Err(HostError::Unexpected {
                        expected: "class-loaded",
                        got: other.name().to_string(),
                    });
                }
            }
        }
    }

    /// Invokes a static no-arg method. Phase 1 of fabric-runtime.
    pub fn invoke_method(&mut self, class_name: &str, method_name: &str) -> Result<String, HostError> {
        self.send(&Frame::InvokeMethod {
            class_name: class_name.to_string(),
            method_name: method_name.to_string(),
            args_json: "[]".to_string(),
        })?;
        loop {
            let frame = self.next_frame(self.config.frame_timeout, "invoke-result")?;
            match frame {
                Frame::InvokeResult { result_json } => return Ok(result_json),
                Frame::Fatal { message } => return Err(HostError::Fatal(message)),
                Frame::Log { .. } => continue,
                other => {
                    return Err(HostError::Unexpected {
                        expected: "invoke-result",
                        got: other.name().to_string(),
                    });
                }
            }
        }
    }

    /// Runs Fabric Loader's discovery and resolution over `mods_dir`.
    pub fn discover(&mut self, mods_dir: &Path) -> Result<Discovery, HostError> {
        self.send(&Discover {
            mods_dir: mods_dir.to_string_lossy().into_owned(),
            game_dir: self.config.game_dir.to_string_lossy().into_owned(),
            game_version: self.config.game_version.clone(),
            side: self.config.side,
        })?;

        let mut logs = Vec::new();

        loop {
            let frame = self.next_frame(self.config.discover_timeout, "a discovery report")?;

            match frame {
                DiscoverResult { json } => {
                    let report = crate::discovery::LoaderReport::parse(&json)?;
                    return Ok(Discovery { report, logs });
                }
                Fatal { message } => return Err(HostError::Fatal(message)),
                Log { message, .. } => logs.push(message),
                other => {
                    return Err(HostError::Unexpected {
                        expected: "discover-result",
                        got: other.name().to_string(),
                    });
                }
            }
        }
    }

    /// Asks the host to exit and waits for the process.
    pub fn shutdown(mut self, code: i32) -> Result<ExitStatus, HostError> {
        let write_error = self.send(&Shutdown { code }).err().map(|e| e.to_string());

        if let Some(status) = self.wait_exit(self.config.shutdown_timeout) {
            return Ok(status);
        }

        Err(match write_error {
            Some(message) => HostError::Protocol(format!(
                "the shutdown frame could not be written ({message}), and the host did not exit{}",
                self.tail_text()
            )),
            None => HostError::Timeout {
                what: "the host to exit after shutdown",
                timeout: self.config.shutdown_timeout,
                tail: self.tail_text(),
            },
        })
    }

    /// Kills the host immediately. The engine calls this instead of waiting when a phase is
    /// abandoned; [`Drop`] is the backstop.
    pub fn kill(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
        self.stdin = None;
    }

    fn send(&mut self, frame: &Frame) -> Result<(), HostError> {
        let stdin = self.stdin.as_mut().ok_or_else(|| {
            HostError::Protocol("the host's input pipe is already closed".to_string())
        })?;

        write_frame(stdin, frame).map_err(|e| {
            HostError::Protocol(format!("cannot write {} to the host: {e}", frame.name()))
        })
    }

    fn next_frame(&mut self, timeout: Duration, what: &'static str) -> Result<Frame, HostError> {
        match self.frames.recv_timeout(timeout) {
            Ok(Incoming::Frame(frame)) => Ok(frame),
            Ok(Incoming::Error(message)) => Err(HostError::Protocol(message)),
            Ok(Incoming::Closed) => {
                let status = self.wait_exit(Duration::from_millis(500));
                Err(HostError::Exited {
                    code: status.and_then(|status| status.code()),
                    tail: self.tail_text(),
                })
            }
            Err(RecvTimeoutError::Timeout) => Err(HostError::Timeout {
                what,
                timeout,
                tail: self.tail_text(),
            }),
            Err(RecvTimeoutError::Disconnected) => Err(HostError::Protocol(
                "the host's frame thread stopped".to_string(),
            )),
        }
    }

    fn wait_exit(&mut self, timeout: Duration) -> Option<ExitStatus> {
        let deadline = Instant::now() + timeout;

        loop {
            match self.child.try_wait() {
                Ok(Some(status)) => return Some(status),
                Ok(None) if Instant::now() < deadline => {
                    thread::sleep(Duration::from_millis(10));
                }
                Ok(None) => return None,
                Err(_) => return None,
            }
        }
    }

    fn tail_text(&self) -> String {
        let tail = self.tail.lock().unwrap_or_else(|e| e.into_inner());
        if tail.is_empty() {
            return String::new();
        }

        let lines: Vec<&str> = tail.iter().map(String::as_str).collect();
        format!(
            "\n--- host stderr (last {}) ---\n{}",
            lines.len(),
            lines.join("\n")
        )
    }
}

impl Drop for JavaHost {
    fn drop(&mut self) {
        // A dropped host must not leave a JVM running. The frame and stderr threads end when the
        // pipes close, so there is nothing to join here.
        let _ = self.child.kill();
        let _ = self.child.wait();
        self.stdin = None;
        self.stderr.take();
    }
}

fn read_stderr(stderr: impl io::Read, tail: Arc<Mutex<VecDeque<String>>>, log: Option<PathBuf>) {
    let mut file = log.as_deref().and_then(|path| {
        fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(path)
            .ok()
    });

    for line in BufReader::new(stderr).lines() {
        let Ok(line) = line else {
            break;
        };

        if let Some(file) = file.as_mut() {
            let _ = writeln!(file, "{line}");
        }

        let mut tail = tail.lock().unwrap_or_else(|e| e.into_inner());
        if tail.len() == TAIL_LINES {
            tail.pop_front();
        }
        tail.push_back(line);
    }

    if let Some(file) = file.as_mut() {
        let _ = file.flush();
    }
}

#[cfg(windows)]
fn no_window(command: &mut Command) {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    command.creation_flags(CREATE_NO_WINDOW);
}

#[cfg(not(windows))]
fn no_window(_command: &mut Command) {}

/// A compiled host class directory and the source hash that names it.
#[derive(Clone, Debug)]
pub struct HostBuild {
    pub classes: PathBuf,
    /// The first 16 hex digits of the SHA-256 of [`SOURCES`]; the cache directory's name.
    pub hash: String,
}

/// Compiles [`SOURCES`] into a cache directory named by their hash, or reuses the classes a
/// previous run left there.
///
/// `javac --release 21` keeps the host bytecode runnable on the oldest JVM Fabric supports for
/// this Minecraft version, even when the JDK that compiles it is newer (Java 25 is needed to *run*
/// a mod set with Fabric API, but not to *write* the host). `libs` is the verified Fabric class
/// path the host compiles against.
pub fn compile(jdk: &Jdk, libs: &[PathBuf], cache: &Path) -> Result<HostBuild, HostError> {
    let hash = source_hash();
    let root = cache.join("horizon-host").join(&hash[..16]);
    let classes = root.join("classes");
    let marker = classes.join("horizon").join("host").join("Main.class");

    if marker.is_file() {
        return Ok(HostBuild {
            classes,
            hash: hash[..16].to_string(),
        });
    }

    let source_root = root.join("src");
    for (name, source) in SOURCES {
        let path = source_root.join(name);
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|e| HostError::Compile {
                message: format!("cannot create {}: {e}", parent.display()),
            })?;
        }
        fs::write(&path, source).map_err(|e| HostError::Compile {
            message: format!("cannot write {}: {e}", path.display()),
        })?;
    }

    let temporary = root.join(format!("classes-tmp-{}", std::process::id()));
    let _ = fs::remove_dir_all(&temporary);
    fs::create_dir_all(&temporary).map_err(|e| HostError::Compile {
        message: format!("cannot create {}: {e}", temporary.display()),
    })?;

    let mut command = Command::new(&jdk.javac);
    command
        .arg("--release")
        .arg("21")
        .arg("-encoding")
        .arg("UTF-8")
        .arg("-proc:none")
        .arg("-classpath")
        .arg(horizon_prepare::java::classpath(
            libs.iter().map(|p| p.as_path()),
        ))
        .arg("-d")
        .arg(&temporary);

    for (name, _) in SOURCES {
        command.arg(source_root.join(name));
    }

    no_window(&mut command);

    let output = command.output().map_err(|e| HostError::Compile {
        message: format!("cannot run {}: {e}", jdk.javac.display()),
    })?;

    if !output.status.success() {
        let _ = fs::remove_dir_all(&temporary);
        let stderr = String::from_utf8_lossy(&output.stderr);
        let stdout = String::from_utf8_lossy(&output.stdout);
        return Err(HostError::Compile {
            message: format!(
                "javac exited with {}{}{}",
                output.status,
                line_block("stdout", &stdout),
                line_block("stderr", &stderr)
            ),
        });
    }

    if classes.is_dir() {
        let _ = fs::remove_dir_all(&classes);
    }

    match fs::rename(&temporary, &classes) {
        Ok(()) => {}
        Err(e) if marker.is_file() => {
            // Another process compiled the same hash first; its classes are equivalent.
            let _ = fs::remove_dir_all(&temporary);
            let _ = e;
        }
        Err(e) => {
            let _ = fs::remove_dir_all(&temporary);
            return Err(HostError::Compile {
                message: format!("cannot move the compiled classes into place: {e}"),
            });
        }
    }

    Ok(HostBuild {
        classes,
        hash: hash[..16].to_string(),
    })
}

/// The SHA-256 of [`SOURCES`], lowercase hex.
pub fn source_hash() -> String {
    use sha2::{Digest, Sha256};
    let mut hasher = Sha256::new();

    for (name, source) in SOURCES {
        hasher.update(name.as_bytes());
        hasher.update([0]);
        hasher.update(source.as_bytes());
        hasher.update([0]);
    }

    crate::libs::hex(&hasher.finalize())
}

fn line_block(name: &str, text: &str) -> String {
    if text.trim().is_empty() {
        String::new()
    } else {
        format!("\n--- javac {name} ---\n{}", text.trim_end())
    }
}

/// Why the host could not be compiled, started or understood.
#[derive(Debug, Error)]
pub enum HostError {
    #[error("cannot start {java}: {source}")]
    Spawn {
        java: PathBuf,
        #[source]
        source: io::Error,
    },
    #[error("cannot prepare the host's log or game folder{}: {message}", path.display())]
    Log { path: PathBuf, message: String },
    #[error("the Java host could not be compiled: {message}")]
    Compile { message: String },
    #[error("cannot start a host thread: {0}")]
    Thread(String),
    #[error("the Java host spoke protocol {host}, the engine speaks {engine}")]
    ProtocolMismatch { host: u32, engine: u32 },
    #[error("the Java host: {0}")]
    Protocol(String),
    #[error("the Java host reported: {0}")]
    Fatal(String),
    #[error("the Java host exited (code {}){}", code.map(|c| c.to_string()).unwrap_or_else(|| "unknown".to_string()), tail)]
    Exited { code: Option<i32>, tail: String },
    #[error("the Java host did not send {what} within {timeout:?}{tail}")]
    Timeout {
        what: &'static str,
        timeout: Duration,
        tail: String,
    },
    #[error("expected {expected} from the Java host, got {got}")]
    Unexpected { expected: &'static str, got: String },
    #[error("the discovery report is not valid JSON: {0}")]
    Report(#[from] serde_json::Error),
}
