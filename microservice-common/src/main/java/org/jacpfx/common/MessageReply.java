package org.jacpfx.common;

import io.vertx.core.eventbus.Message;

/**
 * Created by Andy Moncsek on 10.12.14.
 */
public class MessageReply {

    private Message m;

    public MessageReply() {

    }

    public MessageReply(Message m) {

    }

    public void reply(Object message) {
        // TODO wrap to object which indicates if reply to all or to sender
        m.reply(message);
    }

    public void replyToAll(Object message){

    }

    public void replyToAllButSender(Object message){

    }
}
