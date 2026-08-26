package com.iot.service;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.function.LongConsumer;

/** Decodes the fixed-length frames emitted by the tunneling-machine controller. */
final class TunnelingMachineFrameDecoder extends ByteToMessageDecoder {

    static final int DOCUMENTED_FRAME_LENGTH = 17;
    static final int DEVICE_FRAME_LENGTH = 15;
    private final LongConsumer invalidFrameConsumer;

    TunnelingMachineFrameDecoder(LongConsumer invalidFrameConsumer) {
        this.invalidFrameConsumer = invalidFrameConsumer;
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
        while (true) {
            int start = findHeader(in);
            if (start < 0) {
                // Preserve a possible first byte of the next frame.
                if (in.isReadable() && in.getUnsignedByte(in.writerIndex() - 1) == 0xAA) {
                    in.readerIndex(in.writerIndex() - 1);
                } else {
                    in.skipBytes(in.readableBytes());
                }
                return;
            }
            if (start > in.readerIndex()) {
                in.skipBytes(start - in.readerIndex());
            }
            if (in.readableBytes() < 2) {
                return;
            }
            int index = in.readerIndex();
            int length = in.getUnsignedByte(index + 1);
            if (length == 0x0A) {
                if (in.readableBytes() < DEVICE_FRAME_LENGTH) return;
                if (in.getUnsignedByte(index + 14) != 0x55 || !hasValidCompactCrc(in, index)) {
                    invalidFrameConsumer.accept(1);
                    in.skipBytes(1);
                    continue;
                }
                int function = (in.getUnsignedByte(index + 2) << 8) | in.getUnsignedByte(index + 3);
                byte[] data = new byte[8];
                in.getBytes(index + 4, data);
                in.skipBytes(DEVICE_FRAME_LENGTH);
                out.add(new Frame(function, data));
                continue;
            }
            if (in.readableBytes() < DOCUMENTED_FRAME_LENGTH) return;
            if (length != 0 || in.getUnsignedByte(index + 4) != 0
                    || in.getUnsignedByte(index + 5) != 8 || in.getUnsignedByte(index + 16) != 0x55
                    || !hasValidDocumentedCrc(in, index)) {
                invalidFrameConsumer.accept(1);
                in.skipBytes(1);
                continue;
            }
            int function = (in.getUnsignedByte(index + 2) << 8) | in.getUnsignedByte(index + 3);
            byte[] data = new byte[8];
            in.getBytes(index + 6, data);
            in.skipBytes(DOCUMENTED_FRAME_LENGTH);
            out.add(new Frame(function, data));
        }
    }

    private static int findHeader(ByteBuf in) {
        for (int i = in.readerIndex(); i < in.writerIndex(); i++) {
            if (in.getUnsignedByte(i) == 0xAA) return i;
        }
        return -1;
    }

    static boolean hasValidDocumentedCrc(ByteBuf in, int index) {
        int expected = in.getUnsignedByte(index + 14) | (in.getUnsignedByte(index + 15) << 8);
        int crc = crc16Modbus(in, index + 1, 13);
        return crc == expected;
    }

    static boolean hasValidCompactCrc(ByteBuf in, int index) {
        int crc = crc16Modbus(in, index + 2, 10);
        int bigEndian = (in.getUnsignedByte(index + 12) << 8) | in.getUnsignedByte(index + 13);
        int littleEndian = in.getUnsignedByte(index + 12) | (in.getUnsignedByte(index + 13) << 8);
        return crc == bigEndian || crc == littleEndian;
    }

    static int crc16Modbus(ByteBuf in, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= in.getUnsignedByte(i);
            for (int bit = 0; bit < 8; bit++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
        }
        return crc & 0xFFFF;
    }

    record Frame(int function, byte[] data) { }
}
