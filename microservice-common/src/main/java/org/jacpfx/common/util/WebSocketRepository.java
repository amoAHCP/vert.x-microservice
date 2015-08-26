package org.jacpfx.common.util;

import io.vertx.core.http.ServerWebSocket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by amo on 02.12.14.
 */
public class WebSocketRepository {
    private List<ServerWebSocket> webSockets = new CopyOnWriteArrayList<>();

    public void addWebSocket(ServerWebSocket webSocket) {
        webSockets.add(webSocket);
    }

    public List<ServerWebSocket> getWebSockets() {
        return webSockets;
    }

    public void removeWebSocket(ServerWebSocket webSocket) {
        webSockets.remove(webSocket);
    }
}
