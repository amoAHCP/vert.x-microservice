package org.jacpfx.common;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.jacpfx.common.constants.GlobalKeyHolder;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Created by Andy Moncsek on 10.12.14.
 */
public class WSMessageReply {

    private final static ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final WSEndpoint endpoint;
    private final EventBus bus;
    private final String localReply, replyToAll, replyToAllButSender, selfHostedPostfix;
    private final boolean selfhosted;

    public WSMessageReply() {
       this(null,null,null,null,null,null,false);
    }

    public WSMessageReply(WSEndpoint endpoint, EventBus bus, JsonObject config) {
        this(endpoint, bus, config.getString("wsReplyPath", GlobalKeyHolder.WS_REPLY), config.getString("wsReplyToAllPath", GlobalKeyHolder.WS_REPLY_TO_ALL), config.getString("wsReplyToAllButSenderPath", GlobalKeyHolder.WS_REPLY_TO_ALL_BUT_ME), config.getString("selfhosted-host",""),config.getBoolean("selfhosted",false));
    }

    public WSMessageReply(WSEndpoint endpoint, EventBus bus, String localReply, String replyToAll, String replyToAllButSender,String selfHostedPostfix, boolean selfhosted) {
        this.endpoint = endpoint;
        this.bus = bus;
        this.selfHostedPostfix = selfHostedPostfix;
        this.selfhosted = selfhosted;
        this.localReply = selfhosted?localReply+selfHostedPostfix:localReply;
        this.replyToAll = selfhosted?replyToAll+selfHostedPostfix:replyToAll;
        this.replyToAllButSender = selfhosted?replyToAllButSender+selfHostedPostfix:replyToAllButSender;
    }



    public void replyAsync(Supplier<Serializable> execute) {
        replyAsyncExecution(this.localReply, execute, WSReply.SENDER);
    }

    public void replyToAllAsync(Supplier<Serializable> execute) {
        replyAsyncExecution(this.replyToAll,execute,WSReply.ALL);
    }

    public void replyToAllButSenderAsync(Supplier<Serializable> execute) {
        replyAsyncExecution(this.replyToAllButSender,execute, WSReply.ALL_BUT_SENDER);
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
        bus.send(this.localReply, serializeResult(message, WSReply.SENDER));

    }

    public void replyToAll(Serializable message) {
        bus.send(this.replyToAll, serializeResult(message, WSReply.ALL));
    }

    public void replyToAllButSender(Serializable message) {
        bus.send(this.replyToAllButSender, serializeResult(message, WSReply.ALL_BUT_SENDER));
    }
}
