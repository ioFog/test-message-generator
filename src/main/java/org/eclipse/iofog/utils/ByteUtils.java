/*
 * *******************************************************************************
 *   Copyright (c) 2018 Edgeworx, Inc.
 *
 *   This program and the accompanying materials are made available under the
 *   terms of the Eclipse Public License v. 2.0 which is available at
 *   http://www.eclipse.org/legal/epl-2.0
 *
 *   SPDX-License-Identifier: EPL-2.0
 * *******************************************************************************
 */

package org.eclipse.iofog.utils;

/**
 * Utils class for convenient byte transformations.
 */

public class ByteUtils {

    public static byte[] longToBytes(long x) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; ++i) {
            b[i] = (byte) (x >> (8 - i - 1 << 3));
        }
        return b;
    }

    public static long bytesToLong(byte[] bytes) {
        long result = 0;
        for (byte aByte : bytes) {
            result = (result << 8) + (aByte & 0xff);
        }
        return result;
    }

    public static byte[] integerToBytes(int x) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; ++i) {
            b[i] = (byte) (x >> (4 - i - 1 << 3));
        }
        return b;
    }

    public static int bytesToInteger(byte[] bytes) {
        int result = 0;
        for (byte aByte : bytes) {
            result = (result << 8) + (aByte & 0xff);
        }
        return result;
    }

    public static byte[] shortToBytes(short x) {
        byte[] b = new byte[2];
        for (int i = 0; i < 2; ++i) {
            b[i] = (byte) (x >> (2 - i - 1 << 3));
        }
        return b;
    }

    public static short bytesToShort(byte[] bytes) {
        short result = 0;
        for (byte aByte : bytes) {
            result = (short) ((result << 8) + (aByte & 0xff));
        }
        return result;
    }

    public static byte[] stringToBytes(String s) {
        if (s == null) {
            return new byte[]{};
        } else {
            return s.getBytes();
        }
    }

    public static String bytesToString(byte[] bytes) {
        return new String(bytes);
    }

    public static int getLength(String s) {
        return s != null ? s.length() : 0;
    }
}
