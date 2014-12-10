package org.jacpfx.common;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

import java.io.IOException;

/**
 * Created by Andy Moncsek on 09.12.14.
 */
public class ParameterDecoder implements MessageCodec<Parameter,Parameter> {
    @Override
    public void encodeToWire(Buffer buffer, Parameter parameter) {
        try {
            buffer.appendBytes(Serializer.serialize(parameter));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Parameter decodeFromWire(int pos, Buffer buffer) {
        try {
            return (Parameter) Serializer.deserialize(buffer.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Parameter transform(Parameter parameter) {
        return parameter;
    }

    @Override
    public String name() {
        return "parameter.decoder";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
