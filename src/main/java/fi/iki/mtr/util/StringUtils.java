/*

  StringUtils.java

  Copyright (c) 2015-2016, Markku Rossi
  All rights reserved.

  BSD 2-Clause License:

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  1. Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.

*/

package fi.iki.mtr.util;

public class StringUtils {
    private static final char[] ALPHABET = {
        '0', '1', '2', '3',
        '4', '5', '6', '7',
        '8', '9', 'a', 'b',
        'c', 'd', 'e', 'f',
    };

    public static String hexEncode(byte[] data) {
        return hexEncode(data, "");
    }

    public static String hexEncode(byte[] data, String byteSeparator) {
        StringBuilder sb = new StringBuilder();

        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(byteSeparator);
            }
            sb.append(ALPHABET[(b >> 4) & 0xf]);
            sb.append(ALPHABET[b & 0xf]);
        }

        return sb.toString();
    }

    public static void hexl(byte[] b, int ofs, int len) {
        hexl(null, b, ofs, len);
    }

    public static void hexl(String label, byte[] b, int ofs, int len) {
        StringBuilder sb = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        int i = 0;
        String indentation = "";

        if (label != null) {
            System.out.println(label);
            indentation = "\t";
        }

        for (; i < len; i += 16) {
            sb.setLength(0);
            ascii.setLength(0);

            sb.append(String.format("%08x:", i));

            for (int j = 0; j < 16; j += 2) {
                if (i + j < len) {
                    sb.append(String.format(" %02x", b[ofs + i + j]));
                    toPrintable(ascii, b[ofs + i + j]);
                } else {
                    sb.append("   ");
                }
                if (i + j + 1 < len) {
                    sb.append(String.format("%02x", b[ofs + i + j + 1]));
                    toPrintable(ascii, b[ofs + i + j + 1]);
                } else {
                    sb.append("  ");
                }
            }

            sb.append("  ");
            sb.append(ascii.toString());

            System.out.print(indentation);
            System.out.println(sb.toString());
        }
    }


    public static void toPrintable(StringBuilder sb, byte b) {
        if (b < 0x20 || b > 0x7e) {
            sb.append('.');
        } else {
            sb.append((char) b);
        }
    }

    public static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
