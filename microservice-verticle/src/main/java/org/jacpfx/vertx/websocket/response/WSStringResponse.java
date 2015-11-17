package org.jacpfx.vertx.websocket.response;

import io.vertx.core.eventbus.EventBus;
import org.jacpfx.common.WSEndpoint;

import java.util.function.Supplier;

/**
 * Created by Andy Moncsek on 17.11.15.
 */
public class WSStringResponse {
    private final WSEndpoint endpoint;
    private final EventBus bus;

    public WSStringResponse(WSEndpoint endpoint, EventBus bus) {
        this.endpoint = endpoint;
        this.bus = bus;
    }

    public WSEndpoint endpoint() {
        return endpoint;
    }

    public void reply(Supplier<String> execute) {
        // TODO add Exception handling for execute.get !! going resillient!!
        bus.send(endpoint.getTextHandlerId(), execute.get());
    }
}
