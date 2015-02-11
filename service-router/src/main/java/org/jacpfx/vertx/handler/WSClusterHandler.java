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
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Lock;
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
 */
public class WSClusterHandler {

    private static final Logger log = LoggerFactory.getLogger(WSClusterHandler.class);

    private final Vertx vertx;

    public WSClusterHandler(Vertx vertx) {
        this.vertx = vertx;
    }


    public void findRouteToWSServiceAndRegister(ServerWebSocket serverSocket) {
        final SharedData sharedData = this.vertx.sharedData();
        sharedData.<String, ServiceInfoHolder>getClusterWideMap("registry", onSuccess(resultMap ->
                        resultMap.get("serviceHolder", onSuccess(resultHolder -> findServiceEntryAndRegisterWS(serverSocket, resultHolder, sharedData)))
        ));
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
                flatMap(infos -> infos.stream()).
                filter(op -> op.getUrl().equalsIgnoreCase(path)).
                findFirst();
    }

    private void createEndpointDefinitionAndRegister(ServerWebSocket serverSocket, final SharedData sharedData) {
        sharedData.<String, WSEndpointHolder>getClusterWideMap("wsRegistry", onSuccess(registryMap ->
                        getEndpointHolderAndAdd(serverSocket, registryMap, sharedData)
        ));
    }

    private void getEndpointHolderAndAdd(ServerWebSocket serverSocket, AsyncMap<String, WSEndpointHolder> registryMap, final SharedData sharedData) {
        sharedData.getLockWithTimeout("wsLock", 90000L, onSuccess(lock -> {
            registryMap.get("wsEndpointHolder", wsEndpointHolder -> {
                if (wsEndpointHolder.succeeded()) {
                    updateWSEndpointHolder(serverSocket, registryMap, lock, wsEndpointHolder);
                } else {
                    lock.release();
                }
            });


        }));

    }

    private void updateWSEndpointHolder(ServerWebSocket serverSocket, AsyncMap<String, WSEndpointHolder> registryMap, Lock lock, AsyncResult<WSEndpointHolder> wsEndpointHolder) {
        log("add entry: " + Thread.currentThread());
        final String binaryHandlerId = serverSocket.binaryHandlerID();
        final String textHandlerId = serverSocket.textHandlerID();
        final String path = serverSocket.path();
        final EventBus eventBus = vertx.eventBus();
        final WSEndpoint endpoint = new WSEndpoint(binaryHandlerId, textHandlerId, path);
        final WSEndpointHolder result = wsEndpointHolder.result();
        if (result != null) {
            addDefinitionToRegistry(serverSocket, eventBus, path, endpoint, registryMap, result, lock);
        } else {
            createEntryAndAddDefinition(serverSocket, eventBus, path, endpoint, registryMap, lock);
        }
    }

    private void createEntryAndAddDefinition(ServerWebSocket serverSocket, EventBus eventBus, String path, WSEndpoint endpoint, AsyncMap<String, WSEndpointHolder> registryMap, Lock writeLock) {
        final WSEndpointHolder holder = new WSEndpointHolder();
        holder.add(endpoint);
        registryMap.put("wsEndpointHolder", holder, s -> {
                    writeLock.release();
                    if (s.succeeded()) {
                        log("OK ADD: " + serverSocket.binaryHandlerID() + "  Thread" + Thread.currentThread());
                        sendToWSService(serverSocket, eventBus, path, endpoint);
                    }
                }

        );
    }

    private void addDefinitionToRegistry(ServerWebSocket serverSocket, EventBus eventBus, String path, WSEndpoint endpoint, AsyncMap<String, WSEndpointHolder> registryMap, WSEndpointHolder wsEndpointHolder, Lock writeLock) {
        final WSEndpointHolder holder = wsEndpointHolder;
        holder.add(endpoint);
        registryMap.replace("wsEndpointHolder", holder, s -> {
                    writeLock.release();
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
        final String binaryHandlerID = serverSocket.binaryHandlerID();
        final String textHandlerID = serverSocket.textHandlerID();
        this.vertx.sharedData().<String, WSEndpointHolder>getClusterWideMap("wsRegistry", onSuccess(registryMap -> {
                    vertx.sharedData().getLockWithTimeout("wsLock", 90000L, onSuccess(lock -> {
                        registryMap.get("wsEndpointHolder", wsEndpointHolder -> {
                            retrieveEndpointHolderAndRemove(serverSocket, binaryHandlerID, textHandlerID, registryMap, lock, wsEndpointHolder);

                        });
                    }));


                })
        );
    }

    private void retrieveEndpointHolderAndRemove(ServerWebSocket serverSocket, String binaryHandlerID, String textHandlerID, AsyncMap<String, WSEndpointHolder> registryMap, Lock lock, AsyncResult<WSEndpointHolder> wsEndpointHolder) {
        if (wsEndpointHolder.succeeded()) {
            final WSEndpointHolder result = wsEndpointHolder.result();
            if (result != null) {
                findEndpointAndRemove(serverSocket, binaryHandlerID, textHandlerID, registryMap, lock, result);

            }
        } else {
            lock.release();
        }
    }

    private void findEndpointAndRemove(ServerWebSocket serverSocket, String binaryHandlerID, String textHandlerID, AsyncMap<String, WSEndpointHolder> registryMap, Lock lock, WSEndpointHolder wsEndpointHolder) {
        final List<WSEndpoint> all = wsEndpointHolder.getAll();
        final Optional<WSEndpoint> first = all.stream().filter(e -> e.getBinaryHandlerId().equals(binaryHandlerID) && e.getTextHandlerId().equals(textHandlerID)).findFirst();
        if (first.isPresent()) {
            first.ifPresent(endpoint -> {
                wsEndpointHolder.remove(endpoint);
                registryMap.replace("wsEndpointHolder", wsEndpointHolder, replaceHolder -> {
                    lock.release();
                    log("OK REMOVE: " + serverSocket.binaryHandlerID() + "  succeed:" + replaceHolder.succeeded());

                });
            });
        } else {
            lock.release();
        }
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
            this.vertx.sharedData().<String, WSEndpointHolder>getClusterWideMap("wsRegistry", onSuccess(registryMap -> {
                        registryMap.get("wsEndpointHolder", wsEndpointHolder -> {
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
                        });
                    }
            ));

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
        log.info(value);
    }
}
