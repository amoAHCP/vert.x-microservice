package org.jacpfx.vertx.websocket.response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.jacpfx.common.Serializer;
import org.jacpfx.common.WSEndpoint;
import org.jacpfx.vertx.websocket.decoder.Decoder;
import org.jacpfx.vertx.websocket.util.WSRegistry;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Created by Andy Moncsek on 17.11.15.
 */
public class WSHandler {
    private final static ExecutorService EXECUTOR = Executors.newCachedThreadPool(); // TODO use fixed size and get amount of vertcle instances
    private final WSEndpoint endpoint;
    private final Vertx vertx;
    private final WSRegistry registry;
    private byte[] value;


    public WSHandler(WSRegistry registry, WSEndpoint endpoint, byte[] value, Vertx vertx) {
        this.endpoint = endpoint;
        this.vertx = vertx;
        this.registry = registry;
        this.value = value;
    }

    /**
     * Returns the Endpoint definition with URL and handler ids
     *
     * @return {@see WSEndpoint}
     */
    public WSEndpoint endpoint() {
        return this.endpoint;
    }

    /**
     * Returns the payload handler which gives you access to the payload transmitted
     *
     * @return {@see Payload}
     */
    public Payload payload() {
        return new Payload(value);
    }


    public TargetType response() {
        return new TargetType(endpoint, vertx, false);
    }

    public class Payload {
        private final byte[] value;

        private Payload(byte[] value) {
            this.value = value;
        }

        public Optional<String> getString() {
            return Optional.ofNullable(value != null ? new String(value) : null);
        }

        public Optional<byte[]> getBytes() {
            return Optional.ofNullable(value);
        }

        public <T> Optional<T> getObject(Class<T> clazz, Decoder decoder) {
            Objects.requireNonNull(clazz);
            Objects.requireNonNull(decoder);
            if (decoder instanceof Decoder.ByteDecoder) {
                return ((Decoder.ByteDecoder<T>) decoder).decode(value);
            } else if (decoder instanceof Decoder.StringDecoder) {
                return ((Decoder.StringDecoder<T>) decoder).decode(new String(value));
            }
            return Optional.ofNullable(null);
        }
    }

    public class TargetType {
        private final WSEndpoint endpoint;
        private final Vertx vertx;
        private final boolean async;

        private TargetType(WSEndpoint endpoint, Vertx vertx, boolean async) {
            this.endpoint = endpoint;
            this.vertx = vertx;
            this.async = async;
        }

        public TargetType async() {
            return new TargetType(endpoint, vertx, true);
        }

        public ResponseType toAll() {
            return new ResponseType(endpoint, vertx, async, CommType.ALL);
        }

        public ResponseType toAllButCaller() {
            return new ResponseType(endpoint, vertx, async, CommType.ALL_BUT_CALLER);
        }

        public ResponseType toCaller() {
            return new ResponseType(endpoint, vertx, async, CommType.CALLER);
        }


    }

    public class ResponseType {
        private final WSEndpoint endpoint;
        private final Vertx vertx;
        private final boolean async;
        private final CommType commType;

        private ResponseType(WSEndpoint endpoint, Vertx vertx, final boolean async, final CommType commType) {
            this.endpoint = endpoint;
            this.vertx = vertx;
            this.async = async;
            this.commType = commType;
        }

        public ExecuteWSResponse byteResponse(Supplier<byte[]> byteSupplier) {
            return new ExecuteWSResponse(endpoint, vertx, async, commType, byteSupplier, null, null);
        }

        public ExecuteWSResponse stringResponse(Supplier<String> stringSupplier) {
            return new ExecuteWSResponse(endpoint, vertx, async, commType, null, stringSupplier, null);
        }

        public ExecuteWSResponse objectResponse(Supplier<Serializable> objectSupplier) {
            return new ExecuteWSResponse(endpoint, vertx, async, commType, null, null, objectSupplier);
        }
    }

    public class ExecuteWSResponse {
        private final WSEndpoint endpoint;
        private final Vertx vertx;
        private final boolean async;
        private final CommType commType;
        private final Supplier<byte[]> byteSupplier;
        private final Supplier<String> stringSupplier;
        private final Supplier<Serializable> objectSupplier;

        private ExecuteWSResponse(WSEndpoint endpoint, Vertx vertx, boolean async, CommType commType, Supplier<byte[]> byteSupplier, Supplier<String> stringSupplier, Supplier<Serializable> objectSupplier) {
            this.endpoint = endpoint;
            this.vertx = vertx;
            this.async = async;
            this.commType = commType;
            this.byteSupplier = byteSupplier;
            this.stringSupplier = stringSupplier;
            this.objectSupplier = objectSupplier;
        }

        public void execute() {
            if (async) {
                Optional.ofNullable(byteSupplier).
                        ifPresent(supplier -> CompletableFuture.supplyAsync(byteSupplier, EXECUTOR).thenAccept(this::sendBinary));
                Optional.ofNullable(stringSupplier).
                        ifPresent(supplier -> CompletableFuture.supplyAsync(stringSupplier, EXECUTOR).thenAccept(this::sendText));
                Optional.ofNullable(objectSupplier). // TODO be aware of encoder
                        ifPresent(supplier -> CompletableFuture.supplyAsync(objectSupplier, EXECUTOR).thenAccept(value -> serialize(value).ifPresent(val -> sendBinary(val))));
            } else {
                // TODO check for exception, think about @OnError method execution
                Optional.ofNullable(byteSupplier).
                        ifPresent(supplier -> Optional.ofNullable(supplier.get()).ifPresent(this::sendBinary));
                // TODO check for exception, think about @OnError method execution
                Optional.ofNullable(stringSupplier).
                        ifPresent(supplier -> Optional.ofNullable(supplier.get()).ifPresent(this::sendText));
                // TODO check for exception, think about @OnError method execution
                Optional.ofNullable(objectSupplier).  // TODO be aware of encoder
                        ifPresent(supplier -> Optional.ofNullable(supplier.get()).ifPresent(value -> serialize(value).ifPresent(val -> sendBinary(val))));

            }
        }

        // TODO check if CompleteableFuture must be executed in vertx.executeBlocking
        private void executeBlocking() {
            Optional.ofNullable(byteSupplier).
                    ifPresent(supplier -> this.vertx.executeBlocking(handler -> {
                                try {
                                    handler.complete(supplier.get());
                                } catch (Exception e) {
                                    handler.fail(e);
                                }

                            }, (Handler<AsyncResult<byte[]>>) result ->
                                    // TODO handle exception
                                    Optional.ofNullable(result.result()).ifPresent(byteResult -> sendBinary(byteResult))
                    ));


            Optional.ofNullable(stringSupplier).
                    ifPresent(supplier -> this.vertx.executeBlocking(handler -> {
                                try {
                                    handler.complete(supplier.get());
                                } catch (Exception e) {
                                    handler.fail(e);
                                }

                            }, (Handler<AsyncResult<String>>) result ->
                                    // TODO handle exception
                                    Optional.ofNullable(result.result()).ifPresent(stringResult -> sendText(stringResult))
                    ));


            Optional.ofNullable(objectSupplier).
                    ifPresent(supplier -> this.vertx.executeBlocking(handler -> {
                                try {
                                    handler.complete(supplier.get());
                                } catch (Exception e) {
                                    handler.fail(e);
                                }

                            }, (Handler<AsyncResult<Serializable>>) result ->
                                    // TODO handle exception
                                    Optional.ofNullable(result.result()).ifPresent(value1 -> serialize(value1).ifPresent(val -> sendBinary(val)))
                    ));
        }

        private Optional<byte[]> serialize(Serializable value) {
            try {
                return Optional.ofNullable(Serializer.serialize(value));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return Optional.ofNullable(null);
        }

        private void sendText(String value) {
            switch (commType) {

                case ALL:
                    registry.findEndpointsAndExecute(endpoint, match -> {
                        vertx.eventBus().send(match.getTextHandlerId(), value);
                    });
                    break;
                case ALL_BUT_CALLER:
                    registry.findEndpointsAndExecute(endpoint, match -> {
                        if (!endpoint.equals(match)) vertx.eventBus().send(match.getTextHandlerId(), value);
                    });
                    break;
                case CALLER:
                    vertx.eventBus().send(endpoint.getTextHandlerId(), value);
                    break;
            }
        }

        private void sendBinary(byte[] value) {
            switch (commType) {

                case ALL:
                    registry.findEndpointsAndExecute(endpoint, match -> {
                        vertx.eventBus().send(match.getBinaryHandlerId(), value);
                    });
                    break;
                case ALL_BUT_CALLER:
                    registry.findEndpointsAndExecute(endpoint, match -> {
                        if (!endpoint.equals(match)) vertx.eventBus().send(match.getTextHandlerId(), value);
                    });
                    break;
                case CALLER:
                    vertx.eventBus().send(endpoint.getBinaryHandlerId(), value);
                    break;
            }
        }
    }

    public enum CommType {
        ALL, ALL_BUT_CALLER, CALLER
    }
}
