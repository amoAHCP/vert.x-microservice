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



    public void replyAsync(Supplier<Serializable> execute) {
        replyAsyncExecution("ws.reply", execute, WSReply.SENDER);
    }

    public void replyToAllAsync(Supplier<Serializable> execute) {
        replyAsyncExecution("ws.replyToAll",execute,WSReply.ALL);
    }

    public void replyToAllButSenderAsync(Supplier<Serializable> execute) {
        replyAsyncExecution("ws.replyToAll",execute, WSReply.ALL_BUT_SENDER);
    }

    private void replyAsyncExecution(final String url, Supplier<Serializable> execute,WSReply to) {
       CompletableFuture.supplyAsync(execute, EXECUTOR).
               thenApplyAsync(val -> serializeResult(val, to)).
               thenAcceptAsync(serializedResult -> bus.send(url, serializedResult));
    }

    private byte[] serializeResult(Serializable resultValue, WSReply to) {
        byte[] result = new byte[0];
        try {
            result = Serializer.serialize(new WSMessageWrapper(endpoint, resultValue, resultValue.getClass(), to));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void reply(Serializable message) {
        bus.send("ws.reply", serializeResult(message, WSReply.SENDER));

    }

    public void replyToAll(Serializable message) {
        bus.send("ws.replyToAll", serializeResult(message, WSReply.ALL));
    }

    public void replyToAllButSender(Serializable message) {
        bus.send("ws.replyToAll", serializeResult(message, WSReply.ALL_BUT_SENDER));
    }
}
