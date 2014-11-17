package org.jacpfx.common;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by amo on 05.11.14.
 */
public class JSONTool {

    public static JsonObject createOperationObject(String url, String type, String[] mime, String... param) {
        final JsonObject result = new JsonObject().putString("url", url).putString("type", type);
        if(mime!=null) {
            final JsonArray types = new JsonArray();
            Stream.of(mime).map(m -> new JsonObject().putString("mime", m)).forEach(jso -> types.addObject(jso));
            result.putArray("mime", types);
        }

        if(param!=null) {
            final JsonArray params = new JsonArray();
            Stream.of(param).map(m -> new JsonObject().putString("param", m)).forEach(jso -> params.addObject(jso));
            result.putArray("param", params);
        }

        return result;


    }

    public static List<JsonObject> getObjectListFromArray(final JsonArray jsonarray) {
        List<JsonObject> l = new ArrayList<>();
        if(jsonarray==null) return l;
        for(int i=0; i<jsonarray.size();i++) {
            l.add(jsonarray.get(i));
        }
        return l;
    }
}
