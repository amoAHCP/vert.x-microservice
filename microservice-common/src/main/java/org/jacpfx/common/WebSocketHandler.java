package org.jacpfx.common;

import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;

/**
 * Created by amo on 02.12.14.
 */
@FunctionalInterface
public interface WebSocketHandler<E> {

    void handle(ServerWebSocket ws, Handler<E> handler);
}
