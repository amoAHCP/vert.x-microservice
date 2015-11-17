package org.jacpfx.vertx.websocket.util;

import io.vertx.core.http.ServerWebSocket;
import org.jacpfx.common.WSEndpoint;

import java.util.function.Consumer;

/**
 * Created by Andy Moncsek on 17.11.15.
 */
public interface WSRegistry {
    void removeAndExecuteOnClose(ServerWebSocket serverSocket, Runnable onFinishRemove);

    void findEndpointsAndExecute(WSEndpoint currentEndpoint, Consumer<WSEndpoint> onFinishRegistration);

    void registerAndExecute(ServerWebSocket serverSocket, Consumer<WSEndpoint> onFinishRegistration);
}
