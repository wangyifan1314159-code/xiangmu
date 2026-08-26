package com.iot.service;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TunnelingMachineFrameDecoderTest {

    @Test
    void decodesSplitAndMergedFramesAfterNoise() {
        AtomicLong invalid = new AtomicLong();
        EmbeddedChannel channel = new EmbeddedChannel(new TunnelingMachineFrameDecoder(invalid::addAndGet));
        byte[] first = frame(0x0511, new byte[]{50, 24, 51, 0, 0, 1, 2, 3});
        byte[] second = frame(0x0521, new byte[]{0, 100, (byte) 0xFF, (byte) 0x9C, 0, 0, 0, 0});

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{9, 8, (byte) 0xAA}));
        channel.writeInbound(Unpooled.wrappedBuffer(slice(first, 1, 7)));
        channel.writeInbound(Unpooled.wrappedBuffer(join(slice(first, 8, first.length - 8), second)));

        TunnelingMachineFrameDecoder.Frame decodedFirst = channel.readInbound();
        TunnelingMachineFrameDecoder.Frame decodedSecond = channel.readInbound();
        assertEquals(0x0511, decodedFirst.function());
        assertArrayEquals(new byte[]{50, 24, 51, 0, 0, 1, 2, 3}, decodedFirst.data());
        assertEquals(0x0521, decodedSecond.function());
        assertEquals(0, invalid.get());
        assertNull(channel.readInbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void dropsBadCrcAndContinuesWithNextFrame() {
        AtomicLong invalid = new AtomicLong();
        EmbeddedChannel channel = new EmbeddedChannel(new TunnelingMachineFrameDecoder(invalid::addAndGet));
        byte[] bad = frame(0x0511, new byte[8]);
        bad[14] ^= 1;
        byte[] valid = frame(0x0531, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        channel.writeInbound(Unpooled.wrappedBuffer(join(bad, valid)));
        TunnelingMachineFrameDecoder.Frame decoded = channel.readInbound();
        assertEquals(0x0531, decoded.function());
        assertEquals(1, invalid.get());
        channel.finishAndReleaseAll();
    }

    @Test
    void decodesTheCompactFrameReceivedFromTheActualDevice() {
        AtomicLong invalid = new AtomicLong();
        EmbeddedChannel channel = new EmbeddedChannel(new TunnelingMachineFrameDecoder(invalid::addAndGet));
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                (byte) 0xAA, 0x0A, 0x01, 0x61, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x21, (byte) 0xF9, 0x55
        }));

        TunnelingMachineFrameDecoder.Frame frame = channel.readInbound();
        assertEquals(0x0161, frame.function());
        assertArrayEquals(new byte[]{1, 1, 1, 1, 1, 1, 1, 1}, frame.data());
        assertEquals(0, invalid.get());
        channel.finishAndReleaseAll();
    }

    @Test
    void appliesProtocolScalingAndSignedTiltValues() {
        List<TunnelingMachineTcpClientConnectionManager.Reading> environment = TunnelingMachineTcpClientConnectionManager.decode(
                new TunnelingMachineFrameDecoder.Frame(0x0511, new byte[]{50, 24, 51, 0, 0, 1, 2, 3}));
        List<TunnelingMachineTcpClientConnectionManager.Reading> tilt = TunnelingMachineTcpClientConnectionManager.decode(
                new TunnelingMachineFrameDecoder.Frame(0x0521, new byte[]{0, 100, (byte) 0xFF, (byte) 0x9C, 0, 0, 0, 0}));

        assertEquals(0.5, environment.stream().filter(v -> v.key().equals("wind_speed")).findFirst().orElseThrow().value());
        assertEquals(123, environment.stream().filter(v -> v.key().equals("methane")).findFirst().orElseThrow().value());
        assertEquals(1.0, tilt.stream().filter(v -> v.key().equals("tilt_x")).findFirst().orElseThrow().value());
        assertEquals(-1.0, tilt.stream().filter(v -> v.key().equals("tilt_y")).findFirst().orElseThrow().value());
    }

    private static byte[] frame(int function, byte[] data) {
        byte[] frame = new byte[17];
        frame[0] = (byte) 0xAA;
        frame[2] = (byte) (function >>> 8);
        frame[3] = (byte) function;
        frame[5] = 8;
        System.arraycopy(data, 0, frame, 6, 8);
        int crc = crc(frame, 1, 13);
        frame[14] = (byte) crc;
        frame[15] = (byte) (crc >>> 8);
        frame[16] = 0x55;
        return frame;
    }

    private static int crc(byte[] source, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= source[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
        }
        return crc & 0xFFFF;
    }

    private static byte[] slice(byte[] source, int offset, int length) { byte[] result = new byte[length]; System.arraycopy(source, offset, result, 0, length); return result; }
    private static byte[] join(byte[] left, byte[] right) { byte[] result = new byte[left.length + right.length]; System.arraycopy(left, 0, result, 0, left.length); System.arraycopy(right, 0, result, left.length, right.length); return result; }
}
