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
 * The WSResponse defines the response of a WebSocket call
 * Created by Andy Moncsek on 28.08.15.
 */
public class WSResponse {

    private final WSResponseHolder response;

    private final static ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final WSEndpoint endpoint;
    private final EventBus bus;
    private final String localReply, replyToAll, replyToAllButSender, selfHostedPostfix;
    private final boolean selfhosted;

    public WSResponse(WSResponseHolder response) {
        this(null, null, null, null, null, null, false, response);
    }

    public WSResponse(WSEndpoint endpoint, EventBus bus, JsonObject config) {
        this(endpoint, bus, config.getString("wsReplyPath", GlobalKeyHolder.WS_REPLY), config.getString("wsReplyToAllPath", GlobalKeyHolder.WS_REPLY_TO_ALL), config.getString("wsReplyToAllButSenderPath", GlobalKeyHolder.WS_REPLY_TO_ALL_BUT_ME), config.getString("selfhosted-host", ""), config.getBoolean("selfhosted", false), null);
    }

    public WSResponse(WSEndpoint endpoint, EventBus bus, JsonObject config, WSResponseHolder response) {
        this(endpoint, bus, config.getString("wsReplyPath", GlobalKeyHolder.WS_REPLY), config.getString("wsReplyToAllPath", GlobalKeyHolder.WS_REPLY_TO_ALL), config.getString("wsReplyToAllButSenderPath", GlobalKeyHolder.WS_REPLY_TO_ALL_BUT_ME), config.getString("selfhosted-host", ""), config.getBoolean("selfhosted", false), response);
    }

    public WSResponse(WSEndpoint endpoint, EventBus bus, String localReply, String replyToAll, String replyToAllButSender, String selfHostedPostfix, boolean selfhosted, WSResponseHolder response) {
        this.endpoint = endpoint;
        this.bus = bus;
        this.selfHostedPostfix = selfHostedPostfix;
        this.selfhosted = selfhosted;
        this.localReply = selfhosted ? localReply + selfHostedPostfix : localReply;
        this.replyToAll = selfhosted ? replyToAll + selfHostedPostfix : replyToAll;
        this.replyToAllButSender = selfhosted ? replyToAllButSender + selfHostedPostfix : replyToAllButSender;
        this.response = response;
    }

    public void buildAndSend(WSEndpoint endpoint, EventBus bus, JsonObject config) {
        new WSResponse(endpoint, bus, config, response).send();

    }

    private void send() {
        switch (response.getType()) {
            case SENDER:
                replyAsyncExecution(this.localReply, response.getResponse(), WSResponseType.SENDER);
                break;
            case ALL:
                replyAsyncExecution(this.localReply, response.getResponse(), WSResponseType.ALL);
                break;
            case ALL_BUT_SENDER:
                replyAsyncExecution(this.localReply, response.getResponse(), WSResponseType.ALL_BUT_SENDER);
                break;
            default:
        }
    }

    private byte[] serializeResult(Serializable resultValue, WSResponseType to) {
        byte[] result = new byte[0];
        try {
            result = Serializer.serialize(new WSMessageWrapper(endpoint, resultValue, resultValue.getClass(), to));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void replyAsyncExecution(final String url, Supplier<Serializable> execute, WSResponseType to) {
        // TODO reply NOT to ServiceEntryPoint, but to EndpointAddress itself
        CompletableFuture.supplyAsync(execute, EXECUTOR).
                thenApplyAsync(val -> serializeResult(val, to)).
                thenAcceptAsync(serializedResult -> bus.send(url, serializedResult));
    }

    public void reply(Supplier<Serializable> execute) {
        replyAsyncExecution(this.localReply, execute, WSResponseType.SENDER);
    }

    public void replyToAll(Supplier<Serializable> execute) {
        replyAsyncExecution(this.replyToAll, execute, WSResponseType.ALL);
    }

    public void replyToOtherConnected(Supplier<Serializable> execute) {
        replyAsyncExecution(this.replyToAllButSender,execute, WSResponseType.ALL_BUT_SENDER);
    }


}
