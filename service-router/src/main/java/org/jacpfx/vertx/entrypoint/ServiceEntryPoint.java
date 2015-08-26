package org.jacpfx.vertx.entrypoint;

import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.MetricsService;
import org.jacpfx.common.*;
import org.jacpfx.common.constants.GlobalKeyHolder;
import org.jacpfx.common.handler.RESTHandler;
import org.jacpfx.common.handler.WSClusterHandler;
import org.jacpfx.common.handler.WSLocalHandler;
import org.jacpfx.common.util.CustomRouteMatcher;
import org.jacpfx.common.util.WebSocketRepository;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Created by Andy Moncsek on 13.11.14.
 */
public class ServiceEntryPoint extends AbstractVerticle {

    private static final String HOST = getHostName();
    private static final int PORT = 8080;

    private static final String SERVICE_REGISTRY = "org.jacpfx.vertx.registry.ServiceRegistry";
    private static final String SERVICE_INFO_PATH = "/serviceInfo";
    private static final Logger log = LoggerFactory.getLogger(ServiceEntryPoint.class);




    private final Set<String> registeredRoutes = new HashSet<>();
    private final WebSocketRepository repository = new WebSocketRepository();
    private final ServiceInfoDecoder serviceInfoDecoder = new ServiceInfoDecoder();
    private final CustomRouteMatcher routeMatcher = new CustomRouteMatcher();
    private org.jacpfx.common.handler.WebSocketHandler wsHandler;
    private RESTHandler restHandler;

    private String serviceInfoPath;
    private String serviceRegisterPath;
    private String serviceUnRegisterPath;
    private String wsReplyPath,wsReplyToAllPath,wsReplyToAllButSenderPath;
    private String serviceRegistry;
    private String host;
    private int port;
    private String mainURL;
    private boolean clustered;
    private boolean debug;

    private int defaultServiceTimeout;

    private static String getHostName() {
        return "0.0.0.0";
    }


    @Override
    public void start(io.vertx.core.Future<Void> startFuture) throws Exception {
        log("START ServiceEntryPoint  THREAD: " + Thread.currentThread() + "  this:" + this);
        initConfiguration(getConfig());

        if (clustered) {
            wsHandler = new WSClusterHandler(this.vertx);
        } else {
            wsHandler = new WSLocalHandler(this.vertx);
        }

        // TODO make it configureable if REST should be privided
        restHandler = new RESTHandler(routeMatcher, defaultServiceTimeout, registeredRoutes);
        //vertx.eventBus().registerDefaultCodec(ServiceInfo.class, serviceInfoDecoder);
        //  vertx.eventBus().registerDefaultCodec(Parameter.class, parameterDecoder);


        vertx.eventBus().consumer(wsReplyPath, (Handler<Message<byte[]>>) wsHandler::replyToWSCaller);
        vertx.eventBus().consumer(wsReplyToAllPath, (Handler<Message<byte[]>>) wsHandler::replyToAllWS);
        // TODO vertx.eventBus().consumer(wsReplyToAllButSenderPath, (Handler<Message<byte[]>>) wsHandler::replyToAllWS);
        vertx.eventBus().consumer(serviceRegisterPath, this::serviceRegisterHandler);
        vertx.eventBus().consumer(serviceUnRegisterPath, this::serviceUnRegisterHandler);

        deployRegistry();

        initHTTPConnector();

        startFuture.complete();
    }

    private void deployRegistry() {
        DeploymentOptions options = new DeploymentOptions().setWorker(false).setConfig(vertx.getOrCreateContext().config().put("cluster", true));
        vertx.deployVerticle(serviceRegistry, options);
    }


    /**
     * start the server, attach the route matcher
     */
    private void initHTTPConnector() {
        HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost(host)
                .setPort(port));
        registerWebSocketHandler(server);
        // TODO provide a WebSocket and a EventBus access to ServiceInfo ... this must be routed through the Router to enrich the service info with metadata from the router
        routeMatcher.matchMethod(HttpMethod.GET, serviceInfoPath, request -> fetchRegitryAndUpdateMetadata((serviceInfo -> {
            request.response().putHeader("content-type", "text/json");
            request.response().end(serviceInfo.encodePrettily());
        })));
        routeMatcher.matchMethod(HttpMethod.GET,"/metrics",req -> {
            MetricsService metricsService = MetricsService.create(vertx);
            JsonObject metrics = metricsService.getMetricsSnapshot(vertx);
            req.response().putHeader("content-type", "text/json");
            req.response().end(metrics.encodePrettily());
        }) ;
        routeMatcher.noMatch(handler -> handler.response().end("no route found"));
        server.requestHandler(routeMatcher::accept).listen(res -> {

        });


    }

    private void initConfiguration(JsonObject config) {
        serviceInfoPath = config.getString("serviceInfoPath", SERVICE_INFO_PATH);
        serviceRegisterPath = config.getString("serviceRegisterPath", GlobalKeyHolder.SERVICE_REGISTER_HANDLER);
        serviceUnRegisterPath = config.getString("serviceUnRegisterPath", GlobalKeyHolder.SERVICE_UNREGISTER_HANDLER);
        wsReplyPath = config.getString("wsReplyPath", GlobalKeyHolder.WS_REPLY);
        wsReplyToAllPath = config.getString("wsReplyToAllPath", GlobalKeyHolder.WS_REPLY_TO_ALL);
        wsReplyToAllButSenderPath = config.getString("wsReplyToAllButSenderPath", GlobalKeyHolder.WS_REPLY_TO_ALL_BUT_ME);
        serviceRegistry = config.getString("serviceRegistry", SERVICE_REGISTRY);
        clustered = config.getBoolean("clustered", false);
        host = config.getString("host", HOST);
        port = config.getInteger("port", PORT);
        defaultServiceTimeout = config.getInteger("defaultServiceTimeout", GlobalKeyHolder.DEFAULT_SERVICE_TIMEOUT);
        mainURL = host.concat(":").concat(Integer.valueOf(port).toString());
        debug = config.getBoolean("debug",false);
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
            restHandler.removeRESTRoutes(url);
        });
    }


    /**
     * Register a service route
     *
     * @param message the eventbus message for registering the service
     */
    private void serviceRegisterHandler(Message<ServiceInfo> message) {
        final ServiceInfo info = message.body();
        if(info.getPort()<=0) {
            final EventBus eventBus = vertx.eventBus();
            Stream.of(info.getOperations()).forEach(operation -> {
                        final String type = operation.getType();
                        // TODO use "better" key than a simple relative url
                        final String url = operation.getUrl();
                        final String[] mimes = operation.getProduces() != null ? operation.getProduces() : new String[]{""};
                        // TODO specify timeout in service info object, so that every Service can specify his own timeout
                        // defaultServiceTimeout =   operation.getInteger("timeout");
                        if (!registeredRoutes.contains(url)) {
                            registeredRoutes.add(url);
                            handleServiceType(eventBus, type, url, mimes);
                        }
                    }
            );
        }

    }

    private void handleServiceType(EventBus eventBus, String type, String url, String[] mimes) {
        switch (Type.valueOf(type)) {
            case REST_GET:
                restHandler.handleRESTGetRegistration(eventBus, url, mimes);
                break;
            case REST_POST:
                restHandler.handleRESTPostRegistration(eventBus, url, mimes);
                break;
            case EVENTBUS:
                break;
            case WEBSOCKET:
                break;
            default:


        }
    }


    private void fetchRegitryAndUpdateMetadata(final Consumer<JsonObject> request) {
        vertx.eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_GET, "xyz", (AsyncResultHandler<Message<byte[]>>) serviceInfo ->
                {
                    // TODO move this to static factory
                    // TODO add TTL cache
                    // TODO this should work but it didn't vertx.executeBlocking((Handler<Future<JsonObject>> )future->buildServiceInfoForEntryPoint(serviceInfo),(updatedServiceInfo->request.accept(updatedServiceInfo.result())));
                    byte[] infoBytes = serviceInfo.result().body();
                    ServiceInfoHolder holder = null;
                    try {
                        holder = (ServiceInfoHolder) Serializer.deserialize(infoBytes);
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    if(holder!=null)request.accept(holder.getServiceInfo());

                }

        );
    }




    private void registerWebSocketHandler(HttpServer server) {
        server.websocketHandler((serverSocket) -> {
            if (serverSocket.path().equals("wsServiceInfo")) {
                // TODO implement serviceInfo request
                return;
            }
            logDebug("connect socket to path: " + serverSocket.path());
            serverSocket.pause();
            serverSocket.exceptionHandler(ex -> {
                //TODO
                ex.printStackTrace();
            });
            serverSocket.drainHandler(drain -> {
                //TODO
                log("drain");
            });
            serverSocket.endHandler(end -> {
                //TODO
                log("end");
            });
            serverSocket.closeHandler(close -> {
                wsHandler.findRouteSocketInRegistryAndRemove(serverSocket);
                log("close");
            });
            wsHandler.findRouteToWSServiceAndRegister(serverSocket);
        });
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
    }

    private void log(final String value) {
        log.info(value);
    }

    public static void main(String[] args) {
        VertxOptions vOpts = new VertxOptions();
        DeploymentOptions options = new DeploymentOptions().setInstances(4);
        vOpts.setClustered(true);
        vOpts.setMetricsOptions(new DropwizardMetricsOptions().setEnabled(true));
        Vertx.clusteredVertx(vOpts, cluster-> {
            if(cluster.succeeded()){
                final Vertx result = cluster.result();
                result.deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint",options, handle -> {

                });
            }
        });
    }

    private void logDebug(String message){
        if(debug) {
            log.debug(message);
        }
    }

}
