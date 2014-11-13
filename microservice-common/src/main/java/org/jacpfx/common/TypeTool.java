package org.jacpfx.common;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.io.*;
import java.nio.Buffer;

/**
 * Created by amo on 04.11.14.
 */
public class TypeTool {

    /**
     * Checks if return type is a Vert.x event bus compatible type
     *
     * @param o
     * @return
     */
    public static boolean isCompatibleReturnType(final Object o) {
        if (o instanceof JsonObject
                || o instanceof Byte
                || o instanceof Character
                || o instanceof Double
                || o instanceof Float
                || o instanceof Integer
                || o instanceof JsonArray
                || o instanceof Long
                || o instanceof Short
                || o instanceof String
                || o instanceof byte[]
                || o instanceof Buffer
                || o instanceof Boolean) {
            return true;
        }

        return false;
    }

    /**
     * Checks if return type is a Vert.x event bus compatible type
     *
     * @param o
     * @return
     */
    public static boolean isCompatibleRESTReturnType(final Object o) {
        if (o instanceof JsonObject
                || o instanceof JsonArray
                || o instanceof String) {
            return true;
        }

        return false;
    }

    public static String trySerializeToString(Object o) {
        if (o instanceof JsonObject) {
           return JsonObject.class.cast(o).encodePrettily();
        } else if(o instanceof JsonArray) {
            return JsonArray.class.cast(o).encodePrettily();
        } else if(o instanceof Character
                || o instanceof Double
                || o instanceof Float
                || o instanceof Integer
                || o instanceof Long
                || o instanceof Short
                || o instanceof String
                || o instanceof Boolean) {
              return o.toString();
        }


        return null;
    }

    public static boolean isBuffer (Object o) {
        if (o instanceof Buffer) return true;
        return false;

    }

    public static boolean isByteArray (Object o) {
        if (o instanceof byte[]) return true;
        return false;

    }

    public static byte[] serialize(Object obj) throws IOException {
        final ByteArrayOutputStream b = new ByteArrayOutputStream();
        final ObjectOutputStream o = new ObjectOutputStream(b);
        o.writeObject(obj);
        return b.toByteArray();
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        final ByteArrayInputStream b = new ByteArrayInputStream(bytes);
        final ObjectInputStream o = new ObjectInputStream(b);
        return o.readObject();
    }

    public static <T> T typedDeserialize(byte[] bytes, Class<T> clazz) throws IOException, ClassNotFoundException {
        final ByteArrayInputStream b = new ByteArrayInputStream(bytes);
        final ObjectInputStream o = new ObjectInputStream(b);
        return clazz.cast(o.readObject());
    }
}
