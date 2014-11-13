package org.jacpfx.vertx.entrypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jacpfx.common.JSONTool;
import org.jacpfx.common.Parameter;
import org.jacpfx.common.Type;
import org.jacpfx.common.TypeTool;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceEntryPoint extends Verticle {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static final String SERVICE_REGISTER_HANDLER = "services.register.handler";
    public static final String SERVICE_UNREGISTER_HANDLER = "services.unregister.handler";
    private final CustomRouteMatcher routeMatcher = new CustomRouteMatcher();
    private final Set<String> registeredRoutes = new HashSet<>();

    /**
     * Unregister service from route
     * @param message
     */
    private void serviceUnRegisterHandler(final Message<JsonObject> message) {
        final JsonObject info = message.body();
        JSONTool.getObjectListFromArray(info.getArray("operations")).forEach(operation -> {
            final String url = operation.getString("url");
            if (registeredRoutes.contains(url)) {
                routeMatcher.removeAll(url);
                registeredRoutes.remove(url);
            }
        });
    }

    private void serviceRegisterHandler(Message<JsonObject> message) {
        final JsonObject info = message.body();
        final EventBus eventBus = vertx.eventBus();
        JSONTool.getObjectListFromArray(info.getArray("operations")).forEach(operation -> {

                    final String type = operation.getString("type");
                    final String url = operation.getString("url");
                    final JsonArray mimes = operation.getArray("mime");
                    if (!registeredRoutes.contains(url)) {
                        registeredRoutes.add(url);

                        switch (Type.valueOf(type)) {
                            case REST_GET:
                                routeMatcher.get(url, request -> {
                                    eventBus.
                                            sendWithTimeout(
                                                    url,
                                                    gson.toJson(getParameterEntity(request.params())),
                                                    10000,
                                                    event -> {
                                                        if (mimes != null && mimes.toList().size() > 0)
                                                            request.response().putHeader("content-type", JsonObject.class.cast(mimes.get(0)).encode());
                                                        handleRESTEvent(event, request);
                                                    });
                                });
                                break;
                            case REST_POST:
                                routeMatcher.post(url, request -> {
                                    eventBus.
                                            sendWithTimeout(
                                                    url,
                                                    gson.toJson(getParameterEntity(request.params())),
                                                    10000,
                                                    event -> {
                                                        if (mimes != null && mimes.toList().size() > 0)
                                                            request.response().putHeader("content-type", JsonObject.class.cast(mimes.get(0)).encode());
                                                        handleRESTEvent(event, request);
                                                    });
                                });
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

    @Override
    public void start() {
        System.out.println("START RestEntryVerticle  THREAD: " + Thread.currentThread() + "  this:" + this);

        vertx.eventBus().registerHandler(SERVICE_REGISTER_HANDLER, this::serviceRegisterHandler);
        vertx.eventBus().registerHandler(SERVICE_UNREGISTER_HANDLER, this::serviceUnRegisterHandler);

        HttpServer server = vertx.createHttpServer();
        routeMatcher.get("/serviceInfo", this::registerInfoHandler);
        routeMatcher.noMatch(handler -> handler.response().end("no route found"));
        server.requestHandler(routeMatcher).listen(8080, "localhost");

        this.container.deployVerticle("org.jacpfx.vertx.registry.ServiceRegistry");
    }

    private void registerInfoHandler(HttpServerRequest request) {
        request.response().putHeader("content-type", "text/json");
        vertx.eventBus().send("services.registry.get", "xyz", (Handler<Message<JsonObject>>) h -> {
            request.response().end(h.body().encodePrettily());
        });
    }

    private Parameter<String> getParameterEntity(final MultiMap params) {
        final List<Parameter<String>> parameters = params.
                entries().
                stream().
                map(entry -> new Parameter<>(entry.getKey(), entry.getValue())).
                collect(Collectors.toList());
        return new Parameter<>(parameters);
    }

    private void registerWebSocketHandler(HttpServer server) {
        server.websocketHandler((serverSocket) -> {
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
        });
    }
}
