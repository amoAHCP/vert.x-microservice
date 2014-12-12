package org.jacpfx.common;

import io.vertx.core.eventbus.EventBus;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by Andy Moncsek on 10.12.14.
 */
public class MessageReply {

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

    public void send(Serializable message) {
        // TODO wrap to object which indicates if reply to all or to sender
        try {
            bus.send("ws.redirect", Serializer.serialize(new WSMessageWrapper(endpoint,message, message.getClass(), WSReply.SENDER)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void replyToAll(Object message){

    }

    public void replyToAllButSender(Object message){

    }
}
