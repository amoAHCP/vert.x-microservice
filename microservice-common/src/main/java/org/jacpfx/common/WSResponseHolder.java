package org.jacpfx.common;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * Created by Andy Moncsek on 28.08.15.
 */
public class WSResponseHolder {

    private final Supplier<Serializable> response;
    private final WSResponseType type;

    public WSResponseHolder(Supplier<Serializable> response, WSResponseType type) {
        this.response = response;
        this.type = type;
    }

    public Supplier<Serializable> getResponse() {
        return response;
    }

    public WSResponseType getType() {
        return type;
    }
}
