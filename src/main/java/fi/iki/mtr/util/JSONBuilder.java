/*

  JSONBuilder.java

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

package fi.iki.mtr.util;

public class JSONBuilder {

    /**
     * Expands the JSON template with arguments. The argument
     * placeholders are marked with `?' characters in the template.
     *
     * @param template the JSON template.
     * @param args the template arguments
     * @return the expanded template string.
     * @throws IllegalArgumentException if the expansion fails.
     */
    public static String expand(CharSequence template, ParamsIterator args)
        throws IllegalArgumentException {
        StringBuilder sb = new StringBuilder();
        int len = template.length();
        int argPos = 0;

        for (int i = 0; i < len; i++) {
            char ch = template.charAt(i);
            if (ch == '?') {
                if (argPos >= args.length()) {
                    throw new IllegalArgumentException("Out of arguments: "
                                                       + argPos);
                }
                Object arg = args.get(argPos++);
                if (arg instanceof String) {
                    sb.append("\"");
                    escapeString((String) arg, sb);
                    sb.append("\"");
                } else {
                    sb.append(String.valueOf(arg));
                }
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    public static void escapeString(String str, StringBuilder sb) {
        int len = str.length();

        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            switch (ch) {
            case '"':
            case '\\':
                sb.append('\\');
                sb.append(ch);
                break;

            default:
                sb.append(ch);
                break;
            }
        }
    }
}
