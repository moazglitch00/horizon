package net.minecraft.network;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal compatibility shim for Minecraft's FriendlyByteBuf.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 *
 * <p>Wraps a plain byte array and tracks separate reader and writer indices.
 */
public class FriendlyByteBuf {
    private byte[] data;
    private int readerIndex;
    private int writerIndex;

    public FriendlyByteBuf() {
        this(256);
    }

    public FriendlyByteBuf(int capacity) {
        this(new byte[Math.max(16, capacity)]);
    }

    public FriendlyByteBuf(byte[] data) {
        this.data = data == null ? new byte[0] : data;
        this.readerIndex = 0;
        this.writerIndex = this.data.length;
    }

    public byte[] array() {
        return data;
    }

    public int capacity() {
        return data.length;
    }

    public int readerIndex() {
        return readerIndex;
    }

    public void readerIndex(int index) {
        this.readerIndex = index;
    }

    public int writerIndex() {
        return writerIndex;
    }

    public void writerIndex(int index) {
        this.writerIndex = index;
    }

    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    public int writableBytes() {
        return data.length - writerIndex;
    }

    private void ensureWritable(int count) {
        if (writerIndex + count <= data.length) {
            return;
        }
        int newCapacity = Math.max(data.length * 2, writerIndex + count);
        data = Arrays.copyOf(data, newCapacity);
    }

    public byte readByte() {
        if (readerIndex >= writerIndex) {
            throw new IndexOutOfBoundsException("readByte: no readable bytes");
        }
        return data[readerIndex++];
    }

    public void writeByte(int value) {
        ensureWritable(1);
        data[writerIndex++] = (byte) value;
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    public short readShort() {
        int hi = readByte() & 0xFF;
        int lo = readByte() & 0xFF;
        return (short) ((hi << 8) | lo);
    }

    public void writeShort(int value) {
        writeByte((value >> 8) & 0xFF);
        writeByte(value & 0xFF);
    }

    public int readInt() {
        int b0 = readByte() & 0xFF;
        int b1 = readByte() & 0xFF;
        int b2 = readByte() & 0xFF;
        int b3 = readByte() & 0xFF;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public void writeInt(int value) {
        writeByte((value >> 24) & 0xFF);
        writeByte((value >> 16) & 0xFF);
        writeByte((value >> 8) & 0xFF);
        writeByte(value & 0xFF);
    }

    public long readLong() {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (readByte() & 0xFF);
        }
        return value;
    }

    public void writeLong(long value) {
        for (int i = 7; i >= 0; i--) {
            writeByte((int) ((value >> (i * 8)) & 0xFF));
        }
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    public int readVarInt() {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = readByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IllegalStateException("VarInt too big");
            }
        } while ((b & 0x80) != 0);
        return result;
    }

    public void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte(value & 0x7F);
    }

    public String readUtf(int maxLength) {
        return readUtf();
    }

    public String readUtf() {
        int length = readVarInt();
        if (length < 0 || length > readableBytes()) {
            throw new IndexOutOfBoundsException("readUtf: invalid length");
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = readByte();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void writeUtf(String value) {
        if (value == null) {
            value = "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        writeBytes(bytes);
    }

    public byte[] readByteArray() {
        int length = readVarInt();
        if (length < 0 || length > readableBytes()) {
            throw new IndexOutOfBoundsException("readByteArray: invalid length");
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = readByte();
        }
        return bytes;
    }

    public void writeByteArray(byte[] value) {
        if (value == null) {
            value = new byte[0];
        }
        writeVarInt(value.length);
        writeBytes(value);
    }

    public void writeBytes(byte[] value) {
        if (value == null) {
            return;
        }
        ensureWritable(value.length);
        System.arraycopy(value, 0, data, writerIndex, value.length);
        writerIndex += value.length;
    }

    public byte[] readBytes(int length) {
        if (length < 0 || length > readableBytes()) {
            throw new IndexOutOfBoundsException("readBytes: invalid length");
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = readByte();
        }
        return bytes;
    }

    public void clear() {
        readerIndex = 0;
        writerIndex = 0;
    }

    @Override
    public String toString() {
        return "FriendlyByteBuf[readable=" + readableBytes() + ", capacity=" + data.length + "]";
    }
}
