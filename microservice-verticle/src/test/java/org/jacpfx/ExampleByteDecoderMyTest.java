package org.jacpfx;

import org.jacpfx.common.Serializer;
import org.jacpfx.vertx.websocket.decoder.Decoder;

import java.io.IOException;
import java.util.Optional;

/**
 * Created by Andy Moncsek on 18.11.15.
 */
public class ExampleByteDecoderMyTest implements Decoder.ByteDecoder<MyTestObject> {
    @Override
    public Optional<MyTestObject> decode(byte[] input) {
        try {
            return Optional.ofNullable((MyTestObject)Serializer.deserialize(input));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
