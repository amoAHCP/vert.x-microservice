package org.jacpfx.vertx.util;


import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by amo on 10.11.14.
 */
public class WSRouteMatcher {

    private Map<String,Handler<?>> routes = new HashMap<>();
    private Map<ServerWebSocket,String> sessions = new HashMap<>();


    public void addRoute(final String route, final Handler<?> handler) {
           if(!routes.containsKey(route)) routes.put(route,handler);
    }

    public void removeRoute(final String route) {
        if(routes.containsKey(route)) routes.remove(route);

       List<ServerWebSocket> sessionsToRemove =  sessions.entrySet().stream().filter(entry->entry.getValue().equals(route)).map(e->e.getKey()).collect(Collectors.toList());

        sessionsToRemove.forEach(ws->{sessions.remove(ws);ws.close();});
    }


    public void accept(ServerWebSocket ws){
        final String path = ws.path();
        if(routes.containsKey(path)) {
               sessions.put(ws,path);
               Handler handler = routes.get(path);
               ws.handler(handler);
        }else {
            //TODO error handling
        }
    }

}
