package org.jacpfx.common;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by amo on 27.10.14.
 */
public class Operation implements Serializable{
    private String url;
    private String type;
    private String name;
    private String description;
    private String[] produces;
    private String[] consumes;
    private String[] parameter;

    public Operation(String url, String type, String[] produces,String[] consumes, String... param) {
       this(url,null,url,type, produces,consumes,param);
    }

    public Operation(String name, String description,String url, String type, String[] produces,String[] consumes, String... param) {
        this.name = name;
        this.description = description;
        this.url = url;
        this.type = type;
        this.parameter = param;
        this.produces = produces;
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

    public String[] getProduces() {
        return produces;
    }

    public String[] getConsumes() {
        return consumes;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Operation{" +
                "url='" + url + '\'' +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", produces=" + Arrays.toString(produces) +
                ", consumes=" + Arrays.toString(consumes) +
                ", parameter=" + Arrays.toString(parameter) +
                '}';
    }
}
