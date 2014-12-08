package org.jacpfx.vertx.entrypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.json.JsonObject;
import org.jacpfx.common.*;
import org.jacpfx.vertx.util.CustomRouteMatcher;
import org.jacpfx.vertx.util.WebSocketRepository;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Andy Moncsek on 13.11.14.
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
    private final WebSocketRepository repository = new WebSocketRepository();
    private final ServiceInfoDecoder serviceInfoDecoder = new ServiceInfoDecoder();

    private String serviceInfoPath;
    private String serviceRegisterPath;
    private String serviceUnRegisterPath;
    private String host;
    private int port;
    private int defaultServiceTimeout;


    @Override
    public void start(Future<Void> startFuture) {
        System.out.println("START RestEntryVerticle  THREAD: " + Thread.currentThread() + "  this:" + this);
        vertx.eventBus().registerDefaultCodec(ServiceInfo.class, serviceInfoDecoder);
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
        registerWebSocketHandler(server);
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
    private void serviceUnRegisterHandler(final Message<ServiceInfo> message) {
        final ServiceInfo info = message.body();
        Stream.of(info.getOperations()).forEach(operation -> {
            final String url = operation.getUrl();
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
    private void serviceRegisterHandler(Message<ServiceInfo> message) {
        final ServiceInfo info = message.body();
        final EventBus eventBus = vertx.eventBus();
        Stream.of(info.getOperations()).forEach(operation -> {
                    final String type = operation.getType();
                    final String url = operation.getUrl();
                    final String[] mimes = operation.getMime();
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
                                                        Arrays.asList(mimes),
                                                        defaultServiceTimeout)
                                );
                                break;
                            case REST_POST:

                                routeMatcher.matchMethod(HttpMethod.POST, url, request -> {
                                            request.setExpectMultipart(true);
                                            request.endHandler(new VoidHandler() {
                                                public void handle() {
                                                    final MultiMap attrs = request.formAttributes();
                                                    handleRestRequest(eventBus,
                                                            request,
                                                            url,
                                                            gson.toJson(getParameterEntity(attrs)),
                                                            Arrays.asList(mimes),
                                                            defaultServiceTimeout);
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
                                   final List<String> mimes,
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
                                    final Optional<String> mime = mimes.stream().filter(mm -> mm.equalsIgnoreCase(accept)).findFirst();
                                    mime.ifPresent(m -> request.response().putHeader("content-type", m));

                                } else {
                                    mimes.forEach(m -> request.response().putHeader("content-type", m));
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
            final EventBus eventBus = vertx.eventBus();
            switch (path) {
                // TODO maintain list of path entries
                case "/all":

                    System.out.println("Call");
                    serverSocket.handler(data -> {

                        eventBus.send(path,"message", new DeliveryOptions().setSendTimeout(3000),message -> handleWSEvent(message,serverSocket));


                        System.out.println("DataHandler");
                        serverSocket.writeFrame(new WebSocketFrameImpl("sddsfs"));
                    });
                    break;
            }
        });
    }


    /**
     * Registers onMessage and onClose message handler for WebSockets
     *
     * @param httpServer
     */
    private void registerWebsocketHandler(final HttpServer httpServer) {
        httpServer.websocketHandler((serverSocket) -> {
            repository.addWebSocket(serverSocket);
            List<JsonObject> allInfos = new ArrayList<>();
            serverSocket.handler(socket-> {
                   /* allInfos.forEach( e->e

                    );*/

            }) ;
            // TODO 1.) get a List of all services (PROBLEM: get list from all instances.. use shared map) 2.) iterate over each service/servicepath, register a datahandler and a send
           // serverSocket.dataHandler(this::redirectWSMessageToBus);
            //serverSocket.closeHandler((close) -> handleConnectionClose(close, serverSocket));
        });
    }

    private void serviceRegisterWSHandler(final JsonObject info) {



    }

    /**
     * handles REST events (POST,GET,...)
     *
     * @param event   the async event
     * @param ws the ServerWebSocket
     */
    private void handleWSEvent(AsyncResult<Message<Object>> event, ServerWebSocket ws) {
        if (event.succeeded()) {
            final Object result = event.result().body();
            // TODO check if result should be send back to caller, all, or all but caller
            final String stringResult = TypeTool.trySerializeToString(result);
            if (stringResult != null) {
                ws.writeFrame(new WebSocketFrameImpl(stringResult));
            }

        }
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
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
}
