/*

  Buffer.java

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

public class Buffer {
    private byte[] buf;
    private int dataInBuf;

    public Buffer() {
        buf = new byte[256];
    }

    public Buffer(byte[] data, int ofs, int len) {
        this();
        append(data, ofs, len);
    }

    public boolean isEmpty() {
        return dataInBuf == 0;
    }

    public void clear() {
        dataInBuf = 0;
    }

    public void consume(int amount) {
        if (dataInBuf < amount) {
            throw new IllegalArgumentException(
                                 	String.format("dataInBuf=%d, amount=%d",
                                                      dataInBuf, amount));
        }

        System.arraycopy(buf, amount, buf, 0, dataInBuf - amount);
        dataInBuf -= amount;
    }

    public byte[] data() {
        return buf;
    }

    public int length() {
        return dataInBuf;
    }

    public void append8Bit(int val) {
        ensureCapacity(1);
        GetPut.put8Bit(buf, dataInBuf, val);
        dataInBuf++;
    }

    public void append16Bit(int val) {
        ensureCapacity(2);
        GetPut.put16Bit(buf, dataInBuf, val);
        dataInBuf += 2;
    }

    public void append24Bit(int val) {
        ensureCapacity(3);
        GetPut.put24Bit(buf, dataInBuf, val);
        dataInBuf += 3;
    }

    public void append32Bit(int val) {
        ensureCapacity(4);
        GetPut.put32Bit(buf, dataInBuf, val);
        dataInBuf += 4;
    }

    public void append(byte[] b) {
        append(b, 0, b.length);
    }

    public void append(byte[] b, int ofs, int len) {
        ensureCapacity(len);
        System.arraycopy(b, ofs, buf, dataInBuf, len);
        dataInBuf += len;
    }

    private void ensureCapacity(int amount) {
        if (dataInBuf + amount > buf.length) {
            byte[] n = new byte[dataInBuf + amount + 256];
            System.arraycopy(buf, 0, n, 0, dataInBuf);
            buf = n;
        }
    }
}
