/*

  Mapper.java

  Copyright (c) 2013-2016, Markku Rossi
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;

import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fi.iki.mtr.util.StringUtils.isEmpty;

/**
 * Mapper mapping data between JSON, objects, and database tables.
 */
public class Mapper {
    private static Logger log = LoggerFactory.getLogger(Mapper.class);

    private enum Type {
        INT, INTEGER, CHAR, CHARACTER, STRING, BOOLEAN, DATE;
    }

    /** Information about class fields. */
    private static class FieldInfo {
        /** The reflected field. */
        java.lang.reflect.Field field;

        /** The field type. */
        Type type;

        /** Is this field an ID field? */
        boolean isId;

        /** Should the ID field be skipped in SQL write operations? */
        boolean idAutoAssign;

        /** Is the field read-only i.e. skipped in SQL write operations? */
        boolean readOnly;

        /** The field's JSON attribute name. */
        String jsonName;

        /** The field's DB column name. */
        String dbName;

        /** XML attribute or child element. */
        boolean xmlAttribute;

        /** The field's XML attribute or child element name. */
        String xmlName;

        /** An optional parser for dates. */
        SimpleDateFormat dateFormat;

        FieldInfo(java.lang.reflect.Field field) throws MapperException {
            this.field = field;

            String dateFormat = null;

            Field ann = field.getAnnotation(Field.class);
            if (ann != null) {
                isId = ann.id();
                idAutoAssign = ann.idAutoAssign();
                readOnly = ann.readOnly();
                jsonName = ann.jsonName();
                dbName = ann.dbName();
                xmlAttribute = ann.xmlAttribute();
                xmlName = ann.xmlName();
                dateFormat = ann.dateFormat();
            }
            if (isEmpty(jsonName)) {
                jsonName = field.getName();
            }
            if (isEmpty(dbName)) {
                dbName = field.getName();
            }
            if (isEmpty(xmlName)) {
                xmlName = field.getName();
            }
            if (!isEmpty(dateFormat)) {
                this.dateFormat = makeDateFormat(dateFormat);
            }

            Class<?> cls = field.getType();
            if (cls == int.class) {
                type = Type.INT;
            } else if (cls == Integer.class) {
                type = Type.INTEGER;
            } else if (cls == char.class) {
                type = Type.CHAR;
            } else if (cls == Character.class) {
                type = Type.CHARACTER;
            } else if (cls == String.class) {
                type = Type.STRING;
            } else if (cls == boolean.class) {
                type = Type.BOOLEAN;
            } else if (cls == Date.class) {
                type = Type.DATE;
            } else {
                throw new MapperException("Unsupport field type '"
                                          + cls.getName() + "'");
            }
        }
    }

    private static boolean isEmpty(String val) {
        return val == null || val.length() == 0;
    }

    /** Information about classes. */
    private static class ClassInfo {
        String dbTableName;
        FieldInfo[] fields;

        HashMap<String, FieldInfo> fieldsByJsonName;
        HashMap<String, FieldInfo> fieldsByDbName;

        ClassInfo(Class<?> cls) throws MapperException {
            Record ann = cls.getAnnotation(Record.class);
            if (ann != null) {
                dbTableName = ann.dbName();
            }
            if (isEmpty(dbTableName)) {
                dbTableName = cls.getSimpleName().toLowerCase();
                /* XXX make valid plural form.
                   http://www.grammar.cl/Notes/Plural_Nouns.htm */
                dbTableName += "s";
            }

            try {

                ArrayList<FieldInfo> arr = new ArrayList<>();

                for (java.lang.reflect.Field field : cls.getFields()) {
                    int modifiers = field.getModifiers();

                    if (!Modifier.isPublic(modifiers)) {
                        continue;
                    }

                    arr.add(new FieldInfo(field));
                }

                fields = arr.toArray(new FieldInfo[arr.size()]);
                fieldsByJsonName = new HashMap<>();
                fieldsByDbName = new HashMap<>();

                for (FieldInfo field : fields) {
                    fieldsByJsonName.put(field.jsonName, field);
                    fieldsByDbName.put(field.dbName, field);
                }
            } catch (SecurityException e) {
                throw new MapperException("Could not access class '"
                                          + cls.getName() + "'", e);
            }
        }
    }

    private static IdentityHashMap<Class<?>, ClassInfo> classInfo
        = new IdentityHashMap<>();

    private static ClassInfo makeClassInfo(Class<?> cls)
        throws MapperException {
        return new ClassInfo(cls);
    }

    private static synchronized ClassInfo getClassInfo(Class<?> cls)
        throws MapperException {

        ClassInfo info = classInfo.get(cls);
        if (info == null) {
            info = makeClassInfo(cls);
            classInfo.put(cls, info);
        }

        return info;
    }

    private static HashMap<String, SimpleDateFormat> dateFormats
        = new HashMap<>();

    private static synchronized SimpleDateFormat makeDateFormat(String format) {
        SimpleDateFormat fmt;

        fmt = dateFormats.get(format);
        if (fmt == null) {
            fmt = new SimpleDateFormat(format);
            dateFormats.put(format, fmt);
        }

        return fmt;
    }

    /**
     * Reads the object from the JSON representation.
     *
     * @param object the object to read.
     * @param json the JSON representation of the object.
     * @return the argument object.
     * @throws MapperException if the read operation fails.
     */
    public static Object read(Object object, JSONObject json)
        throws MapperException {

        ClassInfo info = getClassInfo(object.getClass());
        for (String name : JSONObject.getNames(json)) {
            FieldInfo fi = info.fieldsByJsonName.get(name);
            if (fi == null) {
                continue;
            }

            try {
                switch (fi.type) {
                case INT:
                    fi.field.setInt(object, json.getInt(name));
                    break;

                case INTEGER:
                    fi.field.set(object, Integer.valueOf(json.getInt(name)));
                    break;

                case CHAR:
                    fi.field.setChar(object, (char) json.getInt(name));
                    break;

                case CHARACTER:
                    fi.field.set(object,
                                 Character.valueOf((char) json.getInt(name)));
                    break;

                case STRING:
                    fi.field.set(object, json.getString(name));
                    break;

                case BOOLEAN:
                    fi.field.set(object, json.getBoolean(name));
                    break;

                case DATE:
                    fi.field.set(object, new Date(json.getLong(name)));
                    break;
                }
            } catch (IllegalAccessException e) {
                throw new MapperException("Failed to set object field "
                                          + fi.field.getName(), e);
            }
        }

        return object;
    }

    /**
     * Reads the object from the current row of the SQL result set.
     *
     * @param object the object to read.
     * @param rs the SQL result set to read from.
     * @return the argument object.
     * @throws MapperException if the read operation fails.
     */
    public static Object read(Object object, ResultSet rs)
        throws MapperException {

        ClassInfo info = getClassInfo(object.getClass());
        ResultSetMetaData meta;
        int count;

        try {
            meta = rs.getMetaData();
            count = meta.getColumnCount();
        } catch (SQLException e) {
            throw new MapperException("SQL error", e);
        }

        for (int i = 1; i <= count; i++) {
            FieldInfo fi = null;

            try {
                fi = info.fieldsByDbName.get(meta.getColumnName(i));
                if (fi == null) {
                    continue;
                }

                switch (fi.type) {
                case INT:
                    fi.field.setInt(object, rs.getInt(i));
                    break;

                case INTEGER:
                    fi.field.set(object, Integer.valueOf(rs.getInt(i)));
                    break;

                case CHAR:
                    fi.field.setChar(object, rs.getString(i).charAt(0));
                    break;

                case CHARACTER:
                    fi.field.set(object,
                                 Character.valueOf(rs.getString(i).charAt(0)));
                    break;

                case STRING:
                    fi.field.set(object, rs.getString(i));
                    break;

                case BOOLEAN:
                    fi.field.set(object, rs.getBoolean(i));
                    break;

                case DATE:
                    fi.field.set(object, new Date(rs.getLong(i)));
                    break;
                }
            } catch (IllegalAccessException e) {
                throw new MapperException("Failed to set object field "
                                          + fi.field.getName(), e);
            } catch (SQLException e) {
                throw new MapperException("Failed to read object field "
                                          + fi.field.getName()
                                          + " from ResultSet",
                                          e);
            }
        }

        return object;
    }

    /**
     * Reads the object from the DOM element.
     *
     * @param object the object to read.
     * @param element the DOM element containing object fields.
     * @return the argument object.
     * @throws MapperException if the read operation fails.
     */
    public static Object read(Object object, Element element)
        throws MapperException {

        ClassInfo info = getClassInfo(object.getClass());
        for (FieldInfo fi : info.fields) {
            try {
                String val;
                if (fi.xmlAttribute) {
                    val = element.getAttribute(fi.xmlName);
                } else {
                    val = getChildContent(element, fi.xmlName);
                }

                switch (fi.type) {
                case INT:
                    fi.field.setInt(object, Integer.parseInt(val));
                    break;

                case INTEGER:
                    fi.field.setInt(object, Integer.valueOf(val));
                    break;

                case CHAR:
                    fi.field.setChar(object, (char) Integer.parseInt(val));
                    break;

                case CHARACTER:
                    fi.field.setChar(
                    	object,
                        Character.valueOf((char) Integer.parseInt(val)));
                    break;

                case STRING:
                    fi.field.set(object, val);
                    break;

                case BOOLEAN:
                    fi.field.set(object, Boolean.valueOf(val));
                    break;

                case DATE:
                    if (fi.dateFormat == null) {
                        fi.field.set(object, new Date(Long.parseLong(val)));
                    } else if (!isEmpty(val)) {
                        fi.field.set(object, fi.dateFormat.parse(val));
                    }
                    break;
                }
            } catch (IllegalAccessException e) {
                throw new MapperException("Failed to set object field "
                                          + fi.field.getName(), e);
            } catch (NumberFormatException e) {
                throw new MapperException("Invalid integer value for field "
                                          + fi.field.getName(), e);
            } catch (ParseException e) {
                throw new MapperException("Invalid date value for field "
                                          + fi.field.getName(), e);
            }
        }

        return object;

    }

    /**
     * Gets the content of the name child element as string.
     *
     * @param parent the parent element
     * @param child the name of the child element.
     * @return the content of the child element as string.
     */
    private static String getChildContent(Element parent, String child) {
        NodeList nl = parent.getElementsByTagName(child);
        int length = nl.getLength();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(nl.item(i).getTextContent());
        }

        return sb.toString();
    }

    /**
     * Converts the object to JSON representation.
     *
     * @param object the object to convert.
     * @return the JSON representation of the object.
     * @throws MapperException if the conversion fails.
     */
    public static JSONObject toJson(Object object) throws MapperException {
        ClassInfo info = getClassInfo(object.getClass());
        JSONObject json = new JSONObject();

        try {
            Object val;

            for (FieldInfo field : info.fields) {
                switch (field.type) {
                case INT:
                    json.put(field.jsonName, field.field.getInt(object));
                    break;

                case CHAR:
                    json.put(field.jsonName, (int) field.field.getChar(object));
                    break;

                case INTEGER:
                case CHARACTER:
                case STRING:
                    val = field.field.get(object);
                    if (val == null) {
                        val = JSONObject.NULL;
                    }
                    json.put(field.jsonName, val);
                    break;

                case BOOLEAN:
                    json.put(field.jsonName, field.field.getBoolean(object));
                    break;

                case DATE:
                    Date date = (Date) field.field.get(object);
                    if (date != null) {
                        json.put(field.jsonName, date.getTime());
                    }
                    break;
                }
            }

            return json;
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to convert object to JSON", e);
        }
    }

    /**
     * Converts the object to SQL insert statement.
     *
     * @param cls the class of the object to convert
     * @return the SQL insert statement of the object.
     * @throws MapperException if the conversion fails.
     */
    public static String toInsertSql(Class<?> cls) throws MapperException {
        ClassInfo info = getClassInfo(cls);
        StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ");
        sb.append(info.dbTableName);
        sb.append(" (");

        int numColumns = 0;

        for (FieldInfo field : info.fields) {
            if (field.isId && field.idAutoAssign) {
                continue;
            }
            if (field.readOnly) {
                continue;
            }
            if (numColumns > 0) {
                sb.append(',');
            }
            sb.append(field.dbName);
            numColumns++;
        }

        sb.append(") VALUES (");
        for (int i = 0; i < numColumns; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        sb.append(")");

        return sb.toString();
    }

    /**
     * Converts the object to SQL update statement.
     *
     * @param cls the class of the object to convert
     * @return the SQL update statement of the object.
     * @throws MapperException if the conversion fails.
     */
    public static String toUpdateSql(Class<?> cls) throws MapperException {
        return toUpdateSql(cls, null);
    }

    /**
     * Converts the object to SQL update statement.
     *
     * @param cls the class of the object to convert
     * @param constraints optional additional constraints for the
     * objects to update.
     * @return the SQL update statement of the object.
     * @throws MapperException if the conversion fails.
     */
    public static String toUpdateSql(Class<?> cls, String constraints)
        throws MapperException {

        ClassInfo info = getClassInfo(cls);
        StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ");
        sb.append(info.dbTableName);
        sb.append(" SET ");

        int numColumns = 0;
        FieldInfo idField = null;

        for (FieldInfo field : info.fields) {
            if (field.isId) {
                idField = field;
                if (field.idAutoAssign) {
                    continue;
                }
            }
            if (field.readOnly) {
                continue;
            }
            if (numColumns > 0) {
                sb.append(',');
            }
            sb.append(field.dbName);
            sb.append("=?");
            numColumns++;
        }

        if (idField == null) {
            throw new MapperException("Can't update object " + cls
                                      + " without an ID field");
        }

        sb.append(" WHERE ");
        sb.append(idField.dbName);
        sb.append("=?");

        if (!isEmpty(constraints)) {
            sb.append(" AND ");
            sb.append(constraints);
        }

        return sb.toString();
    }

    /**
     * Converts the object to SQL delete statement.
     *
     * @param cls the class of the object to convert.
     * @param the SQL update statement of the object.
     * @throws MapperException if the conversion fails.
     */
    public static String toDeleteSql(Class<?> cls) throws MapperException {
        ClassInfo info = getClassInfo(cls);
        StringBuilder sb = new StringBuilder();

        sb.append("DELETE FROM ");
        sb.append(info.dbTableName);

        FieldInfo idField = null;

        for (FieldInfo field : info.fields) {
            if (field.isId) {
                idField = field;
                break;
            }
        }

        if (idField == null) {
            throw new MapperException("Can't delete object " + cls
                                      + " without an ID field");
        }

        sb.append(" WHERE ");
        sb.append(idField.dbName);
        sb.append("=?");

        return sb.toString();
    }

    /**
     * Converts the object to SQL insert and update statement
     * parameters.
     *
     * @param object the object to convert
     * @return the SQL update statement parameters.
     * @throws MapperException if the conversion fails.
     */
    public static Object[] toSqlParams(Object object) throws MapperException {
        return toSqlParams(object, false);
    }

    /**
     * Converts the object to SQL insert and update statement
     * parameters.
     *
     * @param object the object to convert
     * @param appendId specifies if the object ID field is appended to
     * the paramters array.
     * @return the SQL update statement parameters.
     * @throws MapperException if the conversion fails.
     */
    public static Object[] toSqlParams(Object object, boolean appendId)
        throws MapperException {
        return toSqlParams(object, false, null);
    }

    /**
     * Converts the object to SQL insert and update statement
     * parameters.
     *
     * @param object the object to convert
     * @param appendId specifies if the object ID field is appended to
     * the paramters array.
     * @param tailParams optional extra params to be appended to the
     * parameter list.
     * @return the SQL update statement parameters.
     * @throws MapperException if the conversion fails.
     */
    public static Object[] toSqlParams(Object object, boolean appendId,
                                       Object[] tailParams)
        throws MapperException {

        ClassInfo info = getClassInfo(object.getClass());
        ArrayList<Object> params = new ArrayList<>();

        try {
            FieldInfo idField = null;
            for (FieldInfo field : info.fields) {
                if (field.isId) {
                    idField = field;
                    if (field.idAutoAssign) {
                        continue;
                    }
                }
                if (field.readOnly) {
                    continue;
                }
                params.add(field.field.get(object));
            }
            if (appendId) {
                if (idField == null) {
                    throw new MapperException("No ID field found for object "
                                              + object.getClass());
                }
                params.add(idField.field.get(object));
            }

            if (tailParams != null) {
                for (Object p : tailParams) {
                    params.add(p);
                }
            }

            return params.toArray(new Object[params.size()]);
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to convert object to SQL values",
                                      e);
        }
    }

    public static Object[] toIdParams(Object object)
        throws MapperException {
        ClassInfo info = getClassInfo(object.getClass());
        ArrayList<Object> params = new ArrayList<>();

        try {
            FieldInfo idField = null;

            for (FieldInfo field : info.fields) {
                if (field.isId) {
                    idField = field;
                    break;
                }
            }

            if (idField == null) {
                throw new MapperException("No ID field found for object "
                                          + object.getClass());
            }
            params.add(idField.field.get(object));

            return params.toArray(new Object[params.size()]);
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to convert object to SQL values",
                                      e);
        }
    }

    public static void toCsvHeader(Class<?> cls, CSVBuilder cb)
        throws MapperException {
        ClassInfo info = getClassInfo(cls);

        for (FieldInfo field : info.fields) {
            cb.append(field.xmlName);
        }
    }

    /**
     * Converts the object into CSV and appends the columns to the CSV
     * builder instance.
     *
     * @param object the object to convert.
     * @param cb the <tt>CSVBuilder</tt> where values are appended.
     * @throws MapperException if the conversion fails.
     */
    public static void toCsv(Object object, CSVBuilder cb)
        throws MapperException {
        ClassInfo info = getClassInfo(object.getClass());

        try {
            Object val;

            for (FieldInfo field : info.fields) {
                switch (field.type) {
                case INT:
                    cb.append(field.field.getInt(object));
                    break;

                case CHAR:
                    cb.append((int) field.field.getChar(object));
                    break;

                case INTEGER:
                case CHARACTER:
                case STRING:
                    val = field.field.get(object);
                    if (val == null) {
                        cb.append();
                    } else {
                        cb.append(val.toString());
                    }
                    break;

                case BOOLEAN:
                    cb.append(field.field.getBoolean(object));
                    break;

                case DATE:
                    Date date = (Date) field.field.get(object);
                    if (date == null) {
                        cb.append();
                    } else if (field.dateFormat == null) {
                        cb.append(date.getTime());
                    } else {
                        cb.append(field.dateFormat.format(date));
                    }
                    break;
                }
            }
        } catch (IllegalAccessException e) {
            throw new MapperException("Failed to convert object to CSV", e);
        }
    }
}
