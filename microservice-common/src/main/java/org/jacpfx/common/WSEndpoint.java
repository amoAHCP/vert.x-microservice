package org.jacpfx.common;

import java.io.Serializable;

/**
 * Represents the WebSocket session with it's binary- and texthandler id and it's url
 * Created by Andy Moncsek on 12.12.14.
 */
public class WSEndpoint implements Serializable {
    private final String binaryHandlerId;
    private final String textHandlerId;
    private final String url;

    public WSEndpoint(final String binaryHandlerId, final String textHandlerId, final String url) {
        this.binaryHandlerId = binaryHandlerId;
        this.textHandlerId = textHandlerId;
        this.url = url;
    }

    public String getBinaryHandlerId() {
        return binaryHandlerId;
    }

    public String getTextHandlerId() {
        return textHandlerId;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WSEndpoint)) return false;

        WSEndpoint that = (WSEndpoint) o;

        if (binaryHandlerId != null ? !binaryHandlerId.equals(that.binaryHandlerId) : that.binaryHandlerId != null)
            return false;
        if (textHandlerId != null ? !textHandlerId.equals(that.textHandlerId) : that.textHandlerId != null)
            return false;
        if (url != null ? !url.equals(that.url) : that.url != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = binaryHandlerId != null ? binaryHandlerId.hashCode() : 0;
        result = 31 * result + (textHandlerId != null ? textHandlerId.hashCode() : 0);
        result = 31 * result + (url != null ? url.hashCode() : 0);
        return result;
    }
}
