package org.jacpfx.common;

import java.io.Serializable;

/**
 * Created by Andy Moncsek on 12.12.14.
 */
public class WSDataWrapper implements Serializable {

    private final WSEndpoint endpoint;
    private final byte[] data;

    public WSDataWrapper(WSEndpoint endpoint, byte[] data) {
        this.endpoint = endpoint;
        this.data = data;
    }

    public WSEndpoint getEndpoint() {
        return endpoint;
    }

    public byte[] getData() {
        return data;
    }
}
