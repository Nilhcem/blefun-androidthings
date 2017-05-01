package com.nilhcem.blefun.common;

/**
 * Simplified Ints from Guava
 *
 * @see "https://github.com/google/guava/blob/master/guava/src/com/google/common/primitives/Ints.java"
 **/
public class Ints {

    public static byte[] toByteArray(int value) {
        return new byte[]{
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
        };
    }

    public static int fromByteArray(byte[] bytes) {
        return fromBytes(bytes[0], bytes[1], bytes[2], bytes[3]);
    }

    private static int fromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }
}
