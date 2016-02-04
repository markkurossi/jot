/*

  GetPut.java

  Copyright (c) 2015, Markku Rossi
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

public class GetPut {
    public static int get8Bit(byte[] buf, int ofs) {
        return ((int) buf[ofs]) & 0xff;
    }

    public static int get16Bit(byte[] buf, int ofs) {
        int val = ((int) buf[ofs]) & 0xff;
        val <<= 8;
        val |= ((int) buf[ofs + 1]) & 0xff;

        return val;
    }

    public static int get24Bit(byte[] buf, int ofs) {
        int val = ((int) buf[ofs]) & 0xff;
        val <<= 8;
        val |= ((int) buf[ofs + 1]) & 0xff;
        val <<= 8;
        val |= ((int) buf[ofs + 2]) & 0xff;

        return val;
    }

    public static int get32Bit(byte[] buf, int ofs) {
        int val = ((int) buf[ofs]) & 0xff;
        val <<= 8;
        val |= ((int) buf[ofs + 1]) & 0xff;
        val <<= 8;
        val |= ((int) buf[ofs + 2]) & 0xff;
        val <<= 8;
        val |= ((int) buf[ofs + 3]) & 0xff;

        return val;
    }

    public static void put8Bit(byte[] buf, int pos, int val) {
        buf[pos] = (byte) (val & 0xff);
    }

    public static void put16Bit(byte[] buf, int pos, int val) {
        buf[pos]     = (byte) ((val >>> 8) & 0xff);
        buf[pos + 1] = (byte) (val & 0xff);
    }

    public static void put24Bit(byte[] buf, int pos, int val) {
        buf[pos]     = (byte) ((val >>> 16) & 0xff);
        buf[pos + 1] = (byte) ((val >>> 8) & 0xff);
        buf[pos + 2] = (byte) (val & 0xff);
    }

    public static void put32Bit(byte[] buf, int pos, int val) {
        buf[pos]     = (byte) ((val >>> 24) & 0xff);
        buf[pos + 1] = (byte) ((val >>> 16) & 0xff);
        buf[pos + 2] = (byte) ((val >>> 8) & 0xff);
        buf[pos + 3] = (byte) (val & 0xff);
    }
}
