/*
  CSVBuilder.java

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

package fi.iki.mtr.jot;

import java.text.NumberFormat;
import java.util.Locale;

public class CSVBuilder {
    private StringBuilder sb;
    private boolean bol;
    private NumberFormat df;
    private Format format;

    public enum Format {
        EXCEL,
        UNIX
    }

    public CSVBuilder(Locale locale, Format format) {
        sb = new StringBuilder();
        bol = true;
        df = NumberFormat.getInstance(locale);
        this.format = format;
    }

    public void clear() {
        sb.setLength(0);
    }

    private void next() {
        if (bol) {
            bol = false;
        } else {
            switch (format) {
            case EXCEL:
                sb.append('\t');
                break;

            case UNIX:
                sb.append(',');
                break;
            }
        }
    }

    public CSVBuilder append(String val) {
        next();

        sb.append('"');

        int len = val.length();
        for (int i = 0; i < len; i++) {
            char ch = val.charAt(i);
            switch (ch) {
            case '"':
                sb.append('"');
                sb.append('"');
                break;

            case '\t':
                sb.append(' ');
                break;

            case '\n':
                sb.append("\\n");
                break;

            default:
                sb.append(ch);
                break;
            }
        }
        sb.append('"');

        return this;
    }

    public CSVBuilder append(double val) {
        String s = df.format(val);

        switch (format) {
        case UNIX:
            append(s);
            break;

        case EXCEL:
            next();
            sb.append(s);
        }

        return this;
    }

    public CSVBuilder append(boolean val) {
        append(String.valueOf(val));
        return this;
    }

    public CSVBuilder append() {
        next();
        return this;
    }

    public CSVBuilder nl() {
        sb.append("\n");
        bol = true;

        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
