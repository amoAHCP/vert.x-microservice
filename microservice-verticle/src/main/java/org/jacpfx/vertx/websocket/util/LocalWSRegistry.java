package org.jacpfx.vertx.websocket.util;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.jacpfx.common.WSEndpoint;
import org.jacpfx.common.WSEndpointHolder;
import org.jacpfx.common.handler.WSLocalHandler;
import org.jacpfx.common.handler.WebSocketHandler;

import java.util.function.Consumer;

/**
 * Created by Andy Moncsek on 15.11.15.
 */
public class LocalWSRegistry implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WSLocalHandler.class);



    private final Vertx vertx;

    public LocalWSRegistry(Vertx vertx) {
        this.vertx = vertx;
    }


    @Override
    public void findRouteToWSServiceAndRegister(ServerWebSocket serverSocket) {




    }

    public void registerAndExecute(ServerWebSocket serverSocket, Consumer<WSEndpoint> onFinishRegistration){
        final SharedData sharedData = this.vertx.sharedData();
        final LocalMap<String, byte[]> wsRegistry = sharedData.getLocalMap(WS_REGISTRY);
        final WSEndpointHolder holder = getWSEndpointHolderFromSharedData(wsRegistry);
        final String path = serverSocket.path();
        final WSEndpoint endpoint = new WSEndpoint(serverSocket.binaryHandlerID(), serverSocket.textHandlerID(), path);

        replaceOrAddEndpoint(wsRegistry, holder, endpoint);
        onFinishRegistration.accept(endpoint);
    }


    private void replaceOrAddEndpoint(LocalMap<String, byte[]> wsRegistry, WSEndpointHolder holder, WSEndpoint endpoint) {
        if (holder != null) {
            holder.add(endpoint);
            wsRegistry.replace(WS_ENDPOINT_HOLDER, serialize(holder));

        } else {
            final WSEndpointHolder holderTemp = new WSEndpointHolder();
            holderTemp.add(endpoint);
            wsRegistry.put(WS_ENDPOINT_HOLDER, serialize(holderTemp));
        }
    }


    private WSEndpointHolder getWSEndpointHolderFromSharedData(final LocalMap<String, byte[]> wsRegistry) {
        final byte[] holderPayload = wsRegistry.get(WS_ENDPOINT_HOLDER);
        if (holderPayload != null) {
            return (WSEndpointHolder) deserialize(holderPayload);
        }

        return null;
    }

    @Override
    public void findRouteSocketInRegistryAndRemove(ServerWebSocket serverSocket) {

    }

    @Override
    public void replyToWSCaller(Message<byte[]> message) {

    }

    @Override
    public void replyToAllWS(Message<byte[]> message) {

    }



}
