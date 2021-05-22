/*-
 * Copyright (C) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This file was distributed by Oracle as part of a version of Oracle NoSQL
 * Database made available at:
 *
 * http://www.oracle.com/technetwork/database/database-technologies/nosqldb/downloads/index.html
 *
 * Please see the LICENSE file included in the top-level directory of the
 * appropriate version of Oracle NoSQL Database for a copy of the license and
 * additional information.
 */

package oracle.kv.impl.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.text.DateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Provides utilities to aid in interaction with Jackson JSON processing
 * libraries as well as helpful JSON operations.
 */
@SuppressWarnings("deprecation")
public class JsonUtils {

    protected static final ObjectMapper mapper;

    /*
     * Symbols used to converting hex string
     */
    private static final String HEX = "0123456789ABCDEF";

    /* A formatter for printing human readable time.*/
    private static final DateFormat timeFormatter =
        FormatUtils.getDateTimeAndTimeZoneFormatter();

    static {
        /*
         * Enable some non-default features:
         *  - allow leading 0 in numerics
         *  - allow non-numeric values such as INF, -INF, NaN for float and
         *    double
         *
         * Disables:
         *  - The "NaN" ("not a number", that is, not real number) float/double
         *    values are output as quoted strings.
         */
        JsonFactory jf = new JsonFactory();
        jf.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        jf.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        jf.configure(JsonGenerator.Feature.QUOTE_NON_NUMERIC_NUMBERS, false);
        mapper = new ObjectMapper(jf);
    }

    protected static final Gson gson = new GsonBuilder().create();

    /**
     * Returns a JSON parser that parses input from a reader.
     *
     * @param in the reader
     * @return the parser
     * @throws IOException if there is a problem parsing the input
     */
    public static JsonParser createJsonParser(Reader in)
        throws IOException {

        return mapper.getFactory().createParser(in);
    }

    /**
     * Returns a JSON parser that parses input from an input stream.
     *
     * @param in the input stream
     * @return the parser
     * @throws IOException if there is a problem parsing the input
     */
    public static JsonParser createJsonParser(InputStream in)
        throws IOException {

        return mapper.getFactory().createParser(in);
    }

    /**
     * Returns an object node.
     *
     * @return the object node
     */
    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    /**
     * Returns an array node.
     *
     * @return the array node
     */
    public static ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }

    /**
     * Returns the default object writer.
     *
     * @param pretty whether the writer should do prettyprinting
     * @return the object writer
     */
    public static ObjectWriter createWriter(boolean pretty) {

        /*
         * NOTE: currently using jackson 1.9, which deprecates the method
         * defaultPrettyPrintingWriter() and replaces it with the new method
         * writerWithDefaultPrettyPrinter(). Unfortunately, the latest
         * versions of CDH are distributed with version 1.8 of jackson;
         * which specifies only defaultPrettyPrintingWriter(). Thus,
         * although it is preferable to use writerWithDefaultPrettyPrinter(),
         * any Table API based MapReduce jobs that are run will fail because
         * the Hadoop infrastructure has no knowledge of that method.
         *
         * As a result, until CDH upgrades to jackson 1.9 or greater,
         * this method must continue to employ defaultPrettyPrintingWriter()
         * below; after which it may be changed to use
         * writerWithDefaultPrettyPrinter() instead.
         */
        return (pretty ? mapper.writerWithDefaultPrettyPrinter() :
                mapper.writer());
    }

    /**
     * Returns the value of a field in text format if the node is an object
     * node with the specified value field, otherwise null.
     *
     * @param node the node
     * @param field the field name
     * @return the field value as text or null
     */
    public static String getAsText(JsonNode node, String field) {
        return getAsText(node, field, null);
    }

    /**
     * Returns the value of a field in text format if the node is an object
     * node with the specified value field, otherwise returns the default
     * value.
     *
     * @param node the node
     * @param field the field name
     * @param defaultValue the default value
     * @return the field value as text or the default
     */
    public static String getAsText(JsonNode node, String field,
                                   String defaultValue) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isValueNode()) {
            return defaultValue;
        }
        return fieldNode.asText();
    }

    /**
     * Returns the Long value of a field if the node is an object node with the
     * specified long field, otherwise null.
     *
     * @param node the node
     * @param field the field name
     * @return the field value or null
     */
    public static Long getLong(JsonNode node, String field) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isLong()) {
            return null;
        }
        return fieldNode.asLong();
    }

    /**
     * Returns the Integer value of a field if the node is an object node with
     * the specified int field, otherwise null.
     *
     * @param node the node
     * @param field the field name
     * @return the field value or null
     */
    public static Integer getInt(JsonNode node, String field) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isInt()) {
            return null;
        }
        return Integer.valueOf(fieldNode.asInt());
    }

    /**
     * Returns the Double value of a field if the node is an object node with
     * the specified double field, otherwise null.
     *
     * @param node the node
     * @param field the field name
     * @return the field value or null
     */
    public static Double getDouble(JsonNode node, String field) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isDouble()) {
            return null;
        }
        return Double.valueOf(fieldNode.asDouble());
    }

    /**
     * Returns the Boolean value of a field if the node is an object node with
     * the specified boolean field, otherwise null.
     *
     * @param node the node
     * @param field the field name
     * @return the field value or null
     */
    public static Boolean getBoolean(JsonNode node, String field) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isBoolean()) {
            return null;
        }
        return Boolean.valueOf(fieldNode.asBoolean());
    }

    /**
     * Returns the boolean value of a field if the node is an object node with
     * the specified boolean field, otherwise returns the default value.
     *
     * @param node the node
     * @param field the field name
     * @param defaultValue the default
     * @return the field value or the default
     */
    public static boolean getBoolean(JsonNode node, String field,
                                     boolean defaultValue) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isBoolean()) {
            return defaultValue;
        }
        return fieldNode.asBoolean();
    }

    /**
     * Returns the object node for a field if the node is an object node with
     * the specified object field, otherwise null.
     *
     * @param node the node
     * @param field the field name
     * @return the field value or null
     */
    public static ObjectNode getObject(JsonNode node, String field) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isObject()) {
            return null;
        }
        return (ObjectNode) fieldNode;
    }

    /**
     * Returns an iterable object over the elements of an array for a field if
     * the node is an object node with the specified array field, otherwise
     * an empty iterable.
     *
     * @param node the node
     * @param field the field name
     * @return an iterable over the array elements or an empty iterable
     */
    public static Iterable<JsonNode> getArray(JsonNode node, String field) {
        final JsonNode fieldNode = node.get(field);
        if ((fieldNode == null) || !fieldNode.isArray()) {
            return Collections.emptyList();
        }
        return fieldNode;
    }

    /**
     * Compare the contents of two JSON strings without regard
     * to the order in which fields appear in the string.
     *
     * @param a , b Two JSON strings to compare
     * @return true if the two strings represent equivalent JSON documents
     */
    public static boolean jsonStringsEqual(String a, String b) {
        final Map<String, Object> mapa = getMapFromJsonStr(a);
        final Map<String, Object> mapb = getMapFromJsonStr(b);
        if (mapa == null) {
            return false;
        }
        return mapa.equals(mapb);
    }

    /**
     * Turn a JSON string into a Map.
     *
     * @param s the JSON string
     * @return A Map representing the JSON document.
     */
    public static Map<String, Object> getMapFromJsonStr(final String s) {
        return getMapFromJsonStr(s, null);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMapFromJsonStr(final String s,
                                                        final Logger logger) {
        try {
            return mapper.readValue(s, HashMap.class);
        } catch (IOException e) {
            if (logger != null) {
                final String errStr =
                    "JsonUtils.getMapFromJsonStr: WARNING - " + e;
                logger.log(Level.WARNING, errStr, e);
            }
            return null;
        }
    }

    /**
     * Return the static obecjt mapper.
     */
    public static ObjectMapper getObjectMapper() {
        return mapper;
    }

    /**
     * Convert a byte array to hex string.
     */
    public static String convertBytesToHex(byte[] byteArray) {

        final char[] hexValue = new char[byteArray.length * 2];

        final char[] hexSymbols = HEX.toCharArray();

        for (int i = 0; i < byteArray.length; i++) {
            final int current = byteArray[i] & 0xff;
            /* determine the Hex symbol for the last 4 bits */
            hexValue[i * 2 + 1] = hexSymbols[current & 0x0f];
            /* determine the Hex symbol for the first 4 bits */
            hexValue[i * 2] = hexSymbols[current >> 4];
        }
        return new String(hexValue);
    }

    /**
     * Convert a hex string to byte array.
     */
    public static byte[] convertHexToBytes(String hexString) {

        if (hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string length");
        }

        final byte[] result = new byte[hexString.length() / 2];

        final int n = hexString.length();

        for (int i = 0; i < n; i += 2) {
            /* high bits */
            final int hb = HEX.indexOf(hexString.charAt(i));
            /* low bits */
            final int lb = HEX.indexOf(hexString.charAt(i + 1));
            result[i / 2] = (byte) ((hb << 4) | lb);
        }
        return result;
    }

    /**
     * Returns the default gson singleton.
     */
    public static Gson getGson() {
        return gson;
    }

    /**
     * Converts a json to a string.
     *
     * The string is supposed to be put as a line in a log. To make parsing
     * easier, we add the "JL" marker at the start of the string and structure
     * it in the following format:
     * <pre>
     * JL|<nameField>|<timeField>|<jsonField>
     * </pre>
     * The timeField is in the form:
     * <pre>
     * (<startTime> -&gt; <endTime>)/(<startTimestamp> -&gt; <endTimestamp>)
     * </pre>
     * where <startTime> and <endTime> are human readable time strings and
     * start and end timestamps the epoch time stamps.
     */
    public static String toJsonLine(String name,
                                    long startTime,
                                    long endTime,
                                    JsonElement element) {
        /*
         * Synchronized on timeFormatter as DateFormat is not thread safe. The
         * synchronization should not have a big performance impact as we do not
         * expect to print json line very often.
         */
        synchronized(timeFormatter) {
            return String.format(
                "JL|%s|(%s -> %s)/(%s -> %s)|%s",
                name,
                timeFormatter.format(startTime),
                timeFormatter.format(endTime),
                startTime,
                endTime,
                element);
        }
    }

    /**
     * Returns a json object collector. Use this as the argument to
     * Stream.collect to convert a stream of map entries with strings and
     * JsonElements into a JsonObject with those elements as members.
     */
    public static <T extends Map.Entry<String, JsonElement>>
        Collector<T, JsonObject, JsonObject> getObjectCollector() {

        return Collector.of(
            () -> new JsonObject(),
            (o, e) -> o.add(e.getKey(), e.getValue()),
            (o1, o2) -> {
                o2.entrySet()
                    .forEach(e -> o1.add(e.getKey(), e.getValue()));
                return o1;
            });
    }

    /**
     * Returns a json array collector. Use this as the argument to
     * Stream.collect to convert a stream of JsonElements into a JsonArray.
     */
    public static Collector<JsonElement, JsonArray, JsonArray>
        getArrayCollector() {

        return Collector.of(
            () -> new JsonArray(),
            (a, e) -> a.add(e),
            (a1, a2) -> {
                a1.addAll(a2);
                return a1;
            });
    }

    /**
     * Recursively find an element of the specified name in the json object.
     */
    public static JsonElement find(JsonObject object, String name) {
        JsonElement result = object.get(name);
        if (result != null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final JsonElement e = entry.getValue();
            result = find(e, name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static JsonElement find(JsonElement e, String name) {
        if (e instanceof JsonPrimitive) {
            return null;
        }
        if (e instanceof JsonObject) {
            return find((JsonObject) e, name);
        }
        return find((JsonArray) e, name);
    }

    /**
     * Recursively find an element of the specified name in the json array.
     */
    public static JsonElement find(JsonArray array, String name) {
        JsonElement result;
        final Iterator<JsonElement> iter = array.iterator();
        while (iter.hasNext()) {
            final JsonElement e = iter.next();
            result = find(e, name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

}
