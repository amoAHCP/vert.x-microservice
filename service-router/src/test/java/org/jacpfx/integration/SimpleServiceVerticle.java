package org.jacpfx.integration;

import org.jacpfx.common.Type;
import org.jacpfx.vertx.registry.ServiceRegistry;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import java.util.ArrayList;
import java.util.List;

import static org.vertx.testtools.VertxAssert.assertEquals;

/**
 * Created by amo on 13.11.14.
 */
public class SimpleServiceVerticle extends Verticle {

    @Override
    public void start() {
        vertx.eventBus().registerHandler("/testservice2-info", this::info2);
        vertx.eventBus().send(ServiceRegistry.SERVICE_REGISTRY_REGISTER ,getServiceInfoDesc("/testservice2"));
    }
    @Override
    public void  stop() {
        vertx.eventBus().unregisterHandler("/testservice2-info", this::stop);
    }

    private void stop(Message m) {

    }

    private void info2(Message m) {
        Logger logger = container.logger();

        m.reply(getServiceInfoDesc("/testservice2"));
        logger.info("reply to: " + m.body());
        assertEquals("ping", m.body());
    }

    private JsonObject getServiceInfoDesc(String serviceName) {
        JsonObject info = new JsonObject();
        final JsonArray operationsArray = new JsonArray();
        getDummyOperations().forEach(op -> operationsArray.addObject(op));
        info.putString("serviceName", serviceName);
        info.putArray("operations", operationsArray);

        return info;
    }

    private List<JsonObject> getDummyOperations() {
        List<JsonObject> result = new ArrayList<>();

        result.add(org.jacpfx.common.JSONTool.createOperationObject("/operation1", Type.REST_GET.name(),new String[]{"text"}));
        result.add(org.jacpfx.common.JSONTool.createOperationObject("/operation2", Type.REST_GET.name(),new String[]{"text"}));
        result.add(org.jacpfx.common.JSONTool.createOperationObject("/operation3", Type.REST_GET.name(),new String[]{"text"}));
        result.add(org.jacpfx.common.JSONTool.createOperationObject("/operation4", Type.REST_GET.name(),new String[]{"text"}));
        return result;
    }
}
