package org.jacpfx.vertx.registry;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import org.jacpfx.common.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Service registry knows all service verticles, a verticle registers here and will be traced. The registry also notify the router to add/remove routes to the services.
 * Created by amo on 22.10.14.
 */
public class ServiceRegistry extends AbstractVerticle {

    /**
     * trenne Registry von der Heartbeat Funktionalität
     *
     * Bugfixing 1: error handler für WS wenn keine Route gefunden wurde
     * 2: Startmechanismus vom ServiceVerticle compleate erst aufrufen wenn Registry OK gegeben hat  start(Feature compleate) -> createServiceInfo() -> send(info, onFinish-> comleate.compleate())
     */
    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    private static final long DEFAULT_EXPIRATION_AGE = 5000;
    private static final long DEFAULT_TIMEOUT = 50000;
    private static final long DEFAULT_PING_TIME = 10000;
    private static final long DEFAULT_SWEEP_TIME = 0;
    public static final String HOST = getHostName();
    public static final int PORT = 8080;
    private static final String prefixWS = "ws://";
    private static final String prefixHTTP = "http://";

    private long expiration_age = DEFAULT_EXPIRATION_AGE;
    private long ping_time = DEFAULT_PING_TIME;
    private long sweep_time = DEFAULT_SWEEP_TIME;
    private long timeout_time = DEFAULT_TIMEOUT;
    private String mainURL;
    private String serviceRegisterPath;
    private String serviceUnRegisterPath;
    private String host;
    private int port;
    private final ServiceInfoDecoder serviceInfoDecoder = new ServiceInfoDecoder();

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "127.0.0.1";
        }
    }
    @Override
    public void start(Future<Void> startFuture) {
        log.info("Service registry started.");

        initConfiguration(getConfig());
        vertx.eventBus().registerDefaultCodec(ServiceInfo.class, serviceInfoDecoder);
        vertx.eventBus().consumer(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, this::serviceRegister);
        vertx.eventBus().consumer(GlobalKeyHolder.SERVICE_REGISTRY_GET, this::getServicesInfo);
        pingService();
        startFuture.complete();
    }

    private void initConfiguration(JsonObject config) {
        expiration_age = config.getLong("expiration", DEFAULT_EXPIRATION_AGE);
        ping_time = config.getLong("ping", DEFAULT_PING_TIME);
        sweep_time = config.getLong("sweep", DEFAULT_SWEEP_TIME);
        timeout_time = config.getLong("timeout", DEFAULT_TIMEOUT);
        serviceRegisterPath = config.getString("serviceRegisterPath", GlobalKeyHolder.SERVICE_REGISTER_HANDLER);
        serviceUnRegisterPath = config.getString("serviceUnRegisterPath", GlobalKeyHolder.SERVICE_UNREGISTER_HANDLER);
        host = config.getString("host", HOST);
        port = config.getInteger("port", PORT);
        mainURL = host.concat(":").concat(Integer.valueOf(port).toString());

    }

    private void getServicesInfo(Message<byte[]> message) {
        log.info("service info: " + message.body());
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                        getServiceHolderAndReplyToServiceInfoRequest(message, resultMap)
        ));
    }

    private void getServiceHolderAndReplyToServiceInfoRequest(Message<byte[]> message, AsyncMap<String, ServiceInfoHolder> resultMap) {
        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(resultHolder -> {
            if (resultHolder != null) {
                final ServiceInfoHolder serviceInfoHolder = buildServiceInfoForEntryPoint(resultHolder);
                message.reply(getServiceIfoHolderBinary(serviceInfoHolder));
            } else {
                message.reply(getServiceIfoHolderBinary(new ServiceInfoHolder()));
            }
        }));
    }

    private byte[] getServiceIfoHolderBinary(ServiceInfoHolder resultHolder) {
        try {
            return Serializer.serialize(resultHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[]{};
    }

    protected <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
        return result -> {
            if (result.failed()) {
                log.info("failed: " + result.cause());
                result.cause().printStackTrace();

            } else {
                log.info("ok : " + result.result());
                consumer.accept(result.result());
            }
        };
    }


    private void serviceRegister(final Message<byte[]> message) {
        final ServiceInfo info = deserializeServiceInfo(message);
        info.setLastConnection(getDateStamp());
        log.info("Register: " + message.body());
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap -> {
                    getServiceHolderAndRegister(message, info, resultMap);
                }
        ));

    }

    private void getServiceHolderAndRegister(Message<byte[]> message, ServiceInfo info, AsyncMap<String, ServiceInfoHolder> resultMap) {
        log.info("got map");
        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(resultHolder -> {
            log.info("got result holder");
            if (resultHolder != null) {
                addServiceEntry(resultMap, info, resultHolder, message);
            } else {
                createNewEntry(resultMap, info, new ServiceInfoHolder(), message);
            }
        }));
    }

    private ServiceInfo deserializeServiceInfo(final Message<byte[]> message) {
        try {
            return (ServiceInfo) Serializer.deserialize(message.body());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addServiceEntry(final AsyncMap resultMap, final ServiceInfo info, final ServiceInfoHolder holder, final Message<byte[]> message) {
        holder.add(info);
        log.info("update result holder");
        resultMap.replace(GlobalKeyHolder.SERVICE_HOLDER, holder, onSuccess(s -> {
            publishToEntryPoint(info);
            message.reply(true);
            log.info("Register REPLACE: " + info);
        }));
    }

    private void createNewEntry(final AsyncMap resultMap, final ServiceInfo info, final ServiceInfoHolder holder, final Message<byte[]> message) {
        holder.add(info);
        log.info("add result holder");
        resultMap.put(GlobalKeyHolder.SERVICE_HOLDER, holder, onSuccess(s -> {
            publishToEntryPoint(info);
            message.reply(true);
            log.info("Register ADD: " + info);
        }));
    }

    private void publishToEntryPoint(ServiceInfo info) {
        vertx.eventBus().publish(serviceRegisterPath, info);
    }


    private void pingService() {
        vertx.sharedData().getCounter("registry-timer-counter", onSuccess(counter -> counter.incrementAndGet(onSuccess(val -> {
            log.info("registry-timer-counter: " + val);
            if (val <= 1) {
                vertx.setPeriodic(ping_time, timerID -> {
                    log.info("ping_time: " + timerID);
                    getSharedRegistryAndPing();
                });
            }
        }))));

    }

    private void getSharedRegistryAndPing() {
        final SharedData sharedData = this.vertx.sharedData();
        sharedData.<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap -> {
                    log.info("resultMap " + resultMap);
                    getServiceHolderAndPingServices(resultMap);
                }
        ));
    }

    private void getServiceHolderAndPingServices(AsyncMap<String, ServiceInfoHolder> resultMap) {
        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder -> {
            log.info("get Holder " + holder + " this:" + this);
            if (holder != null) {
                final List<ServiceInfo> serviceHolders = holder.getAll();
                serviceHolders.forEach(this::pingService);

            }

        }));
    }

    private void pingService(final ServiceInfo info) {
        final String serviceName = info.getServiceName();
        vertx.
                eventBus().send(
                serviceName + "-info",
                "ping",
                new DeliveryOptions().setSendTimeout(timeout_time),
                (Handler<AsyncResult<Message<JsonObject>>>) event -> {
                    handlePingResult(info, serviceName, event);
                });
    }

    private void handlePingResult(ServiceInfo info, String serviceName, AsyncResult<Message<JsonObject>> event) {
        if (event.succeeded()) {
            info.setLastConnection(getDateStamp());
            updateServiceInfo(info);
            log.info("ping: " + serviceName);
        } else {
            log.info("ping error: " + serviceName);
            unregisterServiceAtRouter(info);
        }
    }

    private void updateServiceInfo(final ServiceInfo info) {
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder -> {
                            log.info("register service info " + info);
                            holder.replace(info);
                            resultMap.replace(GlobalKeyHolder.SERVICE_HOLDER, holder, onSuccess(s -> log.info("update services: ")));
                        }))
        ));


    }

    private void unregisterServiceAtRouter(final ServiceInfo info) {
        this.vertx.sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder ->
                                        removeAndUpdateServiceInfo(info, resultMap, holder)
                        ))
        ));


    }

    private void removeAndUpdateServiceInfo(ServiceInfo info, AsyncMap<String, ServiceInfoHolder> resultMap, ServiceInfoHolder holder) {
        holder.remove(info);
        resultMap.replace(GlobalKeyHolder.SERVICE_HOLDER, holder, t -> {
            if (t.succeeded()) {
                resetServiceCounterAndPublish(info);

            }
        });
    }

    private void resetServiceCounterAndPublish(ServiceInfo info) {
        vertx.sharedData().getCounter(info.getServiceName(), onSuccess(counter -> counter.get(onSuccess(c -> {
            counter.compareAndSet(c, 0, onSuccess(val ->
                            vertx.eventBus().publish(serviceUnRegisterPath, info)
            ));
        }))));
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
    }


    private String getDateStamp() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return timeFormat.format(Calendar.getInstance().getTime());
    }

    private ServiceInfoHolder buildServiceInfoForEntryPoint(ServiceInfoHolder message) {
        return new ServiceInfoHolder(message.getAll().stream().
                map(info->updateOperationUrl(prefixWS, prefixHTTP, mainURL, info, mainURL.concat(info.getServiceName()), host, port)).
                collect(Collectors.toList()));
    }


    private ServiceInfo updateOperationUrl(String prefixWS, String prefixHTTP, String mainURL, ServiceInfo infoObj, String serviceURL, String host, int port) {
        final List<Operation> mappedOperations = Stream.of(infoObj.getOperations()).map(operation -> {
            final String url = operation.getUrl();
            final Type type = Type.valueOf(operation.getType());
            switch (type) {
                case EVENTBUS:
                    return null;
                case WEBSOCKET:
                    return createOperation(infoObj.getServiceName(), prefixWS, mainURL, host, port, operation, url);
                default:
                    return createOperation(infoObj.getServiceName(), prefixHTTP, mainURL, host, port, operation, url);

            }
        }).collect(Collectors.toList());

        return infoObj.buildFromServiceInfo(serviceURL, mappedOperations.toArray(new Operation[mappedOperations.size()]));
    }

    private Operation createOperation(String serviceName, String prefix, String mainURL, String host, int port, Operation operation, String url) {
        return new Operation(operation.getName(),
                operation.getDescription(),
                prefix.concat(mainURL).concat(url),
                operation.getType(),
                operation.getProduces(),
                operation.getConsumes(),
                serviceName,
                host,
                port,
                null,// transient Vertx instance will be set on client side
                operation.getParameter());
    }


}
