package org.jacpfx.common.handler;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.ServerWebSocket;
import org.jacpfx.common.Serializer;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Created by Andy Moncsek on 26.03.15.
 */
public interface WebSocketHandler {

    public static final String WS_REGISTRY = "wsRegistry";
    public static final String WS_ENDPOINT_HOLDER = "wsEndpointHolder";
    public static final String WS_LOCK = "wsLock";
    public static final String REGISTRY = "registry";

    void findRouteToWSServiceAndRegister(ServerWebSocket serverSocket);

    void findRouteSocketInRegistryAndRemove(ServerWebSocket serverSocket);

    void replyToWSCaller(Message<byte[]> message);

    void replyToAllWS(Message<byte[]> message);

    default byte[] serialize(Object payload) {
        try {
            return Serializer.serialize(payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    default Object deserialize(byte[] payload) {
        try {
            return Serializer.deserialize(payload);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    default <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
        return result -> {
            if (result.failed()) {
                result.cause().printStackTrace();

            } else {
                consumer.accept(result.result());
            }
        };
    }
}
