package org.jacpfx.common.handler;

import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.VoidHandler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import org.jacpfx.common.Parameter;
import org.jacpfx.common.Serializer;
import org.jacpfx.common.TypeTool;
import org.jacpfx.common.util.CustomRouteMatcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by Andy Moncsek on 26.03.15.
 */
public class LocalRESTHandler {

    private final CustomRouteMatcher routeMatcher;
    private final int defaultServiceTimeout;
    private final Set<String> registeredRoutes;

    public LocalRESTHandler(CustomRouteMatcher routeMatcher, int defaultServiceTimeout, Set<String> registeredRoutes) {
        this.routeMatcher = routeMatcher;
        this.defaultServiceTimeout = defaultServiceTimeout;
        this.registeredRoutes = registeredRoutes;
    }

    public void handleRESTGetRegistration(final EventBus eventBus, final String url, final String[] mimes) {
        routeMatcher.matchMethod(HttpMethod.GET, url, request ->
                        handleRestRequest(eventBus,
                                request,
                                url,
                                getParameterEntity(request.params()),
                                Arrays.asList(mimes),
                                defaultServiceTimeout)
        );
    }

    public void handleRESTPostRegistration(final EventBus eventBus, final String url, final String[] mimes) {
        routeMatcher.matchMethod(HttpMethod.POST, url, request -> {
                    request.setExpectMultipart(true);
                    request.endHandler(new VoidHandler() {
                        public void handle() {
                            final MultiMap attrs = request.formAttributes();
                            handleRestRequest(eventBus,
                                    request,
                                    url,
                                    getParameterEntity(attrs),
                                    Arrays.asList(mimes),
                                    defaultServiceTimeout);
                        }
                    });
                }
        );
    }

    public void removeRESTRoutes(String url) {
        if (registeredRoutes.contains(url)) {
            routeMatcher.removeAll(url);
            registeredRoutes.remove(url);
        }
    }

    /**
     * handles REST requests
     *
     * @param eventBus   the vert.x event bus
     * @param request    the http request
     * @param url        the request URL
     * @param parameters the request parameters
     * @param mimes      the service mime types
     * @param timeout    the default timeout
     */
    private void handleRestRequest(final EventBus eventBus,
                                   HttpServerRequest request,
                                   final String url,
                                   final Parameter parameters,
                                   final List<String> mimes,
                                   final int timeout) {
        eventBus.
                send(
                        url,
                        getSerializedParameters(parameters),
                        new DeliveryOptions().setSendTimeout(timeout),
                        event -> createRestResponse(request, mimes, event));
    }


    private byte[] getSerializedParameters(final Parameter parameters) {
        byte[] parameter = new byte[0];
        try {
            parameter = Serializer.serialize(parameters);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return parameter;
    }


    private void createRestResponse(HttpServerRequest request, final List<String> mimes, AsyncResult<Message<Object>> event) {
        if (mimes != null && mimes.size() > 0) {
            final String accept = request.headers().get("Accept");
            if (accept != null) {
                final Optional<String> mime = mimes.stream().filter(mm -> mm.equalsIgnoreCase(accept)).findFirst();
                mime.ifPresent(m -> request.response().putHeader("content-type", m));

            } else {
                mimes.forEach(m -> request.response().putHeader("content-type", m));
            }
        }

        handleRESTEvent(event, request);
    }


    /**
     * handles REST events (POST,GET,...)
     *
     * @param event   the async event
     * @param request the HTTP request
     */
    private void handleRESTEvent(AsyncResult<Message<Object>> event, HttpServerRequest request) {
        if (event.succeeded()) {
            final Object result = event.result().body();
            if (result == null) request.response().end();
            final String stringResult = TypeTool.trySerializeToString(result);
            if (stringResult != null) {
                request.response().end(stringResult);
            } else {
                request.response().end();
            }

        } else {
            // TODO define configurable ERROR message
            request.response().end("error");
        }
    }

    private Parameter<String> getParameterEntity(final MultiMap params) {
        final List<Parameter<String>> parameters = params.
                entries().
                stream().
                map(entry -> new Parameter<>(entry.getKey(), entry.getValue())).
                collect(Collectors.toList());
        return new Parameter<>(parameters);
    }

}
