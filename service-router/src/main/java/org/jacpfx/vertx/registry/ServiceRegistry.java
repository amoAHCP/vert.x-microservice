package org.jacpfx.vertx.registry;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;

import java.util.HashMap;
import java.util.Map;

/**
 * The Service registry knows all service verticles, a verticle registers here and will be traced. The registry also notify the router to add/remove routes to the services.
 * Created by amo on 22.10.14.
 */
public class ServiceRegistry extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    private static final long DEFAULT_EXPIRATION_AGE = 5000;
    private static final long DEFAULT_TIMEOUT = 5000;
    private static final long DEFAULT_PING_TIME = 1000;
    private static final long DEFAULT_SWEEP_TIME = 0;
    // Our own addresses
    public static final String SERVICE_REGISTRY_EXPIRED = "services.registry.expired";
    public static final String SERVICE_REGISTRY_PING = "services.registry.ping";
    public static final String SERVICE_REGISTRY_SEARCH = "services.registry.search";
    private static final String SERVICE_REGISTRY_GET = "services.registry.get";
    public static final String SERVICE_REGISTRY_REGISTER = "services.registry.register";
    public static final String SERVICE_REGISTRY_ADD = "services.registry.add";
    public static final String SERVICE_REGISTRY_REMOVE = "services.registry.remove";
    public static final String SERVICE_REGISTRY = "services.registry";

    private Map<String, Long> handlers = new HashMap<>();

    private long expiration_age = DEFAULT_EXPIRATION_AGE;
    private long ping_time = DEFAULT_PING_TIME;
    private long sweep_time = DEFAULT_SWEEP_TIME;
    private long timeout_time = DEFAULT_TIMEOUT;


    @Override
    public void start() {
        vertx.sharedData().getClusterWideMap(SERVICE_REGISTRY, (Handler<AsyncResult<AsyncMap<String, Long>>>) event -> {
            // handlers = event.result();
        });
        log.info("Service registry started.");
        initConfiguration(getConfig());

        vertx.eventBus().consumer(SERVICE_REGISTRY_REGISTER, this::serviceRegister);
        vertx.eventBus().consumer(SERVICE_REGISTRY_ADD, this::serviceAdd);
        vertx.eventBus().consumer(SERVICE_REGISTRY_REMOVE, this::serviceRemove);
        vertx.eventBus().consumer(SERVICE_REGISTRY_GET, this::getServicesInfo);
        pingService();
    }

    private void initConfiguration(JsonObject config) {
        expiration_age = config.getLong("expiration", DEFAULT_EXPIRATION_AGE);
        ping_time = config.getLong("ping", DEFAULT_PING_TIME);
        sweep_time = config.getLong("sweep", DEFAULT_SWEEP_TIME);
        timeout_time = config.getLong("timeout", DEFAULT_TIMEOUT);

    }

    private void getServicesInfo(Message<JsonObject> message) {
        final JsonArray all = new JsonArray();
        handlers.keySet().forEach(handler -> all.add(new JsonObject(handler)));
        message.reply(new JsonObject().put("services", all));
    }


    private void serviceRegister(Message<JsonObject> message) {
        final String encoded = message.body().encode();
        if (!handlers.containsKey(encoded)) {
            handlers.put(encoded, System.currentTimeMillis());
            vertx.eventBus().publish(SERVICE_REGISTRY_ADD, message.body());
            vertx.eventBus().publish("services.register.handler", message.body());
            log.info("EventBus registered address: " + message.body());

        }

    }

    private void serviceAdd(Message<JsonObject> message) {
        final String encoded = message.body().encode();
        if (!handlers.containsKey(encoded)) {
            handlers.put(encoded, System.currentTimeMillis());
            log.info("EventBus add address: " + message.body());

        }

    }
    private void serviceRemove(Message<JsonObject> message) {
        final String encoded = message.body().encode();
        if (handlers.containsKey(encoded)) {
            handlers.remove(encoded);
            message.reply(true);
            log.info("EventBus remove address: " + message.body());

        }

    }

    private void pingService() {
        vertx.setPeriodic(ping_time, timerID -> {
            final long expired = System.currentTimeMillis() - expiration_age;

            handlers.
                    entrySet().
                    stream().
                    forEach(entry -> {
                        if ((entry.getValue() == null)
                                || (entry.getValue() < expired)) {
                            // vertx's SharedMap instances returns a copy internally, so we must remove by hand
                            final JsonObject info = new JsonObject(entry.getKey());
                            final String serviceName = info.getString("serviceName");

                            vertx.
                                    eventBus().send(
                                    serviceName + "-info",
                                    "ping",
                                    new DeliveryOptions().setSendTimeout(timeout_time),
                                    (Handler<AsyncResult<Message<JsonObject>>>) event -> {
                                        if (event.succeeded()) {
                                            log.info("ping: " + serviceName);
                                        } else {
                                            log.info("ping error: " + serviceName);
                                            unregisterServiceAtRouter(info);
                                        }
                                    });

                        }
                    });

        });
    }

    private void unregisterServiceAtRouter(final JsonObject info) {
        handlers.remove(info.encode());
        vertx.eventBus().publish(SERVICE_REGISTRY_REMOVE, info);
        vertx.eventBus().publish("services.unregister.handler", info);
    }

    private JsonObject getConfig() {
        return context != null ? context.config() : new JsonObject();
    }
}
