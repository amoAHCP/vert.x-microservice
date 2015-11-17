package org.jacpfx.vertx.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.jacpfx.common.*;
import org.jacpfx.common.spi.GSonConverter;
import org.jacpfx.common.spi.JSONConverter;
import org.jacpfx.vertx.registry.ServiceDiscovery;
import org.jacpfx.vertx.websocket.response.WSByteResponse;
import org.jacpfx.vertx.websocket.response.WSHandler;
import org.jacpfx.vertx.websocket.response.WSStringResponse;
import org.jacpfx.vertx.websocket.util.LocalWSRegistry;
import org.jacpfx.vertx.websocket.util.WSRegistry;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extend a service verticle to provide pluggable sevices for vet.x microservice project
 * Created by amo on 28.10.14.
 */
public abstract class VertxServiceEndpoint extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(VertxServiceEndpoint.class);
    private static final String HOST = getHostName();
    private String host;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ServiceInfo descriptor;
    private static final String HOST_PREFIX = "";
    protected ServiceDiscovery dicovery;
    private boolean clustered;
    private WSRegistry wsHandler;
    private int port = 0;


    @Override
    public final void start(final Future<Void> startFuture) {
        long startTime = System.currentTimeMillis();
        port = getEndpointPort();
        //router = Router.router(vertx);
        // collect all service operations in service for descriptor
        descriptor = createInfoObject(getAllOperationsInService(this.getClass().getDeclaredMethods()), port);
        // register info (keepAlive) handler
        vertx.eventBus().consumer(serviceName() + "-info", this::info);

        initSelfHostedService();

        long endTime = System.currentTimeMillis();
        log.info("start time: " + (endTime - startTime) + "ms");
        startFuture.complete();
    }

    private void initSelfHostedService() {
        if (port > 0) {
            updateConfiguration();

            clustered = getConfig().getBoolean("clustered", false);

            HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost(host)
                    .setPort(port));

            initWSHandlerInstance();
            registerWebSocketHandler(server);
            server.listen();
            System.out.println("..");
        }
    }


    private void initWSHandlerInstance() {
        if (clustered) {
            wsHandler = null;
        } else {
            wsHandler = new LocalWSRegistry(this.vertx);
        }
    }

    private void updateConfiguration() {
        getConfig().put("selfhosted", true);
        getConfig().put("selfhosted-host", serviceName());
    }


    private void logDebug(String message) {
        if (true) {
            log.debug(message);
        }
    }

    private void log(final String value) {
        log.info(value);
    }

    private void registerWebSocketHandler(HttpServer server) {
        server.websocketHandler((serverSocket) -> {
            if (serverSocket.path().equals("wsServiceInfo")) {
                // TODO implement serviceInfo request
                return;
            }
            logDebug("connect socket to path: " + serverSocket.path());
            final String path = serverSocket.path();
            final String sName = serviceName();
            if (path.startsWith(sName)) {
                serverSocket.pause();
                final String methodName = path.replace(sName, "");
                final Method[] declaredMethods = this.getClass().getDeclaredMethods();
                Stream.of(declaredMethods).parallel().
                        filter(method -> method.isAnnotationPresent(OperationType.class) && method.getAnnotation(OperationType.class).value().equals(Type.WEBSOCKET)).
                        filter(method1 -> method1.isAnnotationPresent(Path.class) && method1.getAnnotation(Path.class).value().equalsIgnoreCase(methodName)).
                        findFirst().
                        ifPresent(wsMethod -> {
                            if (wsHandler instanceof WSRegistry) {
                                // only for testing
                                WSRegistry.class.cast(wsHandler).registerAndExecute(serverSocket, endpoint -> {
                                    log("register:+ " + endpoint.getUrl());

                                    serverSocket.handler(handler -> {
                                                log("invoke endpoint " + endpoint.getUrl());
                                                final byte[] bytes = handler.getBytes();
                                                invokeWSMethod(bytes, wsMethod, endpoint);

                                            }
                                    );
                                    serverSocket.exceptionHandler(ex -> {
                                        //TODO  move definition to sendToWSService and notify method about the status
                                        ex.printStackTrace();
                                    });
                                    serverSocket.drainHandler(drain -> {
                                        //TODO  move definition to sendToWSService and notify method about the status
                                        log("drain");
                                    });
                                    serverSocket.endHandler(end -> {
                                        //TODO  move definition to sendToWSService and notify method about the status
                                        log("end");
                                    });
                                    serverSocket.closeHandler(close -> {
                                        // wsHandler.findRouteSocketInRegistryAndRemove(serverSocket);
                                        log("close");
                                    });

                                    serverSocket.resume();
                                });
                            }

                        });
            }

        });
    }

    private void invokeWSMethod(byte[] payload, Method method, WSEndpoint endpoint) {
        genericMethodInvocation(method, () -> invokeWSParameters(payload, method, endpoint));
    }

    private void genericMethodInvocation(Method method, Supplier<Object[]> supplier) {
        try {
            final Object returnValue = method.invoke(this, supplier.get());
            if (returnValue != null) {
                // TODO throw exception, no return value expected
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();

        } catch (InvocationTargetException e) {

        }
    }


    private Object[] invokeWSParameters(byte[] m, Method method, WSEndpoint endpoint) {
        final java.lang.reflect.Parameter[] parameters = method.getParameters();
        final Object[] parameterResult = new Object[parameters.length];
        final Consumes consumes = method.getDeclaredAnnotation(Consumes.class);
        int i = 0;

        for (java.lang.reflect.Parameter p : parameters) {
            // TODO remove all but WSHandler and else
            if (p.getType().equals(WSResponse.class)) {
                parameterResult[i] = new WSResponse(endpoint, this.vertx.eventBus(), this.getConfig());
            }
            if (p.getType().equals(WSByteResponse.class)) {
                parameterResult[i] = new WSByteResponse(endpoint, this.vertx.eventBus());
            }
            if (p.getType().equals(WSStringResponse.class)) {
                parameterResult[i] = new WSStringResponse(endpoint, this.vertx.eventBus());
            }
            if (p.getType().equals(WSHandler.class)) {
                parameterResult[i] = new WSHandler((WSRegistry) wsHandler,endpoint, this.vertx.eventBus());
            } else {
                putTypedWSParameter(consumes, parameterResult, p, i, m);
            }

            i++;
        }

        return parameterResult;
    }


    private ServiceInfo createInfoObject(List<Operation> operations, Integer port) {
        return new ServiceInfo(serviceName(), null, getHostName(), null, null, port, operations.toArray(new Operation[operations.size()]));
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "127.0.0.1";
        }
    }

    protected <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
        return result -> {
            if (result.failed()) {
                result.cause().printStackTrace();

            } else {
                consumer.accept(result.result());
            }
        };
    }

    /**
     * Scans all method in ServiceVerticle, checks method signature, registers each path and create for each method a operation objects for service information.
     *
     * @param allMethods methods in serviceVerticle
     * @return a list of all operation in service
     */
    private List<Operation> getAllOperationsInService(final Method[] allMethods) {
        return Stream.of(allMethods).parallel().
                filter(m -> m.isAnnotationPresent(Path.class)).
                map(this::mapServiceMethod).collect(Collectors.toList());
    }

    private Operation mapServiceMethod(Method method) {
        final Path path = method.getDeclaredAnnotation(Path.class);
        final Produces produces = method.getDeclaredAnnotation(Produces.class);
        final Consumes consumes = method.getDeclaredAnnotation(Consumes.class);
        final OperationType opType = method.getDeclaredAnnotation(OperationType.class);
        if (opType == null)
            throw new MissingResourceException("missing OperationType ", this.getClass().getName(), "");
        final String[] mimeTypes = produces != null ? produces.value() : null;
        final String[] consumeTypes = consumes != null ? consumes.value() : null;
        final String url = serviceName().concat(path.value());
        final List<String> parameters = new ArrayList<>();

        switch (opType.value()) {

            case WEBSOCKET:
                parameters.addAll(getWSParameter(method));
                break;
        }
        // TODO add service description!!!
        return new Operation(path.value(), null, url, opType.value().name(), mimeTypes, consumeTypes, parameters.toArray(new String[parameters.size()]));
    }


    /**
     * Retrieving a list (note only one parameter is allowed) of all possible ws method paramaters
     *
     * @param method
     * @return a List of all available parameters on method
     */
    private List<String> getWSParameter(Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        // TODO, instead of returning the class names of the parameter return a json representation if methods @Consumes annotation defines application/json. Be aware of String, Integer....
        final List<Class> classes = Stream.of(parameterTypes).
                filter(c -> !c.equals(WSResponse.class)).
                filter(c1 -> !c1.equals(WSByteResponse.class)).
                filter(c2 -> !c2.equals(WSStringResponse.class)).
                filter(c3 -> !c3.equals(WSHandler.class)).  // TODO this should be the only one
                collect(Collectors.toList());
        if (classes.size() > 1)
            throw new IllegalArgumentException("only one parameter is allowed -- the message body -- and/or the WSResponse");
        return classes.stream().map(Class::getName).collect(Collectors.toList());
    }


    private void putTypedWSParameter(final Consumes consumes, final Object[] parameterResult, final java.lang.reflect.Parameter p, final int counter, final byte[] myParameter) {
        if (p.getType().equals(String.class)) {
            parameterResult[counter] = new String(myParameter);
        } else {
            try {
                // TODO analyze @Consumes annotation, check for String Integer, or simply cast
                if (isBinary(consumes)) {
                    handleBinaryWSParameter(parameterResult, p, counter, myParameter);
                } else if (isJSON(consumes)) {
                    handleJSONWSParameter(parameterResult, p, counter, myParameter);
                } else {
                    // check for application/octet-stream or application/json
                    handleBinaryWSParameter(parameterResult, p, counter, myParameter);
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }


    }

    private void handleJSONWSParameter(Object[] parameterResult, java.lang.reflect.Parameter p, int counter, byte[] myParameter) {
        final String jsonString = new String(myParameter);
        if (p.getType().equals(String.class)) {
            parameterResult[counter] = jsonString;
        } else {
            parameterResult[counter] = getConverter().convertToObject(jsonString, p.getType());
        }
    }

    private void handleBinaryWSParameter(Object[] parameterResult, java.lang.reflect.Parameter p, int counter, byte[] myParameter) throws IOException, ClassNotFoundException {
        Object o = Serializer.deserialize(myParameter);
        parameterResult[counter] = p.getType().cast(o);
    }

    private JSONConverter getConverter() {
        // TODO privide impl. by SPI
        return new GSonConverter();
    }

    private boolean isBinary(final Consumes consumes) {
        if (consumes == null || consumes.value().length == 0) return false;
        Optional<String> result = Stream.of(consumes.value()).filter(val -> val.equalsIgnoreCase("application/octet-stream")).findFirst();
        return result.isPresent();
    }

    private boolean isJSON(final Consumes consumes) {
        if (consumes == null || consumes.value().length == 0) return false;
        Optional<String> result = Stream.of(consumes.value()).filter(val -> val.equalsIgnoreCase("application/json")).findFirst();
        return result.isPresent();
    }


    private void info(Message m) {

        try {
            m.reply(Serializer.serialize(getServiceDescriptor()), new DeliveryOptions().setSendTimeout(10000));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public ServiceInfo getServiceDescriptor() {
        return this.descriptor;
    }

    protected String serviceName() {
        if (this.getClass().isAnnotationPresent(org.jacpfx.common.ServiceEndpoint.class)) {
            final JsonObject config = getConfig();
            final String host = config.getString("host-prefix", HOST_PREFIX);
            final org.jacpfx.common.ServiceEndpoint path = this.getClass().getAnnotation(org.jacpfx.common.ServiceEndpoint.class);
            return host.length() > 1 ? "/".concat(host).concat("-").concat(path.value()) : path.value();
        } else {
            // TODO define Exception !!!
        }
        return null;
    }


    private Integer getEndpointPort() {
        if (this.getClass().isAnnotationPresent(org.jacpfx.common.ServiceEndpoint.class)) {
            final JsonObject config = getConfig();
            org.jacpfx.common.ServiceEndpoint selfHosted = this.getClass().getAnnotation(org.jacpfx.common.ServiceEndpoint.class);
            host = config.getString("host", HOST);
            return config.getInteger("port", selfHosted.port());
        } else {
            // TODO define Exception !!!
        }
        return 0;
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
    }


    // TODO add versioning to service
    protected String getVersion() {
        return null;
    }

}
