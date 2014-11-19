package org.jacpfx.vertx.entrypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.jacpfx.common.JSONTool;
import org.jacpfx.common.Parameter;
import org.jacpfx.common.Type;
import org.jacpfx.common.TypeTool;
import org.jacpfx.vertx.util.CustomRouteMatcher;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceEntryPoint extends AbstractVerticle {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static final String SERVICE_REGISTER_HANDLER = "services.register.handler";
    public static final String SERVICE_UNREGISTER_HANDLER = "services.unregister.handler";
    public static final String HOST = "localhost";
    public static final int PORT = 8080;
    public static final int DEFAULT_SERVICE_TIMEOUT = 10000;
    private static final String SERVICE_INFO_PATH = "/serviceInfo";

    private final CustomRouteMatcher routeMatcher = new CustomRouteMatcher();
    private final Set<String> registeredRoutes = new HashSet<>();

    private String serviceInfoPath;
    private String serviceRegisterPath;
    private String serviceUnRegisterPath;
    private String host;
    private int port;
    private int defaultServiceTimeout;


    @Override
    public void start(Future<Void> startFuture) {
        System.out.println("START RestEntryVerticle  THREAD: " + Thread.currentThread() + "  this:" + this);

        initConfiguration(getConfig());

        vertx.eventBus().consumer(serviceRegisterPath, this::serviceRegisterHandler);
        vertx.eventBus().consumer(serviceUnRegisterPath, this::serviceUnRegisterHandler);

        vertx.deployVerticle("org.jacpfx.vertx.registry.ServiceRegistry");

        initHTTPConnector(startFuture);
    }

    /**
     * start the server, attach the route matcher
     */
    private void initHTTPConnector(Future<Void> startFuture) {
        final HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost(host)
                .setPort(port));
        routeMatcher.matchMethod(HttpMethod.GET, serviceInfoPath, this::registerInfoHandler);
        routeMatcher.noMatch(handler -> handler.response().end("no route found"));
        server.requestHandler(routeMatcher::accept).listen(res -> {
            // When the web server is listening we'll say that the start of this verticle is complete
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(res.cause());
            }
        });

    }

    private void initConfiguration(JsonObject config) {
        serviceInfoPath = config.getString("serviceInfoPath", SERVICE_INFO_PATH);
        serviceRegisterPath = config.getString("serviceRegisterPath", SERVICE_REGISTER_HANDLER);
        serviceUnRegisterPath = config.getString("serviceUnRegisterPath", SERVICE_UNREGISTER_HANDLER);
        host = config.getString("host", HOST);
        port = config.getInteger("port", PORT);
        defaultServiceTimeout = config.getInteger("defaultServiceTimeout", DEFAULT_SERVICE_TIMEOUT);
    }

    /**
     * Unregister service from route
     *
     * @param message the eventbus message for unregistering the service
     */
    private void serviceUnRegisterHandler(final Message<JsonObject> message) {
        final JsonObject info = message.body();
        JSONTool.getObjectListFromArray(info.getJsonArray("operations")).forEach(operation -> {
            final String url = operation.getString("url");
            if (registeredRoutes.contains(url)) {
                routeMatcher.removeAll(url);
                registeredRoutes.remove(url);
            }
        });
    }

    /**
     * Register a service route
     *
     * @param message the eventbus message for registering the service
     */
    private void serviceRegisterHandler(Message<JsonObject> message) {
        final JsonObject info = message.body();
        final EventBus eventBus = vertx.eventBus();
        JSONTool.getObjectListFromArray(info.getJsonArray("operations")).
                forEach(operation -> {
                            final String type = operation.getString("type");
                            final String url = operation.getString("url");
                            final JsonArray mimes = operation.getJsonArray("mime");
                            // TODO specify timeout in service info object, so that every Service can specify his own timeout
                            // defaultServiceTimeout =   operation.getInteger("timeout");
                            if (!registeredRoutes.contains(url)) {
                                registeredRoutes.add(url);
                                switch (Type.valueOf(type)) {
                                    case REST_GET:
                                        routeMatcher.matchMethod(HttpMethod.GET, url, request ->
                                                        handleRestRequest(eventBus,
                                                                request,
                                                                url,
                                                                gson.toJson(getParameterEntity(request.params())),
                                                                JSONTool.getObjectListFromArray(mimes),
                                                                defaultServiceTimeout)
                                        );
                                        break;
                                    case REST_POST:

                                        routeMatcher.matchMethod(HttpMethod.POST, url, request -> {
                                                    request.setExpectMultipart(true);
                                                    request.bodyHandler(body -> {
                                                        System.out.println(new String(body.getBytes()));
                                                        System.out.println(new String(body.getBytes()));
                                                    });
                                                    request.endHandler(new VoidHandler() {
                                                        public void handle() {
                                                            final MultiMap attrs = request.formAttributes();
                                                            handleRestRequest(eventBus,
                                                                    request,
                                                                    url,
                                                                    gson.toJson(getParameterEntity(attrs)),
                                                                    JSONTool.getObjectListFromArray(mimes),
                                                                    defaultServiceTimeout);
                                                            System.out.println(attrs);
                                                            // Do something with them
                                                        }
                                                    });
                                                }
                                        );
                                        break;
                                    case EVENTBUS:
                                        break;
                                    case WEBSOCKET:
                                        break;
                                    default:


                                }
                            }
                        }
                );

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
                                   final String parameters,
                                   final List<JsonObject> mimes,
                                   final int timeout) {
        eventBus.
                send(
                        url,
                        parameters,
                        new DeliveryOptions().setSendTimeout(timeout),
                        event -> {
                            if (mimes != null && mimes.size() > 0) {
                                final String accept = request.headers().get("Accept");
                                if (accept != null) {
                                    final Optional<JsonObject> mime = mimes.stream().filter(mm -> mm.getString("mime").equalsIgnoreCase(accept)).findFirst();
                                    mime.ifPresent(m -> request.response().putHeader("content-type", m.getString("mime")));

                                } else {
                                    mimes.forEach(m -> request.response().putHeader("content-type", m.getString("mime")));
                                }
                            }

                            handleRESTEvent(event, request);
                        });
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
            request.response().end("error");
        }
    }


    private void registerInfoHandler(HttpServerRequest request) {
        request.response().putHeader("content-type", "text/json");
        vertx.eventBus().send("services.registry.get", "xyz",(AsyncResultHandler<Message<JsonObject>>) h ->
                        request.response().end(h.result().body().encodePrettily())
        );
    }

    // TODO change to JsonObject
    private Parameter<String> getParameterEntity(final MultiMap params) {
        final List<Parameter<String>> parameters = params.
                entries().
                stream().
                map(entry -> new Parameter<>(entry.getKey(), entry.getValue())).
                collect(Collectors.toList());
        return new Parameter<>(parameters);
    }

    private void registerWebSocketHandler(HttpServer server) {
        /*server.websocketHandler((serverSocket) -> {
            final String path = serverSocket.path();
            switch (path) {
                case "/all":

                    System.out.println("Call");
                    serverSocket.dataHandler(data -> {
                        System.out.println("DataHandler");
                        serverSocket.writeTextFrame("hallo2");
                    });
                    break;
            }
        });*/
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
    }
}
