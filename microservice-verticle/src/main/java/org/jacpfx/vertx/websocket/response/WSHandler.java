package org.jacpfx.vertx.websocket.response;

import io.vertx.core.eventbus.EventBus;
import org.jacpfx.common.Serializer;
import org.jacpfx.common.WSEndpoint;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Created by Andy Moncsek on 17.11.15.
 */
public class WSHandler {
    private final static ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private final WSEndpoint endpoint;
    private final EventBus bus;

    public WSHandler(WSEndpoint endpoint, EventBus bus) {
        this.endpoint = endpoint;
        this.bus = bus;
    }

    public TargetType response() {
        return new TargetType(endpoint, bus, false);
    }


    public class TargetType {
        private final WSEndpoint endpoint;
        private final EventBus bus;
        private final boolean async;

        private TargetType(WSEndpoint endpoint, EventBus bus, boolean async) {
            this.endpoint = endpoint;
            this.bus = bus;
            this.async = async;
        }

        public TargetType async() {
            return new TargetType(endpoint, bus, true);
        }

        public ResponseType toAll() {
            return new ResponseType(endpoint, bus, async, CommType.ALL);
        }

        public ResponseType toAllButCaller() {
            return new ResponseType(endpoint, bus, async, CommType.ALL_BUT_CALLER);
        }

        public ResponseType toCaller() {
            return new ResponseType(endpoint, bus, async, CommType.CALLER);
        }


    }

    public class ResponseType {
        private final WSEndpoint endpoint;
        private final EventBus bus;
        private final boolean async;
        private final CommType commType;

        private ResponseType(WSEndpoint endpoint, EventBus bus, final boolean async, final CommType commType) {
            this.endpoint = endpoint;
            this.bus = bus;
            this.async = async;
            this.commType = commType;
        }

        public ExecuteWSResponse byteResponse(Supplier<byte[]> byteSupplier) {
            return new ExecuteWSResponse(endpoint, bus, async, commType, byteSupplier, null, null);
        }

        public ExecuteWSResponse stringResponse(Supplier<String> stringSupplier) {
            return new ExecuteWSResponse(endpoint, bus, async, commType, null, stringSupplier, null);
        }

        public ExecuteWSResponse objectResponse(Supplier<Serializable> objectSupplier) {
            return new ExecuteWSResponse(endpoint, bus, async, commType, null, null, objectSupplier);
        }
    }

    public class ExecuteWSResponse {
        private final WSEndpoint endpoint;
        private final EventBus bus;
        private final boolean async;
        private final CommType commType;
        private final Supplier<byte[]> byteSupplier;
        private final Supplier<String> stringSupplier;
        private final Supplier<Serializable> objectSupplier;

        private ExecuteWSResponse(WSEndpoint endpoint, EventBus bus, boolean async, CommType commType, Supplier<byte[]> byteSupplier, Supplier<String> stringSupplier, Supplier<Serializable> objectSupplier) {
            this.endpoint = endpoint;
            this.bus = bus;
            this.async = async;
            this.commType = commType;
            this.byteSupplier = byteSupplier;
            this.stringSupplier = stringSupplier;
            this.objectSupplier = objectSupplier;
        }

        public void execute() {
             if(async){

             } else {
                 if(byteSupplier!=null) {
                     // TODO check for exception, think about @OnError method execution
                     Optional.ofNullable(byteSupplier.get()).ifPresent(value-> {
                         sendBinary(value);
                     });
                 } else if(stringSupplier!=null){
                     // TODO check for exception, think about @OnError method execution
                     Optional.ofNullable(stringSupplier.get()).ifPresent(value-> {
                         sendText(value);
                     });
                 } else if(objectSupplier!=null){
                     // TODO check for exception, think about @OnError method execution
                     Optional.ofNullable(objectSupplier.get()).ifPresent(value-> {
                         try {
                             sendBinary(Serializer.serialize(value));
                         } catch (IOException e) {
                             e.printStackTrace();
                         }

                     });
                 }

             }
        }

        private void sendText(String value) {
            switch (commType){

                case ALL:
                    break;
                case ALL_BUT_CALLER:
                    break;
                case CALLER:
                    bus.send(endpoint.getTextHandlerId(),value);
                    break;
            }
        }

        private void sendBinary(byte[] value) {
            switch (commType){

                case ALL:
                    break;
                case ALL_BUT_CALLER:
                    break;
                case CALLER:
                    bus.send(endpoint.getBinaryHandlerId(),value);
                    break;
            }
        }
    }

    public enum CommType {
        ALL, ALL_BUT_CALLER, CALLER
    }
}
