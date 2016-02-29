/*
  AbstractDAO.java

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

import java.io.IOException;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDAO {
    protected static Logger log = LoggerFactory.getLogger(AbstractDAO.class);
    private ConnectionManager connectionManager;

    protected AbstractDAO(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    protected class Executor<T> {
        private Class<?> cls;
        private boolean debug;

        /**
         * An optional connection instance to be used for operations.
         * If this is unset, each operation will allocate and release
         * its own connection.
         */
        protected CachedConnection persistentConnection;

        /** The auto-generated key for the latest insert operation. */
        public int generatedKey;

        public Executor(Class<?> cls) {
            this.cls = cls;
        }

        public void setDebug(boolean debug) {
            this.debug = debug;
        }

        private CachedConnection getConnection() throws SQLException {
            if (persistentConnection != null) {
                return persistentConnection;
            }
            return connectionManager.getConnection();
        }

        private void recycleConnection(CachedConnection conn) {
            if (conn == null || persistentConnection != null) {
                return;
            }
            connectionManager.recycle(conn);
        }

        public List<T> select(String query, Object[] params)
            throws IOException {

            CachedConnection conn = null;
            ResultSet rs = null;
            try {
                conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query);

                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                }

                if (debug) {
                    log.debug("query={}, params={}", query, params);
                }

                ArrayList<T> result = new ArrayList<>();
                rs = stmt.executeQuery();
                while (rs.next()) {
                    result.add((T) Mapper.read(cls.newInstance(), rs));
                }

                return result;
            } catch (SQLException e) {
                log.error("SQL error", e);
                throw new IOException("SQL error", e);
            } catch (InstantiationException|IllegalAccessException e) {
                log.error("Could not create object instance", e);
                throw new IOException("Could not create object instance", e);
            } catch (MapperException e) {
                log.error("Mapper error", e);
                throw new IOException("Mapper error", e);
            } finally {
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (SQLException e) {}
                }
                recycleConnection(conn);
            }
        }

        public void insert(T obj) throws IOException {
            CachedConnection conn = null;
            ResultSet keys = null;
            try {
                conn = getConnection();
                PreparedStatement stmt
                    = conn.prepareStatement(Mapper.toInsertSql(cls),
                                            Statement.RETURN_GENERATED_KEYS);
                Object[] params = Mapper.toSqlParams(obj);

                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                if (stmt.executeUpdate() != 1) {
                    throw new IOException("Failed to insert object");
                }

                keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    generatedKey = keys.getInt(1);
                }

            } catch (SQLException e) {
                log.error("SQL error", e);
                throw new IOException("SQL error", e);
            } catch (MapperException e) {
                log.error("Mapper error", e);
                throw new IOException("Mapper error", e);
            } finally {
                if (keys != null) {
                    try {
                        keys.close();
                    } catch (SQLException e) {}
                }
                recycleConnection(conn);
            }
        }

        public void insert(List<T> objects) throws IOException {
            CachedConnection conn = null;

            try {
                conn = getConnection();
                String sql = Mapper.toInsertSql(cls);
                PreparedStatement stmt
                    = conn.prepareStatement(Mapper.toInsertSql(cls));

                for (T obj : objects) {
                    Object[] params = Mapper.toSqlParams(obj);

                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }

                    stmt.addBatch();
                }

                stmt.executeBatch();

            } catch (SQLException e) {
                log.error("SQL error", e);
                throw new IOException("SQL error", e);
            } catch (MapperException e) {
                log.error("Mapper error", e);
                throw new IOException("Mapper error", e);
            } finally {
                recycleConnection(conn);
            }
        }

        public void update(T obj) throws IOException {
            update(obj, null, null);
        }

        public void update(T obj, String where, Object[] params)
            throws IOException {
            try {
                int count = executeUpdate(Mapper.toUpdateSql(cls, where),
                                          Mapper.toSqlParams(obj, true,
                                                             params));
                if (count != 1) {
                    throw new IOException("Unexpected update count: " + count);
                }
            } catch (MapperException e) {
                log.error("Mapper error", e);
                throw new IOException("Mapper error", e);
            }
        }

        public void delete(T obj) throws IOException {
            try {
                int count = executeUpdate(Mapper.toDeleteSql(cls),
                                          Mapper.toIdParams(obj));
                if (count != 1) {
                    throw new IOException("Unexpected delete count: " + count);
                }
            } catch (MapperException e) {
                log.error("Mapper error", e);
                throw new IOException("Mapper error", e);
            }
        }

        public int executeUpdate(String query, Object[] params)
            throws IOException {

            CachedConnection conn = null;

            try {
                conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query);

                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                if (debug) {
                    log.debug("query={}, params={}", query, params);
                }

                return stmt.executeUpdate();
            } catch (SQLException e) {
                log.error("SQL error", e);
                throw new IOException("SQL error", e);
            } finally {
                recycleConnection(conn);
            }
        }

    }

    protected class TransactionExecutor<T> extends Executor<T> {

        public TransactionExecutor(Class<?> cls) throws IOException {
            super(cls);

            try {
                persistentConnection = connectionManager.getConnection();
                persistentConnection.setAutoCommit(false);
            } catch (SQLException e) {
                log.error("SQL error", e);
                throw new IOException("SQL error", e);
            }
        }

        public void commit() throws IOException {
            try {
                persistentConnection.commit();
            } catch (SQLException e) {
                log.error("SQL error", e);
                throw new IOException("SQL error", e);
            }
        }

        public synchronized void close() {
            if (persistentConnection == null) {
                return;
            }
            try {
                persistentConnection.setAutoCommit(true);
            } catch (SQLException e) {
                log.error("SQL error", e);
            } finally {
                connectionManager.recycle(persistentConnection);
                persistentConnection = null;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }
    }
}
