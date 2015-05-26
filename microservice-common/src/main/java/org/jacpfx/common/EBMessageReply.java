package org.jacpfx.common;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

/**
 * Created by Andy Moncsek on 22.05.15.
 */
public class EBMessageReply {

    private EventBus bus;
    private Message<?> message;

    public EBMessageReply(EventBus bus, Message<?> message) {
        this.bus = bus;
        this.message = message;
    }

    public void reply(Object m) {
       this.message.reply(m);
    }
}
