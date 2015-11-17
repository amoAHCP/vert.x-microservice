package org.jacpfx.vertx.websocket.response;

import io.vertx.core.eventbus.EventBus;
import org.jacpfx.common.Serializer;
import org.jacpfx.common.WSEndpoint;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Supplier;

/**
 * Created by Andy Moncsek on 17.11.15.
 */
public class WSByteResponse {
    private final WSEndpoint endpoint;
    private final EventBus bus;

    public WSByteResponse(WSEndpoint endpoint, EventBus bus) {
        this.endpoint = endpoint;
        this.bus = bus;
    }

    public WSEndpoint endpoint() {
        return endpoint;
    }

    public void reply(Supplier<Serializable> execute) {
        try {
            // TODO add Exception handling for execute.get !! going resillient!!
            bus.send(endpoint.getBinaryHandlerId(), Serializer.serialize(execute.get()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
