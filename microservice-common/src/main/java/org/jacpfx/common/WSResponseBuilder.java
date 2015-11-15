package org.jacpfx.common;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * The WSResponse defines the response of a WebSocket call
 * Created by Andy Moncsek on 28.08.15.
 */
public class WSResponseBuilder {



    public final static WSResponse reply(Supplier<Serializable> execute) {
        return new WSResponse(new WSResponseHolder(execute,WSResponseType.SENDER));
    }

    public final static WSResponse replyToAll(Supplier<Serializable> execute) {
        return new WSResponse(new WSResponseHolder(execute,WSResponseType.ALL));
    }

    public final static WSResponse replyToOtherConnected(Supplier<Serializable> execute) {
        return new WSResponse(new WSResponseHolder(execute,WSResponseType.ALL_BUT_SENDER));
    }
}
