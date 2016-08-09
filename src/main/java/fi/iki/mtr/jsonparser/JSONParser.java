/*

  JSONParser.java

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

package fi.iki.mtr.jsonparser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Stack;

import fi.iki.mtr.io.ParserInput;

public class JSONParser {
    private ParserInput input;
    private JSONParserListener listener;
    private StringBuilder sb;
    private String tString;

    private enum StackItemType {
        OBJECT, ARRAY, EOF;
    }

    private static final Token[] OBJ_ELEMENTS = {
        Token.vt_STRING_OR_OBJ_END,
        Token.COLON,
        Token.vt_VALUE,
        Token.vt_COMMA_OR_OBJ_END,
    };

    private static final Token[] ARR_ELEMENTS = {
        Token.vt_VALUE_OR_ARR_END,
        Token.vt_COMMA_OR_ARR_END,
    };

    private static final Token[] EOF_ELEMENTS = {
        Token.EOF,
    };

    private class StackItem {
        StackItemType type;
        Token[] elements;
        int elementsPos;

        StackItem(StackItemType type) {
            this.type = type;
            switch (type) {
            case OBJECT:
                elements = OBJ_ELEMENTS;
                break;

            case ARRAY:
                elements = ARR_ELEMENTS;
                break;

            case EOF:
                elements = EOF_ELEMENTS;
                break;
            }
        }

        public boolean accept(Token token, JSONParserListener listener) {
            Token expected = elements[elementsPos];
            boolean result = false;

            switch (expected) {
            case vt_STRING_OR_OBJ_END:
                if (token == Token.STRING || token == Token.SYMBOL) {
                    result = true;
                    listener.onProperty(tString);
                } else if (token == Token.OBJ_END) {
                    result = true;
                }
                break;

            case STRING:
                break;

            case COLON:
                result = (expected == token);
                break;


            case vt_VALUE_OR_ARR_END:
                if (token == Token.ARR_END) {
                    result = true;
                    break;
                }
                /* FALLTHROUG to vt_VALUE. */

            case vt_VALUE:
                switch (token) {
                case OBJ_START:
                case ARR_START:
                    result = true;
                    break;

                case STRING:
                    listener.onStringValue(tString);
                    result = true;
                    break;

                case NUMBER:
                    listener.onNumberValue(42 /*XXX*/);
                    result = true;
                    break;

                case TRUE:
                    listener.onBooleanValue(true);
                    result = true;
                    break;

                case FALSE:
                    listener.onBooleanValue(false);
                    result = true;
                    break;

                case NULL:
                    listener.onNullValue();
                    result = true;
                    break;
                }
                break;

            case vt_COMMA_OR_OBJ_END:
                result = (token == Token.COMMA || token == Token.OBJ_END);
                break;

            case vt_COMMA_OR_ARR_END:
                result = (token == Token.COMMA || token == Token.ARR_END);
                break;
            }

            elementsPos = (++elementsPos % elements.length);

            return result;
        }
    }

    private void accept(Token token) throws JSONParserException {
        if (stack.empty()) {
            if (token == Token.OBJ_START || token == Token.ARR_START) {
                return;
            }
        } else {
            StackItem item = stack.peek();
            if (item.accept(token, listener)) {
                return;
            }
        }

        throw new JSONParserException("Unexpected token: " + token);
    }

    private enum Token {
        EOF,
        OBJ_START,
        OBJ_END,
        ARR_START,
        ARR_END,
        COLON,
        COMMA,
        STRING,
        NUMBER,
        TRUE,
        FALSE,
        NULL,
        SYMBOL,

        vt_VALUE,
        vt_STRING_OR_OBJ_END,
        vt_VALUE_OR_ARR_END,
        vt_COMMA_OR_OBJ_END,
        vt_COMMA_OR_ARR_END
    }

    private Stack<StackItem> stack;

    public JSONParser(File file, JSONParserListener listener)
        throws IOException {
        this(new BufferedReader(new InputStreamReader(new FileInputStream(file),
                                                      "UTF-8")),
             listener);
    }

    public JSONParser(Reader reader, JSONParserListener listener) {
        this.input = new ParserInput(reader);
        this.listener = listener;

        sb = new StringBuilder();
        stack = new Stack<>();
    }

    public void parse() throws JSONParserException {
        while (true) {
            Token token = getToken();
            // System.err.println(token);

            switch (token) {
            case EOF:
                pop(StackItemType.EOF);
                return;

            case OBJ_START:
                listener.onObjectStart();
                accept(token);
                push(StackItemType.OBJECT);
                break;

            case OBJ_END:
                accept(token);
                pop(StackItemType.OBJECT);
                listener.onObjectEnd();
                break;

            case ARR_START:
                listener.onArrayStart();
                accept(token);
                push(StackItemType.ARRAY);
                break;

            case ARR_END:
                accept(token);
                pop(StackItemType.ARRAY);
                listener.onArrayEnd();
                break;

            default:
                accept(token);
            }
        }
    }

    private Token getToken() throws JSONParserException {
        try {
            while (true) {
                int ch = input.getChar();
                switch (ch) {
                case -1:
                    return Token.EOF;

                case '{':
                    return Token.OBJ_START;

                case '}':
                    return Token.OBJ_END;

                case '[':
                    return Token.ARR_START;

                case ']':
                    return Token.ARR_END;

                case ':':
                    return Token.COLON;

                case ',':
                    return Token.COMMA;

                default:
                    if (Character.isWhitespace(ch)) {
                        if (stack.empty()) {
                            throw new JSONParserException("Invalid JSON start: "
                                                          + "expected { or [");
                        }
                        /* Skip whitespace between tokens. */
                        continue;
                    }
                    if (Character.isJavaIdentifierStart(ch)) {
                        sb.setLength(0);
                        sb.append((char) ch);

                        for (ch = input.getChar();
                             Character.isJavaIdentifierStart(ch);
                             ch = input.getChar()) {
                            sb.append((char) ch);
                        }
                        input.ungetChar(ch);

                        tString = sb.toString();
                        switch (tString) {
                        case "true":
                            return Token.TRUE;

                        case "false":
                            return Token.FALSE;

                        case "null":
                            return Token.NULL;

                        default:
                            return Token.SYMBOL;
                        }
                    }
                    if (ch == '"') {
                        sb.setLength(0);

                        while (true) {
                            ch = input.getChar();
                            if (ch == '"') {
                                break;
                            } else if (ch == '\\') {
                                ch = input.getChar();
                                switch (ch) {
                                case '"':
                                    sb.append('"');
                                    break;

                                case '\\':
                                    sb.append('\\');
                                    break;

                                case '/':
                                    sb.append('/');
                                    break;

                                case 'b':
                                    sb.append('\b');
                                    break;

                                case 'f':
                                    sb.append('\f');
                                    break;

                                case 'n':
                                    sb.append('\n');
                                    break;

                                case 'r':
                                    sb.append('\r');
                                    break;

                                case 't':
                                    sb.append('\t');
                                    break;

                                case 'u':
                                    throw new JSONParserException(
                                     	"XXX \\uxxxx not implemented yet");

                                default:
                                    throw new JSONParserException(
                                     	"Invalid escape character in string "
                                        + "constant: \\" + (char) ch);
                                }
                            } else {
                                sb.append((char) ch);
                            }
                        }

                        tString = sb.toString();
                        return Token.STRING;
                    }

                    /* NUMBER */
                    throw new JSONParserException(
                                 	"XXX NUMBERS not implemented yet");
                }
            }
        } catch (IOException e) {
            throw new JSONParserException("I/O error", e);
        }
    }

    private void push(StackItemType type) throws JSONParserException {
        stack.push(new StackItem(type));
    }

    private void pop(StackItemType type) throws JSONParserException {
        if (stack.empty()) {
            throw new JSONParserException("Unmatched end-tag: " + type);
        }
        StackItem item = stack.pop();
        if (item.type != type) {
            throw new JSONParserException("Wrong end-tag: " + type);
        }
        if (stack.empty()) {
            push(StackItemType.EOF);
        }
    }
}
