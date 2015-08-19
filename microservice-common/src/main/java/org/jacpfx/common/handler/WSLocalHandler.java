package org.jacpfx.common.handler;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.jacpfx.common.*;
import org.jacpfx.common.constants.GlobalKeyHolder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Created by Andy Moncsek on 11.02.15.
 * The WSLocalhandler registers WebSockets in registry and defines handlers for each incoming socket connection. This handler works only in one Vertx instance. If you need failover, please use the WSClusterHandler.
 */
public class WSLocalHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WSLocalHandler.class);



    private final Vertx vertx;

    public WSLocalHandler(Vertx vertx) {
        this.vertx = vertx;
    }


    @Override
    public void findRouteToWSServiceAndRegister(ServerWebSocket serverSocket) {


        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(REGISTRY, onSuccess(resultMap ->
                        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(resultHolder -> findServiceEntryAndRegisterWS(serverSocket, resultHolder)))
        ));
    }

    @Override
    public void findRouteSocketInRegistryAndRemove(ServerWebSocket serverSocket) {
        final SharedData sharedData = this.vertx.sharedData();
        final String binaryHandlerID = serverSocket.binaryHandlerID();
        final String textHandlerID = serverSocket.textHandlerID();
        final LocalMap<String, byte[]> wsRegistry = sharedData.getLocalMap(WS_REGISTRY);
        final WSEndpointHolder holder = getWSEndpointHolderFromSharedData(wsRegistry);
        if (holder != null) {
            final List<WSEndpoint> all = holder.getAll();
            final Optional<WSEndpoint> first = all.parallelStream().filter(e -> e.getBinaryHandlerId().equals(binaryHandlerID) && e.getTextHandlerId().equals(textHandlerID)).findFirst();
            first.ifPresent(endpoint -> {
                holder.remove(endpoint);
                wsRegistry.replace(WS_ENDPOINT_HOLDER, serialize(holder));
                log("OK REMOVE: " + serverSocket.binaryHandlerID());
            });
        }
    }



    @Override
    public void replyToWSCaller(Message<byte[]> message) {
        try {
            log("REDIRECT: " + this);
            final WSMessageWrapper wrapper = (WSMessageWrapper) Serializer.deserialize(message.body());
            final String stringResult = TypeTool.trySerializeToString(wrapper.getBody());
            replyToEndpoint(stringResult, Serializer.serialize(wrapper.getBody()), wrapper.getEndpoint());

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void replyToAllWS(Message<byte[]> message) {
        try {
            log("Reply to all: " + this);
            final WSMessageWrapper wrapper = (WSMessageWrapper) Serializer.deserialize(message.body());
            final String stringResult = TypeTool.trySerializeToString(wrapper.getBody());
            final byte[] payload = stringResult != null ? stringResult.getBytes() : Serializer.serialize(wrapper.getBody());

            final SharedData sharedData = this.vertx.sharedData();
            final LocalMap<String, byte[]> wsRegistry = sharedData.getLocalMap(WS_REGISTRY);
            final byte[] holderPayload = wsRegistry.get(WS_ENDPOINT_HOLDER);
            if (holderPayload != null) {
                final WSEndpointHolder holder = (WSEndpointHolder) deserialize(holderPayload);
                final List<WSEndpoint> all = holder.getAll();
                all.parallelStream().
                        filter(endP -> endP.getUrl().equals(wrapper.getEndpoint().getUrl())).
                        forEach(
                                endpoint -> replyToEndpoint(stringResult, payload, endpoint)
                        );
            }


        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void replyToEndpoint(String stringResult, byte[] payload, WSEndpoint endpoint) {
        if (stringResult != null) {
            vertx.eventBus().send(endpoint.getTextHandlerId(), stringResult);
        } else {
            vertx.eventBus().send(endpoint.getBinaryHandlerId(), payload);
        }
    }


    private void findServiceEntryAndRegisterWS(final ServerWebSocket serverSocket, final ServiceInfoHolder resultHolder) {
        if (resultHolder != null) {
            final String path = serverSocket.path();
            log("find entry : " + path);
            final Optional<Operation> operationResult = findServiceInfoEntry(resultHolder, path);
            operationResult.ifPresent(op ->
                            createEndpointDefinitionAndRegister(serverSocket)
            );
        }
    }

    private Optional<Operation> findServiceInfoEntry(ServiceInfoHolder resultHolder, String path) {
        return resultHolder.
                getAll().
                parallelStream().
                map(info -> Arrays.asList(info.getOperations())).
                flatMap(Collection::stream).
                filter(op -> op.getUrl().equalsIgnoreCase(path)).
                findFirst();
    }

    private void createEndpointDefinitionAndRegister(ServerWebSocket serverSocket) {
        final SharedData sharedData = this.vertx.sharedData();
        final LocalMap<String, byte[]> wsRegistry = sharedData.getLocalMap(WS_REGISTRY);
        final WSEndpointHolder holder = getWSEndpointHolderFromSharedData(wsRegistry);
        final String path = serverSocket.path();
        final WSEndpoint endpoint = new WSEndpoint(serverSocket.binaryHandlerID(), serverSocket.textHandlerID(), path);

        replaceOrAddEndpoint(wsRegistry, holder, endpoint);

        sendToWSService(serverSocket, path, endpoint);


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



    private void sendToWSService(final ServerWebSocket serverSocket, final String path, final WSEndpoint endpoint) {
        final EventBus eventBus = vertx.eventBus();
        serverSocket.handler(handler -> {
                    try {
                        log("send WS:+ " + endpoint.getUrl());
                        eventBus.send(path, Serializer.serialize(new WSDataWrapper(endpoint, handler.getBytes())), new DeliveryOptions().setSendTimeout(GlobalKeyHolder.DEFAULT_SERVICE_TIMEOUT));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


        );
        serverSocket.resume();
        //TODO set close handler!!
    }



    private void log(final String value) {
        System.out.println(value);
    }
}
