package org.jacpfx.vertx.registry;

import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.jacpfx.common.GlobalKeyHolder;
import org.jacpfx.common.ServiceInfo;
import org.jacpfx.common.ServiceInfoResult;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * This Client/server API allows you to find services and to operate with them
 * Created by Andy Moncsek on 05.05.15.
 */
public class ServiceDiscovery {

    private final Vertx vertx;
    private final String restURL;

    public ServiceDiscovery(Vertx vertx, String restURL) {
        this.vertx = vertx;
        this.restURL = restURL;
    }

    // TODO add TTL parameter
    public static ServiceDiscovery getInstance(Vertx vertx) {
        return new ServiceDiscovery(vertx, null);
    }
    // TODO add TTL parameter
    public static ServiceDiscovery getInstance(String restURL) {
        return new ServiceDiscovery(null, restURL);
    }

    public ServiceDiscovery getService(String serviceName, Consumer<ServiceInfoResult> consumer) {
        if(vertx!=null){
            getServiceInfoByVertx(consumer,(serviceInfo)->serviceInfo.getServiceName().equalsIgnoreCase(serviceName));
        } //TODO add http connection
        return new ServiceDiscovery(vertx, restURL);
    }

    public ServiceDiscovery getServicesByHostName(String hostName, Consumer<ServiceInfoResult> consumer) {
        if(vertx!=null){
            getServiceInfoByVertx(consumer,(serviceInfo)->serviceInfo.getHostName().equalsIgnoreCase(hostName));
        }
        return new ServiceDiscovery(vertx, restURL);
    }

    private ServiceInfo getServiceInfoByVertx(Consumer<ServiceInfoResult> consumer, Function<ServiceInfo,Boolean> criteria) {
        // TODO add caching mechanism with TTL to reduce
        vertx.eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_GET, "xyz", (AsyncResultHandler<Message<JsonObject>>) h ->
                {
                    if (h.succeeded()) {
                        final Stream<ServiceInfo> serviceInfos = getServiceInfoFromMessage(h);
                        consumer.accept(new ServiceInfoResult(serviceInfos.filter(info->criteria.apply(info)),h.succeeded(),h.cause()));
                    } else {
                        consumer.accept(new ServiceInfoResult(Stream.<ServiceInfo>empty(),h.succeeded(),h.cause()));
                    }

                }
        );

        return null;
    }

    private Stream<ServiceInfo> getServiceInfoFromMessage(AsyncResult<Message<JsonObject>> h) {
        Message<JsonObject> message = h.result();
        final JsonArray servicesArray = message.body().getJsonArray("services");
        return servicesArray.stream().map(obj -> (JsonObject) obj).map(jsonInfo -> ServiceInfo.buildFromJson(jsonInfo));
    }
}
