package org.jacpfx.vertx.entrypoint;

import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import org.jacpfx.common.*;
import org.jacpfx.vertx.util.CustomRouteMatcher;
import org.jacpfx.vertx.util.WebSocketRepository;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Andy Moncsek on 13.11.14.
 */
public class ServiceEntryPoint extends AbstractVerticle {
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
    private final ParameterDecoder parameterDecoder = new ParameterDecoder();

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
      //  vertx.eventBus().registerDefaultCodec(Parameter.class, parameterDecoder);
        initConfiguration(getConfig());

        vertx.eventBus().consumer("ws.redirect", (Handler<Message<byte[]>>) event -> redirect(event));
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
                                handleRESTGetRegistration(eventBus, url, mimes);
                                break;
                            case REST_POST:
                                handleRESTPostRegistration(eventBus, url, mimes);
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

    private void handleRESTGetRegistration(final EventBus eventBus, final String url, final String[] mimes) {
        routeMatcher.matchMethod(HttpMethod.GET, url, request ->
                        handleRestRequest(eventBus,
                                request,
                                url,
                                getParameterEntity(request.params()),
                                Arrays.asList(mimes),
                                defaultServiceTimeout)
        );
    }

    private void handleRESTPostRegistration(final EventBus eventBus, final String url, final String[] mimes) {
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

    private byte[] getSerializedParameters(final Parameter parameters) {
        byte[] parameter = new byte[0];
        try {
            parameter = Serializer.serialize(parameters);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return parameter;
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


    private void registerInfoHandler(HttpServerRequest request) {
        request.response().putHeader("content-type", "text/json");
        vertx.eventBus().send("services.registry.get", "xyz", (AsyncResultHandler<Message<JsonObject>>) h ->
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
            findRouteToWSServiceAndRegister(serverSocket);
        });
    }


    private void findRouteToWSServiceAndRegister(ServerWebSocket serverSocket) {

        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap("registry", onSuccess(resultMap ->
                        resultMap.get("serviceHolder", onSuccess(resultHolder -> findServiceEntryAndRegisterWS(serverSocket, resultHolder)))
        ));
    }

    private void findServiceEntryAndRegisterWS(final ServerWebSocket serverSocket, final ServiceInfoHolder resultHolder){
        if (resultHolder != null) {

            final String path = serverSocket.path();
            final Optional<Operation> operationResult = findServiceInfoEntry(resultHolder, path);
            operationResult.ifPresent(op ->
                createEndpointDefinitionAndRegister(serverSocket, path)
            );
        }
    }

    private Optional<Operation> findServiceInfoEntry(ServiceInfoHolder resultHolder, String path) {
        return resultHolder.
                getAll().
                stream().
                map(info -> Arrays.asList(info.getOperations())).
                flatMap(infos -> infos.stream()).
                filter(op -> op.getUrl().equalsIgnoreCase(path)).
                findFirst();
    }

    private void createEndpointDefinitionAndRegister(ServerWebSocket serverSocket,String path) {

        this.vertx.sharedData().<String, WSEndpointHolder>getClusterWideMap("wsRegistry", onSuccess(registryMap -> {
                    getEndpointHolderAndAdd(serverSocket, path, registryMap);
                }
        ));
    }

    private void getEndpointHolderAndAdd(ServerWebSocket serverSocket, String path, AsyncMap<String, WSEndpointHolder> registryMap) {
        registryMap.get("wsEndpointHolder", onSuccess(wsEndpointHolder -> {
            final String binaryHandlerId = serverSocket.binaryHandlerID();
            final String textHandlerId = serverSocket.textHandlerID();
            final EventBus eventBus = vertx.eventBus();
            final WSEndpoint endpoint = new WSEndpoint(binaryHandlerId, textHandlerId, path);
            if (wsEndpointHolder != null) {
                addDefinitionToRegistry(serverSocket, eventBus, path, endpoint, registryMap, wsEndpointHolder);
            } else {
                createEntryAndAddDefinition(serverSocket, eventBus, path, endpoint, registryMap);
            }
        }));
    }

    private void createEntryAndAddDefinition(ServerWebSocket serverSocket, EventBus eventBus, String path, WSEndpoint endpoint, AsyncMap<String, WSEndpointHolder> registryMap) {
        final WSEndpointHolder holder= new WSEndpointHolder();
        holder.add(endpoint);
        registryMap.put("wsEndpointHolder", holder, onSuccess(s ->{
                    System.out.println("OK ADD");
                    sendToWSService(serverSocket, eventBus, path, endpoint);
                }

        ));
    }

    private void addDefinitionToRegistry(ServerWebSocket serverSocket, EventBus eventBus, String path, WSEndpoint endpoint, AsyncMap<String, WSEndpointHolder> registryMap, WSEndpointHolder wsEndpointHolder) {
        final WSEndpointHolder holder= wsEndpointHolder;
        holder.add(endpoint);
        registryMap.replace("wsEndpointHolder", holder, onSuccess(s -> {
                    System.out.println("OK REPLACE");
                    sendToWSService(serverSocket, eventBus, path, endpoint);
                }  )
        );
    }

    private void sendToWSService(final ServerWebSocket serverSocket, final EventBus eventBus, final String path, final WSEndpoint endpoint) {
        serverSocket.handler(handler -> {
                    try {
                        eventBus.send(path, Serializer.serialize(new WSDataWrapper(endpoint, handler.getBytes())));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

        );

        //TODO set close handler!!
    }


    private void redirect(Message<byte[]> message) {
        try {
            System.out.println("REDIRECT: "+this);
            final WSMessageWrapper wrapper = (WSMessageWrapper) Serializer.deserialize(message.body());
            final String stringResult = TypeTool.trySerializeToString(wrapper.getBody());
            if (stringResult != null) {
                vertx.eventBus().send(wrapper.getEndpoint().getTextHandlerId(), stringResult);
            } else {
                vertx.eventBus().send(wrapper.getEndpoint().getBinaryHandlerId(), Serializer.serialize(wrapper.getBody()));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
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
