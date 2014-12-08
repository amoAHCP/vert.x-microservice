package org.jacpfx.vertx.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import org.jacpfx.common.JSONTool;
import org.jacpfx.common.OperationType;
import org.jacpfx.common.Parameter;
import org.jacpfx.common.TypeTool;

import javax.ws.rs.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extend a service verticle to provide pluggable sevices for vet.x microservice project
 * Created by amo on 28.10.14.
 */
public abstract class ServiceVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(ServiceVerticle.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private JsonObject descriptor;
    private static final String HOST_PREFIX = "";

    @Override
    public final void start() {
        // collect all service operations in service for descriptor
        descriptor = createInfoObject(getAllOperationsInService(this.getClass().getDeclaredMethods()));

        vertx.sharedData().getCounter(serviceName(),onSuccess(counter-> {
            counter.incrementAndGet(onSuccess(val-> {
                log.info(val);
                if(val<=1)
                    // register service at service registry
                    vertx.eventBus().send("services.registry.register", descriptor);
            }));
        }));

        // register info handler
        vertx.eventBus().consumer(serviceName() + "-info", this::info);
    }

    private JsonObject createInfoObject(List<JsonObject> operations) {
        final JsonObject tmp = new JsonObject();
        final JsonArray operationsArray = new JsonArray();
        operations.forEach(op -> operationsArray.add(op));
        tmp.put("serviceName", serviceName());
        tmp.put("operations", operationsArray);

        return tmp;
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
    private List<JsonObject> getAllOperationsInService(final Method[] allMethods) {
        return Stream.of(allMethods).
                filter(m -> m.isAnnotationPresent(Path.class)).
                map(method -> {
                    final Path path = method.getDeclaredAnnotation(Path.class);
                    final Produces mime = method.getDeclaredAnnotation(Produces.class);
                    final OperationType opType = method.getDeclaredAnnotation(OperationType.class);
                    if (opType == null)
                        throw new MissingResourceException("missing OperationType ", this.getClass().getName(), "");
                    final String[] mimeTypes = mime != null ? mime.value() : null;
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
                            break;
                        case EVENTBUS:
                            break;
                    }


                    return JSONTool.createOperationObject(url, opType.value().name(), mimeTypes, parameters.toArray(new String[parameters.size()]));
                }).collect(Collectors.toList());
    }

    /**
     * Retrieving a list of all possible REST parameters in method signature
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
        for (Annotation[] parameterAnnotation : parameterAnnotations) {
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
        for (Annotation[] parameterAnnotation : parameterAnnotations) {
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
    private Object[] invokePatameters(Message m, Method method) {
        final Parameter<String> params = gson.fromJson(m.body().toString(), Parameter.class);
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


    private void info(Message m) {

        m.reply(getServiceDescriptor());
        System.out.println("reply to: " + m.body());
    }


    public JsonObject getServiceDescriptor() {
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
}
