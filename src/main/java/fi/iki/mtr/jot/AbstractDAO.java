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
    private static Logger log = LoggerFactory.getLogger(AbstractDAO.class);
    private ConnectionManager connectionManager;

    protected AbstractDAO(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    protected class Executor<T> {
        private Class<?> cls;

        /** The auto-generated key for the latest insert operation. */
        public int generatedKey;

        public Executor(Class<?> cls) {
            this.cls = cls;
        }

        public List<T> select(String query, Object[] params)
            throws IOException {

            CachedConnection conn = null;
            ResultSet rs = null;
            try {
                conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query);

                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
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
                if (conn != null) {
                    connectionManager.recycle(conn);
                }
            }
        }

        public void insert(T obj) throws IOException {
            CachedConnection conn = null;
            ResultSet keys = null;
            try {
                conn = connectionManager.getConnection();
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
                if (conn != null) {
                    connectionManager.recycle(conn);
                }
            }
        }

        public void insert(List<T> objects) throws IOException {
            CachedConnection conn = null;

            try {
                conn = connectionManager.getConnection();
                String sql = Mapper.toInsertSql(cls);
                System.err.println("SQL: " + sql);
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
                if (conn != null) {
                    connectionManager.recycle(conn);
                }
            }
        }

        public void update(T obj) throws IOException {
        }

        public int executeUpdate(String query, Object[] params)
            throws IOException {

            CachedConnection conn = null;

            try {
                conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query);

                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                return stmt.executeUpdate();
            } catch (SQLException e) {
                log.error("SQL error", e);
                throw new IOException("SQL error", e);
            } finally {
                if (conn != null) {
                    connectionManager.recycle(conn);
                }
            }
        }
    }
}
