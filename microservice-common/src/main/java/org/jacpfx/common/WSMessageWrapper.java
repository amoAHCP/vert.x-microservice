package org.jacpfx.common;

import java.io.Serializable;

/**
 * Created by Andy Moncsek on 11.12.14.
 */
public class WSMessageWrapper implements Serializable{

    private final Serializable body;
    private final Class<?> bodyType;
    private final WSResponseType replyeType;
    private final WSEndpoint endpoint;

    public WSMessageWrapper(final WSEndpoint endpoint,Serializable body,Class<?> bodyType,WSResponseType replyeType) {
         this.body = body;
        this.bodyType = bodyType;
        this.replyeType = replyeType;
        this.endpoint = endpoint;
    }

    public Serializable getBody() {
        return body;
    }

    public Class<?> getBodyType() {
        return bodyType;
    }

    public WSResponseType getReplyeType() {
        return replyeType;
    }

    public WSEndpoint getEndpoint() {
        return endpoint;
    }
}
