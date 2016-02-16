/*
  CachedConnection.java

  Copyright (c) 2013-2016, Markku Rossi.
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

public class CachedConnection {
    /** The wrapped connection. */
    private Connection conn;

    /** Cached prepared statements. */
    private HashMap<String, PreparedStatement> stmts;

    public CachedConnection(Connection conn) {
        this.conn = conn;

        stmts = new HashMap<>();
    }

    public boolean isValid(int timeout) throws SQLException {
        return conn.isValid(timeout);
    }

    public PreparedStatement prepareStatement(String query)
        throws SQLException {
        return prepareStatement(query, Statement.NO_GENERATED_KEYS);
    }

    public PreparedStatement prepareStatement(String query,
                                              int autoGeneratedKeys)
        throws SQLException {

        PreparedStatement stmt = stmts.get(query);
        if (stmt == null) {
            stmt = conn.prepareStatement(query, autoGeneratedKeys);
            stmts.put(query, stmt);
        }

        return stmt;
    }

    public void close() throws SQLException {
        conn.close();
    }
}
