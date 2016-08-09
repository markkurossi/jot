/*

  ParserInput.java

  Copyright (c) 2016, Markku Rossi
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

package fi.iki.mtr.io;

import java.io.IOException;
import java.io.Reader;

public class ParserInput {
    private Reader in;
    private String inputName;

    private static class Position {
        public int line;
        public int column;

        public void Position() {
            line = 1;
            column = 0;
        }

        public void next() {
            column++;
        }

        public void newline() {
            line++;
            column = 0;
        }

        public void set(Position pos) {
            line = pos.line;
            column = pos.column;
        }
    }

    private Position prevPos;
    private Position pos;
    private int ungetch;

    public ParserInput(Reader in) {
        this(in, "{input}");
    }

    public ParserInput(Reader in, String inputName) {
        this.in = in;
        this.inputName = inputName;

        prevPos = new Position();
        pos = new Position();
        ungetch = -1;
    }

    /**
     * Gets the next character from this parser input.
     *
     * @return the next next character or <code>-1</code> if the end
     * of input has been reached.
     * @throws IOException if an I/O exception occurs.
     */
    public int getChar() throws IOException {
        int ret;

        if (ungetch >= 0) {
            ret = ungetch;
            ungetch = -1;
        } else {
            ret = in.read();
        }

        if (ret >= 0) {
            prevPos.set(pos);

            if (ret == '\n') {
                pos.newline();
            } else {
                pos.next();
            }
        }

        return ret;
    }

    public void ungetChar(int ch) {
        pos.set(prevPos);
        ungetch = ch;
    }

    public int getLine() {
        return prevPos.line;
    }

    public int getColumn() {
        return prevPos.column;
    }

    public String getPosition() {
        return String.format("%s:%d:%d",
                             inputName,
                             prevPos.line,
                             prevPos.column);
    }
}
