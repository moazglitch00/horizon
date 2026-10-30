use horizon_jvm::protocol::{self, Frame, PROTOCOL_VERSION, Side};
use std::io::Cursor;

#[test]
fn every_frame_round_trips_through_encode_and_decode() {
    let frames = vec![
        Frame::Hello {
            protocol: PROTOCOL_VERSION,
            host: "horizon-host/0.1.0".to_string(),
            loader: "0.19.5".to_string(),
            game_version: "26.3".to_string(),
            side: Side::Client,
        },
        Frame::HelloAck {
            protocol: PROTOCOL_VERSION,
        },
        Frame::Ping {
            nonce: 0x0123_4567_89ab_cdef,
        },
        Frame::Pong {
            nonce: 0x0123_4567_89ab_cdef,
        },
        Frame::Discover {
            mods_dir: "C:\\mods".to_string(),
            game_dir: "C:\\game".to_string(),
            game_version: "26.3".to_string(),
            side: Side::Server,
        },
        Frame::DiscoverResult {
            json: "{\"ok\":true}".to_string(),
        },
        Frame::Log {
            level: 2,
            message: "hello\nworld".to_string(),
        },
        Frame::Fatal {
            message: "boom".to_string(),
        },
        Frame::Shutdown { code: -1 },
        Frame::RegistrySnapshot {
            json: "{\"kind\":\"block-states\",\"count\":2}".to_string(),
        },
        Frame::Event {
            name: "block-placed".to_string(),
            payload: "{\"x\":1}".to_string(),
        },
    ];

    for frame in frames {
        let encoded = frame.encode().expect("encode");
        let decoded = Frame::decode(&encoded).expect("decode");
        assert_eq!(decoded, frame, "round trip failed for {}", frame.name());
    }
}

#[test]
fn control_frames_are_tagged_correctly() {
    assert!(
        Frame::Hello {
            protocol: 1,
            host: String::new(),
            loader: String::new(),
            game_version: String::new(),
            side: Side::Client,
        }
        .is_control()
    );
    assert!(Frame::Ping { nonce: 0 }.is_control());
    assert!(Frame::Pong { nonce: 0 }.is_control());
    assert!(Frame::Shutdown { code: 0 }.is_control());
    assert!(
        !Frame::Discover {
            mods_dir: String::new(),
            game_dir: String::new(),
            game_version: String::new(),
            side: Side::Client,
        }
        .is_control()
    );
}

#[test]
fn trailing_bytes_are_rejected() {
    let mut bytes = Frame::Ping { nonce: 7 }.encode().unwrap();
    bytes.push(0);
    assert!(Frame::decode(&bytes).is_err());
}

#[test]
fn empty_frame_is_rejected() {
    assert!(Frame::decode(&[]).is_err());
}

#[test]
fn unknown_tag_is_rejected() {
    assert!(Frame::decode(&[99]).is_err());
}

#[test]
fn read_and_write_frame_use_the_length_prefix() {
    let frame = Frame::Log {
        level: 3,
        message: "line".to_string(),
    };

    let mut buffer = Vec::new();
    protocol::write_frame(&mut buffer, &frame).expect("write");

    let mut cursor = Cursor::new(buffer);
    let read = protocol::read_frame(&mut cursor)
        .expect("read")
        .expect("frame");
    assert_eq!(read, frame);
    assert!(protocol::read_frame(&mut cursor).unwrap().is_none());
}

#[test]
fn a_truncated_length_is_an_error() {
    let mut cursor = Cursor::new(vec![1u8, 2, 3]);
    assert!(protocol::read_frame(&mut cursor).is_err());
}

#[test]
fn an_oversized_length_is_rejected_before_reading() {
    let mut header = 9_000_000u32.to_le_bytes().to_vec();
    header.extend_from_slice(&[0u8; 4]);
    let mut cursor = Cursor::new(header);
    assert!(protocol::read_frame(&mut cursor).is_err());
}

#[test]
fn side_round_trips() {
    for (side, byte) in [(Side::Client, 0u8), (Side::Server, 1)] {
        assert_eq!(side.wire(), byte);
        assert_eq!(Side::from_wire(byte), Some(side));
        assert_eq!(side.as_str(), if byte == 0 { "client" } else { "server" });
    }
    assert!(Side::from_wire(2).is_none());
    assert!(Side::parse("nope").is_none());
}

#[test]
fn hello_ack_is_a_control_frame() {
    let frame = Frame::HelloAck {
        protocol: PROTOCOL_VERSION,
    };
    let _encoded = frame.encode().unwrap();
    let mut buffer = Vec::new();
    protocol::write_frame(&mut buffer, &frame).unwrap();
    let mut cursor = Cursor::new(buffer);
    let read = protocol::read_frame(&mut cursor).unwrap().unwrap();
    assert_eq!(read, frame);
    assert!(read.is_control());
}
