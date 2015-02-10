package org.jacpfx.common;

import io.vertx.core.eventbus.EventBus;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Created by Andy Moncsek on 10.12.14.
 */
public class MessageReply {

    private final static ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private WSEndpoint endpoint;
    private EventBus bus;

    public MessageReply() {

    }

    public MessageReply(WSEndpoint endpoint, EventBus bus) {
        this.endpoint = endpoint;
        this.bus = bus;
    }

    public void reply(Serializable message) {
        // TODO wrap to object which indicates if reply to all or to sender

    }

    public void replyAsync(Supplier<Serializable> execute) {
        final CompletableFuture<Serializable> future = CompletableFuture.supplyAsync(execute, EXECUTOR);
        final CompletableFuture<byte[]> serializedFuture = future.thenApplyAsync(this::serializeResult);
        serializedFuture.thenAcceptAsync(serializedResult -> bus.send("ws.reply", serializedResult));
    }

    public void replyToAllAsync(Supplier<Serializable> execute) {
        final CompletableFuture<Serializable> future = CompletableFuture.supplyAsync(execute, EXECUTOR);
        final CompletableFuture<byte[]> serializedFuture = future.thenApplyAsync(this::serializeResult);
        serializedFuture.thenAcceptAsync(serializedResult -> bus.send("ws.replyToAll", serializedResult));
    }

    private byte[] serializeResult(Serializable resultValue) {
        byte[] result = new byte[0];
        try {
            result = Serializer.serialize(new WSMessageWrapper(endpoint, resultValue, resultValue.getClass(), WSReply.SENDER));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void send(Serializable message) {
        // TODO wrap to object which indicates if reply to all or to sender
        try {
            bus.send("ws.reply", Serializer.serialize(new WSMessageWrapper(endpoint, message, message.getClass(), WSReply.SENDER)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void replyToAll(Object message) {

    }

    public void replyToAllButSender(Object message) {

    }
}
