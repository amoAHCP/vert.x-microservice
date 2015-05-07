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

    public static JsonObject createOperationObject(String name, String description,String url, String type, String[] produces,String[] consumes, String... param) {
        final JsonObject result = new JsonObject().put("name",name).put("description",description).put("url", url).put("type", type);
        if(produces!=null) {
            final JsonArray types = new JsonArray();
            Stream.of(produces).map(m -> new JsonObject().put("produces", m)).forEach(jso -> types.add(jso));
            result.put("produces", types);
        }
        if(consumes!=null) {
            final JsonArray types = new JsonArray();
            Stream.of(consumes).map(m -> new JsonObject().put("consumes", m)).forEach(jso -> types.add(jso));
            result.put("consumes", types);
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
