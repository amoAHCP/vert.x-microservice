package org.jacpfx.common;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.jacpfx.common.spi.JSONConverter;

import java.io.IOException;

/**
 * Created by Andy Moncsek on 22.05.15.
 */
public class EBMessageReply {

    private EventBus bus;
    private Message<?> message;
    private String consumes;
    private JSONConverter jsonConverter;

    public EBMessageReply(EventBus bus, Message<?> message,String consumes,JSONConverter jsonConverter) {
        this.bus = bus;
        this.message = message;
        this.consumes = consumes;
        this.jsonConverter = jsonConverter;
    }

    public void reply(Object m) {

        if (isBinary(consumes)) {
            this.message.reply(serializeResult(m));
        } else if(isJSON(consumes)) {
            if (TypeTool.isCompatibleType(m.getClass())) {
                this.message.reply(m);
            } else {
                this.message.reply(jsonConverter.convertToJSONString(m));
            }
        } else if (TypeTool.isCompatibleType(m.getClass())) {
            this.message.reply(m);
        }
    }

    private byte[] serializeResult(Object resultValue) {
        byte[] result = new byte[0];
        try {
            result = Serializer.serialize(resultValue);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    private boolean isBinary(final String consumes) {
        return consumes.equalsIgnoreCase("application/octet-stream");
    }

    private boolean isJSON(final String consumes) {
        return consumes.equalsIgnoreCase("application/json");
    }
}
