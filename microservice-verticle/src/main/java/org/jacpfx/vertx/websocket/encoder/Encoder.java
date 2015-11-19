package org.jacpfx.vertx.websocket.encoder;

/**
 * Created by Andy Moncsek on 17.11.15.
 */
public interface Encoder {

    interface ByteEncoder<I> extends Encoder{
        byte[] decode(I input);
    }

    interface StringEncoder<I> extends Encoder{
        String decode(I input);
    }
}
