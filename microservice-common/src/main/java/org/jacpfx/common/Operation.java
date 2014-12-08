package org.jacpfx.common;

import java.io.Serializable;

/**
 * Created by amo on 27.10.14.
 */
public class Operation implements Serializable{
    private String url;
    private String type;
    private String[] mime;
    private String[] parameter;

    public Operation(String url, String type, String[] mime, String... param) {
        this.url = url;
        this.type = type;
        this.parameter = param;
        this.mime = mime;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }


    public String[] getParameter() {
        return parameter;
    }

    public String[] getMime() {
        return mime;
    }


}
