package org.jacpfx.common;


import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by amo on 05.11.14.
 */
public class JSONTool {

    public static JsonObject createOperationObject(String url, String type, String[] mime, String... param) {
        final JsonObject result = new JsonObject().put("url", url).put("type", type);
        if(mime!=null) {
            final JsonArray types = new JsonArray();
            Stream.of(mime).map(m -> new JsonObject().put("mime", m)).forEach(jso -> types.add(jso));
            result.put("mime", types);
        }

        if(param!=null) {
            final JsonArray params = new JsonArray();
            Stream.of(param).map(m -> new JsonObject().put("param", m)).forEach(jso -> params.add(jso));
            result.put("param", params);
        }

        return result;


    }

    public static List<JsonObject> getObjectListFromArray(final JsonArray jsonarray) {
        List<JsonObject> l = new ArrayList<>();
        if(jsonarray==null) return l;
        for(int i=0; i<jsonarray.size();i++) {
            l.add(jsonarray.getJsonObject(i));
        }
        return l;
    }
}
