/*

  Neo4jDao.java

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

package fi.iki.mtr.jot;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import fi.iki.mtr.util.ArrayParamsIterator;
import fi.iki.mtr.util.JSONBuilder;
import fi.iki.mtr.util.ListParamsIterator;
import fi.iki.mtr.util.ParamsIterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.apache.commons.codec.binary.Base64;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Neo4jDao {
    private static Logger log = LoggerFactory.getLogger(Neo4jDao.class);

    private String serverUri;
    private String username;
    private String password;
    private CloseableHttpClient httpClient;

    public static class Statement {
        private CharSequence statement;

        public Statement(CharSequence stmt, ParamsIterator params) {
            statement = JSONBuilder.expand(stmt, params);
        }

        public JSONObject toJSON() {
            JSONObject json = new JSONObject();

            json.put("statement", statement);

            return json;
        }

        @Override
        public String toString() {
            return statement.toString();
        }
    }

    public static class Result {

        /** Result row. */
        public class Row {
            public List<Column> columns;

            public Row() {
                columns = new ArrayList<>();
            }

            public int size() {
                return columns.size();
            }
        }

        /** Result row column. */
        public class Column {
            public String name;
            public Map<String, Object> properties;

            public Column(String name) {
                this.name = name;
                properties = new HashMap<>();
            }

            public void addProperty(String key, Object value) {
                properties.put(key, value);
            }

            public JSONObject toJSON() {
                try {
                    JSONObject json = new JSONObject();

                    for (String key : properties.keySet()) {
                        json.put(key, properties.get(key));
                    }

                    return json;
                } catch (JSONException e) {
                    throw new AssertionError(e);
                }
            }
        }

        /** Result rows. */
        public List<Row> rows;

        public Result(JSONObject json) throws JSONException, IOException {
            JSONArray errors = json.getJSONArray("errors");
            if (errors.length() != 0) {
                String errorMessage = null;

                log.debug("Operation failed:");
                for (int i = 0; i < errors.length(); i++) {
                    JSONObject o = errors.getJSONObject(i);
                    String msg = o.getString("message");

                    log.error("Operation failed: " + o.getString("code")
                              + ": " + msg);

                    if (errorMessage == null) {
                        errorMessage = msg;
                    }
                }
                if (errorMessage == null) {
                    errorMessage = "Storage error";
                }
                throw new IOException("Operation failed: " + errorMessage);
            }

            JSONArray results = json.getJSONArray("results");

            log.debug("results: " + results.toString(2));

            rows = new ArrayList<>();

            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                JSONArray columns = result.getJSONArray("columns");
                JSONArray data = result.getJSONArray("data");

                for (int r = 0; r < data.length(); r++) {
                    Row row = new Row();
                    JSONObject dObj = data.getJSONObject(r);
                    JSONArray rArr = dObj.getJSONArray("row");

                    for (int c = 0; c < rArr.length(); c++) {
                        Column column = new Column(columns.getString(c));
                        Object val = rArr.get(c);
                        if (val instanceof JSONObject) {
                            JSONObject col = (JSONObject) val;

                            Iterator<String> iter = col.keys();
                            while (iter.hasNext()) {
                                String key = iter.next();
                                column.addProperty(key, col.get(key));
                            }
                        } else if (val instanceof Integer) {
                            column.addProperty("{value}", val);
                        } else {
                            throw new AssertionError("Unexpected value: "
                                                     + val);
                        }

                        row.columns.add(column);
                    }

                    rows.add(row);
                }
            }
        }

        public int size() {
            return rows.size();
        }
    }

    public Neo4jDao(String serverUri, String username, String password) {
        this.serverUri = serverUri;
        this.username = username;
        this.password = password;

        httpClient = HttpClients.createDefault();
    }

    public Result execute(CharSequence stmt, Object[] params)
        throws IOException {
        return execute(new Statement(stmt, new ArrayParamsIterator(params)));
    }

    public Result execute(CharSequence stmt, List<Object> params)
        throws IOException {
        return execute(new Statement(stmt, new ListParamsIterator(params)));
    }

    public Result execute(Statement stmt) throws IOException {
        return execute(new Statement[] { stmt });
    }

    public Result execute(Statement[] statements) throws IOException {
        JSONObject json = new JSONObject();
        JSONArray arr = new JSONArray();

        for (Statement stmt : statements) {
            arr.put(stmt.toJSON());
        }

        json.put("statements", arr);

        String url = String.format("%s%s",
                                   serverUri,
                                   "/db/data/transaction/commit");

        HttpPost m = new HttpPost(url);
        m.setEntity(new StringEntity(json.toString(),
                                     ContentType.APPLICATION_JSON));
        sign(m);

        CloseableHttpResponse response = httpClient.execute(m);
        try {
            StatusLine line = response.getStatusLine();
            int code = line.getStatusCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Failed to create task: "
                                      + line.getReasonPhrase());
            }
            JSONObject data
                = new JSONObject(EntityUtils.toString(response.getEntity()));

            return new Result(data);

        } catch (JSONException e) {
            log.error("Failed to parse response JSON", e);
            throw new IOException("Invalid server response");
        } finally {
            response.close();
        }
    }

    private void sign(HttpRequestBase req) {
        try {
            String credentials = username + ":" + password;

            req.addHeader("Authorization",
                          "Basic " + Base64.encodeBase64String(
                                      	credentials.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {}
            httpClient = null;
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
