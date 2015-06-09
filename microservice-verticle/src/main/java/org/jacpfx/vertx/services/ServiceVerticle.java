package org.jacpfx.vertx.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import org.jacpfx.common.*;
import org.jacpfx.common.spi.GSonConverter;
import org.jacpfx.common.spi.JSONConverter;
import org.jacpfx.vertx.registry.ServiceDiscovery;

import javax.ws.rs.*;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extend a service verticle to provide pluggable sevices for vet.x microservice project
 * Created by amo on 28.10.14.
 */
public abstract class ServiceVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(ServiceVerticle.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ServiceInfo descriptor;
    private static final String HOST_PREFIX = "";
    protected ServiceDiscovery dicovery;

    @Override
    public final void start(final Future<Void> startFuture) {
        long startTime = System.currentTimeMillis();
        // collect all service operations in service for descriptor
        descriptor = createInfoObject(getAllOperationsInService(this.getClass().getDeclaredMethods()));
        // register info handler
        vertx.eventBus().consumer(serviceName() + "-info", this::info);
        registerService(startFuture);
        dicovery = ServiceDiscovery.getInstance(this.getVertx());
        long endTime = System.currentTimeMillis();
        log.info("start time: " + (endTime - startTime) + "ms");
    }

    private void registerService(final Future<Void> startFuture) {
        vertx.sharedData().getCounter(serviceName(), onSuccess(counter -> counter.incrementAndGet(onSuccess(val -> {
            if (val <= 1) {
                // register service at service registry
                try {
                    vertx.eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, Serializer.serialize(descriptor), handler -> {
                        log.info("Register Service: " + handler.succeeded());
                        startFuture.complete();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                startFuture.complete();
            }
        }))));
    }

    private ServiceInfo createInfoObject(List<Operation> operations) {
        return new ServiceInfo(serviceName(), null, getHostName(), null, null, operations.toArray(new Operation[operations.size()]));
    }

    private String getHostName() {
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
            case REST_POST:
                parameters.addAll(getAllRESTParameters(method));
                vertx.eventBus().consumer(url, handler -> genericRESTHandler(handler, method));
                break;
            case REST_GET:
                parameters.addAll(getAllRESTParameters(method));
                vertx.eventBus().consumer(url, handler -> genericRESTHandler(handler, method));
                break;
            case WEBSOCKET:
                parameters.addAll(getWSParameter(method));
                vertx.eventBus().consumer(url, (Handler<Message<byte[]>>) handler -> genericWSHandler(handler, method));
                break;
            case EVENTBUS:
                List<String> parameter = getEVENTBUSParameter(method);
                parameters.addAll(parameter);

                registerEventBusMethod(method, consumes, url, parameter);
                break;
        }
        // TODO add service description!!!
        return new Operation(path.value(), null, url, opType.value().name(), mimeTypes, consumeTypes, parameters.toArray(new String[parameters.size()]));
    }

    private void registerEventBusMethod(Method method, Consumes consumes, String url, List<String> parameter) {
        Class<?> clazzParameter = null;
        try {
            clazzParameter = parameter.isEmpty() ? null : Class.forName(parameter.get(0));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        if (isBinary(consumes)) {
            vertx.eventBus().consumer(url, (Handler<Message<byte[]>>) handler -> binaryEBHandler(handler, method));
        } else if (isJSON(consumes)) {
            vertx.eventBus().consumer(url, handler -> objectEBHandler(handler, method));
        } else if (clazzParameter != null && TypeTool.isCompatibleType(clazzParameter)) {
            vertx.eventBus().consumer(url, handler -> objectEBHandler(handler, method));
        }
    }


    /**
     * Retrieving a list of all possible REST parameters in method signature
     *
     * @param method the method to analyse
     * @return a List of all available parameters on method
     */
    private List<String> getAllRESTParameters(Method method) {
        final List<String> parameters = getQueryParametersInMethod(method.getParameterAnnotations());
        parameters.addAll(getPathParametersInMethod(method.getParameterAnnotations()));
        parameters.addAll(getFormParamParametersInMethod(method.getParameterAnnotations()));
        return parameters;

    }

    /**
     * Retrieving a list of all possible eventbus method paramaters
     *
     * @param method
     * @return a List of all available parameters on method
     */
    private List<String> getEVENTBUSParameter(Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        final List<Class> classes = Stream.of(parameterTypes).filter(c -> !c.equals(EBMessageReply.class)).collect(Collectors.toList());
        if (classes.size() > 1) throw new IllegalArgumentException("only one parameter is allowed");
        return classes.stream().map(Class::getName).collect(Collectors.toList());
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
        final List<Class> classes = Stream.of(parameterTypes).filter(c -> !c.equals(WSMessageReply.class)).collect(Collectors.toList());
        if (classes.size() > 1) throw new IllegalArgumentException("only one parameter is allowed");
        return classes.stream().map(Class::getName).collect(Collectors.toList());
    }

    /**
     * Returns all query parameters in a method, this is only for REST methods
     *
     * @param parameterAnnotations an array with all parameter annotations
     * @return a list of QueryParameters in a method
     */
    private List<String> getQueryParametersInMethod(final Annotation[][] parameterAnnotations) {
        final List<String> parameters = new ArrayList<>();
        for (final Annotation[] parameterAnnotation : parameterAnnotations) {
            parameters.addAll(Stream.of(parameterAnnotation).
                    filter(pa -> QueryParam.class.isAssignableFrom(pa.getClass())).
                    map(parameter -> QueryParam.class.cast(parameter).value()).
                    collect(Collectors.toList()));
        }
        return parameters;
    }

    /**
     * Returns all path parameters in a method, this is only for REST methods
     *
     * @param parameterAnnotations an array with all parameter annotations
     * @return a list of PathParameters in a method
     */
    private List<String> getPathParametersInMethod(final Annotation[][] parameterAnnotations) {
        final List<String> parameters = new ArrayList<>();
        for (final Annotation[] parameterAnnotation : parameterAnnotations) {
            parameters.addAll(Stream.of(parameterAnnotation).
                    filter(pa -> PathParam.class.isAssignableFrom(pa.getClass())).
                    map(parameter -> PathParam.class.cast(parameter).value()).
                    collect(Collectors.toList()));
        }
        return parameters;
    }

    /**
     * Returns all FormParam parameters in a method, this is only for REST methods
     *
     * @param parameterAnnotations an array with all parameter annotations
     * @return a list of PathParameters in a method
     */
    private List<String> getFormParamParametersInMethod(final Annotation[][] parameterAnnotations) {
        final List<String> parameters = new ArrayList<>();
        for (final Annotation[] parameterAnnotation : parameterAnnotations) {
            parameters.addAll(Stream.of(parameterAnnotation).
                    filter(pa -> FormParam.class.isAssignableFrom(pa.getClass())).
                    map(parameter -> FormParam.class.cast(parameter).value()).
                    collect(Collectors.toList()));
        }
        return parameters;
    }

    /**
     * executes a requested Service Method in ServiceVerticle
     *
     * @param m
     * @param method
     */
    private void genericRESTHandler(Message m, Method method) {
        try {
            final Object replyValue = method.invoke(this, invokePatameters(m, method));
            if (replyValue != null) {
                if (TypeTool.isCompatibleRESTReturnType(replyValue)) {
                    m.reply(replyValue);
                } else {
                    m.reply(serializeToJSON(replyValue));
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            m.fail(200, e.getMessage());
        } catch (InvocationTargetException e) {
            m.fail(200, e.getMessage());
        }
    }

    /**
     * executes a requested Service Method in ServiceVerticle
     *
     * @param handler
     * @param method
     */
    private void genericWSHandler(Message<byte[]> handler, Method method) {
        genericVoidMethodInvocation(handler, method, () -> invokeWSParameters(handler, method));
    }

    private void objectEBHandler(Message<Object> handler, Method method) {
        genericVoidMethodInvocation(handler, method, () -> invokeObjectEBParameters(handler, method));

    }

    private void binaryEBHandler(Message<byte[]> handler, Method method) {
        genericVoidMethodInvocation(handler, method, () -> invokeBinaryEBParameters(handler, method));
    }

    private void genericVoidMethodInvocation(Message handler, Method method, Supplier<Object[]> supplier) {
        try {
            method.invoke(this, supplier.get());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            handler.fail(200, e.getMessage());
        } catch (InvocationTargetException e) {
            handler.fail(200, e.getMessage());
        }
    }

    private Object[] invokeBinaryEBParameters(Message<byte[]> m, Method method) {
        byte[] tmp = m.body();
        final java.lang.reflect.Parameter[] parameters = method.getParameters();
        final Object[] parameterResult = new Object[parameters.length];
        final Consumes consumes = method.getDeclaredAnnotation(Consumes.class);
        int i = 0;
        for (java.lang.reflect.Parameter p : parameters) {
            if (p.getType().equals(EBMessageReply.class)) {
                final String consumesVal = consumes != null && consumes.value().length > 0 ? consumes.value()[0] : "";
                parameterResult[i] = new EBMessageReply(this.vertx.eventBus(), m, consumesVal, getConverter());
            } else {
                putTypedEBParameter(consumes, parameterResult, p, i, tmp);
            }

            i++;
        }

        return parameterResult;
    }

    private Object[] invokeObjectEBParameters(Message<Object> m, Method method) {
        final java.lang.reflect.Parameter[] parameters = method.getParameters();
        final Object[] parameterResult = new Object[parameters.length];
        final Consumes consumes = method.getDeclaredAnnotation(Consumes.class);
        int counter = 0;
        for (java.lang.reflect.Parameter p : parameters) {
            if (p.getType().equals(EBMessageReply.class)) {
                final String consumesVal = consumes != null && consumes.value().length > 0 ? consumes.value()[0] : "";
                parameterResult[counter] = new EBMessageReply(this.vertx.eventBus(), m, consumesVal, getConverter());
            } else {
                if (TypeTool.isCompatibleType(p.getType())) {
                    parameterResult[counter] = p.getType().cast(m.body());
                } else {
                    parameterResult[counter] = getConverter().convertToObject(String.valueOf(m.body()), p.getType());
                }
            }

            counter++;
        }

        return parameterResult;
    }

    private Object[] invokeWSParameters(Message<byte[]> m, Method method) {
        final WSDataWrapper wrapper = getWSDataWrapper(m);
        final java.lang.reflect.Parameter[] parameters = method.getParameters();
        final Object[] parameterResult = new Object[parameters.length];
        final Consumes consumes = method.getDeclaredAnnotation(Consumes.class);
        int i = 0;
        for (java.lang.reflect.Parameter p : parameters) {
            if (p.getType().equals(WSMessageReply.class)) {
                parameterResult[i] = new WSMessageReply(wrapper.getEndpoint(), this.vertx.eventBus());
            } else {
                putTypedWSParameter(consumes, parameterResult, p, i, wrapper.getData());
            }

            i++;
        }

        return parameterResult;
    }


    private WSDataWrapper getWSDataWrapper(Message<byte[]> m) {
        WSDataWrapper wrapper = null;
        try {
            wrapper = (WSDataWrapper) Serializer.deserialize(m.body());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return wrapper;
    }


    protected String serializeToJSON(final Object o) {
        return gson.toJson(o);
    }


    /**
     * checks method parameters an request parameters for method invocation
     *
     * @param m      the message
     * @param method the service method
     * @return an array with all valid method parameters
     */
    private Object[] invokePatameters(Message<byte[]> m, Method method) {
        final Parameter<String> params = getParameterObject(m);
        final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        final Class[] parameterTypes = method.getParameterTypes();
        final Object[] parameters = new Object[parameterAnnotations.length];

        int i = 0;
        for (final Annotation[] parameterAnnotation : parameterAnnotations) {
            if (parameterAnnotation.length > 0) {
                // check only first parameter annotation as only one is allowed
                final Annotation annotation = parameterAnnotation[0];
                putQueryParameter(parameters, i, annotation, params);
                putPathParameter(parameters, i, annotation, params);
                putFormParameter(parameters, i, annotation, params);
            } else {
                final Class typeClass = parameterTypes[i];
                if (typeClass.isAssignableFrom(m.getClass())) {
                    parameters[i] = m;
                }
            }
            i++;
        }
        return parameters;
    }


    private Parameter<String> getParameterObject(Message<byte[]> m) {
        Parameter<String> params = null;
        try {
            params = (Parameter<String>) Serializer.deserialize(m.body());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return params;
    }

    private void putQueryParameter(Object[] parameters, int counter, Annotation annotation, final Parameter<String> params) {
        if (QueryParam.class.isAssignableFrom(annotation.getClass())) {
            parameters[counter] = (params.getValue(QueryParam.class.cast(annotation).value()));
        }
    }

    private void putPathParameter(Object[] parameters, int counter, Annotation annotation, final Parameter<String> params) {
        if (PathParam.class.isAssignableFrom(annotation.getClass())) {
            parameters[counter] = (params.getValue(PathParam.class.cast(annotation).value()));
        }
    }

    private void putFormParameter(Object[] parameters, int counter, Annotation annotation, final Parameter<String> params) {
        if (FormParam.class.isAssignableFrom(annotation.getClass())) {
            parameters[counter] = (params.getValue(FormParam.class.cast(annotation).value()));
        }
    }

    private void putTypedEBParameter(final Consumes consumes, final Object[] parameterResult, final java.lang.reflect.Parameter p, final int counter, final byte[] myParameter) {
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
        if (this.getClass().isAnnotationPresent(ApplicationPath.class)) {
            final JsonObject config = getConfig();
            final String host = config.getString("host-prefix", HOST_PREFIX);
            final ApplicationPath path = this.getClass().getAnnotation(ApplicationPath.class);
            return host.length() > 1 ? "/".concat(host).concat("-").concat(path.value()) : path.value();
        }
        return null;
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
    }

    // TODO add versioning to service
    protected String getVersion() {
        return null;
    }

}
