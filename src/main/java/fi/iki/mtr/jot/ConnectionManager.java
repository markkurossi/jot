/*
  ConnectionManager.java

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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;

public class ConnectionManager {
    /** The JDBC connection URL. */
    private String url;

    /** An optional JDBC driver. */
    private String driver;

    /** The maximum connection pool size. */
    private int poolSize;

    /** The number of connections created. */
    private int numConnections;

    /** Is the connection manager initialized? */
    private boolean initialized;

    /** Does the driver has <tt>isValid</tt> method? */
    private boolean hasIsValid;

    private LinkedList<CachedConnection> connections;

    public ConnectionManager(String url) {
        this(url, null);
    }

    public ConnectionManager(String url, String driver) {
        this(url, driver, 1);
    }

    public ConnectionManager(String url, String driver, int poolSize) {
        this.url = url;
        this.driver = driver;

        if (poolSize < 1) {
            throw new IllegalArgumentException("Invalid pool size:" + poolSize);
        }
        this.poolSize = poolSize;

        hasIsValid = true;

        connections = new LinkedList<>();
    }

    public synchronized CachedConnection getConnection() throws SQLException {
        while (true) {
            while (!connections.isEmpty()) {
                CachedConnection conn = connections.removeFirst();
                if (!isValid(conn)) {
                    try {
                        conn.close();
                    } catch (SQLException e) {}
                    continue;
                }

                return conn;
            }
            if (numConnections < poolSize) {
                break;
            }
            while (connections.isEmpty()) {
                try {
                    wait();
                } catch (InterruptedException e) {}
            }
        }

        if (!initialized) {
            if (driver != null) {
                try {
                    Class.forName(driver);
                } catch (ClassNotFoundException e) {
                    throw new SQLException("Could not load JDBC driver", e);
                }
            }
            initialized = true;
        }

        Connection conn = DriverManager.getConnection(url);
        numConnections++;

        return new CachedConnection(conn);
    }

    /**
     * Tests if the argument connection is valid.
     *
     * @param conn the connection to test.
     * @return <tt>true</tt> if the connection is valid and
     * <tt>false</tt> otherwise.
     */
    private boolean isValid(CachedConnection conn) {
        try {
            if (hasIsValid) {
                try {
                    return conn.isValid(1);
                } catch (AbstractMethodError e) {
                    hasIsValid = false;
                }
            }

            conn.prepareStatement("select 1").executeQuery();
            return true;

        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void recycle(CachedConnection conn) {
        connections.add(conn);
        notifyAll();
    }
}
