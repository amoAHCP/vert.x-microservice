package org.jacpfx.entities;

/**
 * Created by Andy Moncsek on 18.09.15.
 */
public class WSChatOperation {
    private final Type type;

    public WSChatOperation(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        CONNECT, MESSAGE_TO, MESSAGE_TO_ALL
    }
}
