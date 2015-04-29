package org.jacpfx.common;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by amo on 27.10.14.
 */
public class Operation implements Serializable{
    private String url;
    private String type;
    private String[] mime;
    private String[] consumes;
    private String[] parameter;

    public Operation(String url, String type, String[] mime,String[] consumes, String... param) {
        this.url = url;
        this.type = type;
        this.parameter = param;
        this.mime = mime;
        this.consumes = consumes;
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

    public String[] getConsumes() {
        return consumes;
    }

    @Override
    public String toString() {
        return "Operation{" +
                "url='" + url + '\'' +
                ", type='" + type + '\'' +
                ", mime=" + Arrays.toString(mime) +
                ", consumes=" + Arrays.toString(consumes) +
                ", parameter=" + Arrays.toString(parameter) +
                '}';
    }
}
