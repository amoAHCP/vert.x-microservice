package org.jacpfx.vertx.handler;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import org.jacpfx.common.*;
import org.jacpfx.vertx.entrypoint.ServiceEntryPoint;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Created by Andy Moncsek on 11.02.15.
 */
public class WSClusterHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WSClusterHandler.class);

    private final Vertx vertx;

    public WSClusterHandler(Vertx vertx) {
        this.vertx = vertx;
    }


    @Override
    public void findRouteToWSServiceAndRegister(ServerWebSocket serverSocket) {
        final SharedData sharedData = this.vertx.sharedData();
        sharedData.<String, ServiceInfoHolder>getClusterWideMap(REGISTRY, onSuccess(resultMap ->
                        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(resultHolder -> findServiceEntryAndRegisterWS(serverSocket, resultHolder, sharedData)))
        ));
    }

    @Override
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

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void findRouteSocketInRegistryAndRemove(ServerWebSocket serverSocket) {
        final String binaryHandlerID = serverSocket.binaryHandlerID();
        final String textHandlerID = serverSocket.textHandlerID();
        this.vertx.sharedData().<String, WSEndpointHolder>getClusterWideMap(WS_REGISTRY, onSuccess(registryMap -> registryMap.get(WS_ENDPOINT_HOLDER, wsEndpointHolder -> {
            retrieveEndpointHolderAndRemove(serverSocket, binaryHandlerID, textHandlerID, registryMap, wsEndpointHolder);

        }))
        );
    }

    private void findServiceEntryAndRegisterWS(final ServerWebSocket serverSocket, final ServiceInfoHolder resultHolder, final SharedData sharedData) {
        if (resultHolder != null) {
            final String path = serverSocket.path();
            log("find entry : " + path);
            final Optional<Operation> operationResult = findServiceInfoEntry(resultHolder, path);
            operationResult.ifPresent(op ->
                            createEndpointDefinitionAndRegister(serverSocket, sharedData)
            );
        }
    }

    private Optional<Operation> findServiceInfoEntry(ServiceInfoHolder resultHolder, String path) {
        return resultHolder.
                getAll().
                stream().
                map(info -> Arrays.asList(info.getOperations())).
                flatMap(Collection::stream).
                filter(op -> op.getUrl().equalsIgnoreCase(path)).
                findFirst();
    }

    private void createEndpointDefinitionAndRegister(ServerWebSocket serverSocket, final SharedData sharedData) {
        sharedData.<String, WSEndpointHolder>getClusterWideMap(WS_REGISTRY, onSuccess(registryMap ->
                        getEndpointHolderAndAdd(serverSocket, registryMap)
        ));
    }

    private void getEndpointHolderAndAdd(ServerWebSocket serverSocket, AsyncMap<String, WSEndpointHolder> registryMap) {
        registryMap.get(WS_ENDPOINT_HOLDER, wsEndpointHolder -> {
            if (wsEndpointHolder.succeeded()) {
                updateWSEndpointHolder(serverSocket, registryMap, wsEndpointHolder);
            }
        });

    }

    private void updateWSEndpointHolder(ServerWebSocket serverSocket, AsyncMap<String, WSEndpointHolder> registryMap, AsyncResult<WSEndpointHolder> wsEndpointHolder) {
        log("add entry: " + Thread.currentThread());
        final String binaryHandlerId = serverSocket.binaryHandlerID();
        final String textHandlerId = serverSocket.textHandlerID();
        final String path = serverSocket.path();
        final EventBus eventBus = vertx.eventBus();
        final WSEndpoint endpoint = new WSEndpoint(binaryHandlerId, textHandlerId, path);
        final WSEndpointHolder result = wsEndpointHolder.result();
        if (result != null) {
            addDefinitionToRegistry(serverSocket, eventBus, path, endpoint, registryMap, result);
        } else {
            createEntryAndAddDefinition(serverSocket, eventBus, path, endpoint, registryMap);
        }
    }

    private void createEntryAndAddDefinition(ServerWebSocket serverSocket, EventBus eventBus, String path, WSEndpoint endpoint, AsyncMap<String, WSEndpointHolder> registryMap) {
        final WSEndpointHolder holder = new WSEndpointHolder();
        holder.add(endpoint);
        registryMap.put(WS_ENDPOINT_HOLDER, holder, s -> {
                    if (s.succeeded()) {
                        log("OK ADD: " + serverSocket.binaryHandlerID() + "  Thread" + Thread.currentThread());
                        sendToWSService(serverSocket, eventBus, path, endpoint);
                    }
                }

        );
    }

    private void addDefinitionToRegistry(ServerWebSocket serverSocket, EventBus eventBus, String path, WSEndpoint endpoint, AsyncMap<String, WSEndpointHolder> registryMap, WSEndpointHolder wsEndpointHolder) {
        wsEndpointHolder.add(endpoint);
        registryMap.replace(WS_ENDPOINT_HOLDER, wsEndpointHolder, s -> {
                    if (s.succeeded()) {
                        log("OK REPLACE: " + serverSocket.binaryHandlerID() + "  Thread" + Thread.currentThread());
                        sendToWSService(serverSocket, eventBus, path, endpoint);
                    }
                }
        );
    }

    private void sendToWSService(final ServerWebSocket serverSocket, final EventBus eventBus, final String path, final WSEndpoint endpoint) {
        serverSocket.handler(handler -> {
                    try {
                        eventBus.send(path, Serializer.serialize(new WSDataWrapper(endpoint, handler.getBytes())), new DeliveryOptions().setSendTimeout(ServiceEntryPoint.DEFAULT_SERVICE_TIMEOUT));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


        );
        serverSocket.resume();
        //TODO set close handler!!
    }



    private void retrieveEndpointHolderAndRemove(ServerWebSocket serverSocket, String binaryHandlerID, String textHandlerID, AsyncMap<String, WSEndpointHolder> registryMap, AsyncResult<WSEndpointHolder> wsEndpointHolder) {
        if (wsEndpointHolder.succeeded()) {
            final WSEndpointHolder result = wsEndpointHolder.result();
            if (result != null) {
                findEndpointAndRemove(serverSocket, binaryHandlerID, textHandlerID, registryMap,result);

            }
        }
    }

    private void findEndpointAndRemove(ServerWebSocket serverSocket, String binaryHandlerID, String textHandlerID, AsyncMap<String, WSEndpointHolder> registryMap, WSEndpointHolder wsEndpointHolder) {
        final List<WSEndpoint> all = wsEndpointHolder.getAll();
        final Optional<WSEndpoint> first = all.stream().filter(e -> e.getBinaryHandlerId().equals(binaryHandlerID) && e.getTextHandlerId().equals(textHandlerID)).findFirst();
        if (first.isPresent()) {
            first.ifPresent(endpoint -> {
                wsEndpointHolder.remove(endpoint);
                registryMap.replace(WS_ENDPOINT_HOLDER, wsEndpointHolder, replaceHolder -> log("OK REMOVE: " + serverSocket.binaryHandlerID() + "  succeed:" + replaceHolder.succeeded()));
            });
        }
    }



    public void replyToAllWS(Message<byte[]> message) {
        try {
            log("Reply to all: " + this);
            final WSMessageWrapper wrapper = (WSMessageWrapper) Serializer.deserialize(message.body());
            final String stringResult = TypeTool.trySerializeToString(wrapper.getBody());
            final byte[] payload = stringResult != null ? stringResult.getBytes() : Serializer.serialize(wrapper.getBody());
            this.vertx.sharedData().<String, WSEndpointHolder>getClusterWideMap(WS_REGISTRY, onSuccess(registryMap -> registryMap.get(WS_ENDPOINT_HOLDER, wsEndpointHolder -> {
                if (wsEndpointHolder.succeeded() && wsEndpointHolder.result() != null) {
                    final List<WSEndpoint> all = wsEndpointHolder.result().getAll();
                    all.stream().filter(endP -> {
                        log(endP.getUrl() + " equals: " + endP.getUrl().equals(wrapper.getEndpoint().getUrl()));
                        return endP.getUrl().equals(wrapper.getEndpoint().getUrl());
                    }).forEach(
                            endpoint -> {
                                if (stringResult != null) {
                                    vertx.eventBus().send(endpoint.getTextHandlerId(), stringResult);
                                } else {
                                    vertx.eventBus().send(endpoint.getBinaryHandlerId(), payload);
                                }
                            }
                    );

                }
            })
            ));

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void log(final String value) {
        log.info(value);
    }
}
