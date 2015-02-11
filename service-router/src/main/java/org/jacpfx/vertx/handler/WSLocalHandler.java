package org.jacpfx.vertx.handler;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.jacpfx.common.*;
import org.jacpfx.vertx.entrypoint.ServiceEntryPoint;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Created by Andy Moncsek on 11.02.15.
 * The WSLocalhandler registers WebSockets in registry and defines handlers for each incoming socket connection. This handler works only in one Vertx instance. If you need failover, please use the WSClusterHandler.
 */
public class WSLocalHandler {

    private static final Logger log = LoggerFactory.getLogger(WSLocalHandler.class);

    private final Vertx vertx;

    public WSLocalHandler(Vertx vertx) {
        this.vertx = vertx;
    }


    public void findRouteToWSServiceAndRegister(ServerWebSocket serverSocket) {


        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap("registry", onSuccess(resultMap ->
                        resultMap.get("serviceHolder", onSuccess(resultHolder -> findServiceEntryAndRegisterWS(serverSocket, resultHolder)))
        ));
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
                stream().
                map(info -> Arrays.asList(info.getOperations())).
                flatMap(infos -> infos.stream()).
                filter(op -> op.getUrl().equalsIgnoreCase(path)).
                findFirst();
    }

    private void createEndpointDefinitionAndRegister(ServerWebSocket serverSocket) {
        final SharedData sharedData = this.vertx.sharedData();

        sharedData.getLockWithTimeout("wsLock", 90000L, onSuccess(lock -> {

            final LocalMap<String, byte[]> wsRegistry = sharedData.getLocalMap("wsRegistry");
            final WSEndpointHolder holder = getWSEndpointHolderFromSharedData(wsRegistry);
            final String path = serverSocket.path();
            final WSEndpoint endpoint = new WSEndpoint(serverSocket.binaryHandlerID(), serverSocket.textHandlerID(), path);

            if (holder != null) {
                holder.add(endpoint);
                wsRegistry.replace("wsEndpointHolder", serialize(holder));

            } else {
                final WSEndpointHolder holderTemp = new WSEndpointHolder();
                holderTemp.add(endpoint);
                wsRegistry.put("wsEndpointHolder", serialize(holderTemp));
            }

            sendToWSService(serverSocket, path, endpoint);
            lock.release();
        }));


    }

    private WSEndpointHolder getWSEndpointHolderFromSharedData(final LocalMap<String, byte[]> wsRegistry) {
        final byte[] holderPayload = wsRegistry.get("wsEndpointHolder");
        if (holderPayload != null) {
            return (WSEndpointHolder) deserialize(holderPayload);
        }

        return null;
    }

    private byte[] serialize(Object payload) {
        try {
            return Serializer.serialize(payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private Object deserialize(byte[] payload) {
        try {
            return Serializer.deserialize(payload);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void sendToWSService(final ServerWebSocket serverSocket, final String path, final WSEndpoint endpoint) {
        final EventBus eventBus = vertx.eventBus();
        serverSocket.handler(handler -> {
                    try {
                        log("send WS:+ " + endpoint.getUrl());
                        eventBus.send(path, Serializer.serialize(new WSDataWrapper(endpoint, handler.getBytes())), new DeliveryOptions().setSendTimeout(ServiceEntryPoint.DEFAULT_SERVICE_TIMEOUT));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


        );
        serverSocket.resume();
        //TODO set close handler!!
    }


    public void findRouteSocketInRegistryAndRemove(ServerWebSocket serverSocket) {
        final SharedData sharedData = this.vertx.sharedData();
        final String binaryHandlerID = serverSocket.binaryHandlerID();
        final String textHandlerID = serverSocket.textHandlerID();
        sharedData.getLockWithTimeout("wsLock", 90000L, onSuccess(lock -> {
            final LocalMap<String, byte[]> wsRegistry = sharedData.getLocalMap("wsRegistry");
            final WSEndpointHolder holder = getWSEndpointHolderFromSharedData(wsRegistry);
            if (holder != null) {
                final List<WSEndpoint> all = holder.getAll();
                final Optional<WSEndpoint> first = all.stream().filter(e -> e.getBinaryHandlerId().equals(binaryHandlerID) && e.getTextHandlerId().equals(textHandlerID)).findFirst();
                if (first.isPresent()) {
                    first.ifPresent(endpoint -> {
                        holder.remove(endpoint);
                        wsRegistry.replace("wsEndpointHolder", serialize(holder));
                        log("OK REMOVE: " + serverSocket.binaryHandlerID());
                    });
                }
            }
            lock.release();
        }));
    }


    public void replyToWSCaller(Message<byte[]> message) {
        try {
            log("REDIRECT: " + this);
            final WSMessageWrapper wrapper = (WSMessageWrapper) Serializer.deserialize(message.body());
            final String stringResult = TypeTool.trySerializeToString(wrapper.getBody());
            if (stringResult != null) {
                vertx.eventBus().send(wrapper.getEndpoint().getTextHandlerId(), stringResult);
            } else {
                vertx.eventBus().send(wrapper.getEndpoint().getBinaryHandlerId(), Serializer.serialize(wrapper.getBody()));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void replyToAllWS(Message<byte[]> message) {
        try {
            log("Reply to all: " + this);
            final WSMessageWrapper wrapper = (WSMessageWrapper) Serializer.deserialize(message.body());
            final String stringResult = TypeTool.trySerializeToString(wrapper.getBody());
            final byte[] payload = stringResult != null ? stringResult.getBytes() : Serializer.serialize(wrapper.getBody());

            final SharedData sharedData = this.vertx.sharedData();
            final LocalMap<String, byte[]> wsRegistry = sharedData.getLocalMap("wsRegistry");
            final byte[] holderPayload = wsRegistry.get("wsEndpointHolder");
            if (holderPayload != null) {
                WSEndpointHolder holder = (WSEndpointHolder) deserialize(holderPayload);
                final List<WSEndpoint> all = holder.getAll();
                all.stream().
                        filter(endP -> endP.getUrl().equals(wrapper.getEndpoint().getUrl())).
                        forEach(
                                endpoint -> {
                                    if (stringResult != null) {
                                        vertx.eventBus().send(endpoint.getTextHandlerId(), stringResult);
                                    } else {
                                        vertx.eventBus().send(endpoint.getBinaryHandlerId(), payload);
                                    }
                                }
                        );
            }


        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    private <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
        return result -> {
            if (result.failed()) {
                result.cause().printStackTrace();

            } else {
                consumer.accept(result.result());
            }
        };
    }

    private void log(final String value) {
        System.out.println(value);
    }
}
