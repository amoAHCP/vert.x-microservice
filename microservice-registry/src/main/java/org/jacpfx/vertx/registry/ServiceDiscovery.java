package org.jacpfx.vertx.registry;

import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import org.jacpfx.common.*;

import javax.management.ServiceNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        vertx.eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_GET, "xyz", (AsyncResultHandler<Message<byte[]>>) h ->
                {
                    if (h.succeeded()) {
                        final List<ServiceInfo> serviceInfos = getServiceInfoFromMessage(h).filter(info -> criteria.apply(info)).collect(Collectors.toList());
                        if(!serviceInfos.isEmpty()){
                            consumer.accept(new ServiceInfoResult(serviceInfos.stream(),h.succeeded(),h.cause()));
                        }else {
                            consumer.accept(new ServiceInfoResult(serviceInfos.stream(),false,new ServiceNotFoundException("selected service not found")));
                        }
                    } else {
                        consumer.accept(new ServiceInfoResult(Stream.<ServiceInfo>empty(),h.succeeded(),h.cause()));
                    }

                }
        );

        return null;
    }

    private Stream<ServiceInfo> getServiceInfoFromMessage(AsyncResult<Message<byte[]>> h) {
        ServiceInfoHolder holder = new ServiceInfoHolder();
        try {
            holder = (ServiceInfoHolder) Serializer.deserialize(h.result().body());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return holder.getAll().stream().map(i->new ServiceInfo(i,vertx));
    }
}
