package org.jacpfx.integration;

import org.jacpfx.common.Type;
import org.jacpfx.vertx.registry.ServiceRegistry;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shareddata.ConcurrentSharedMap;
import org.vertx.testtools.TestVerticle;

import java.util.ArrayList;
import java.util.List;

import static org.vertx.testtools.VertxAssert.*;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceRegistryPingTest extends TestVerticle {

    @Override
    public void start() {
        // Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
        initialize();
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hardecode it in your tests
        container.deployVerticle("org.jacpfx.vertx.registry.ServiceRegistry",asyncResult ->{
                // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
                assertTrue(asyncResult.succeeded());
                assertNotNull("deploymentID should not be null", asyncResult.result());
                // If deployed correctly then start the tests!
                startTests();

        });
    }



    @Test
    public void testCheckPingFromRegistry() {
        vertx.eventBus().registerHandler("/testservice-info", this::info);
        vertx.eventBus().send(ServiceRegistry.SERVICE_REGISTRY_REGISTER, getServiceInfoDesc("/testservice"), (Handler<Message<Boolean>>)reply->{

            assertEquals(true, reply.body());

            final ConcurrentSharedMap<Object, Object> map = vertx.sharedData().getMap(ServiceRegistry.SERVICE_REGISTRY);
            assertTrue(map.size()==1);

        });
    }




    private void info(Message m) {
        Logger logger = container.logger();

        m.reply(getServiceInfoDesc("/testservice"));
        logger.info("reply to: " + m.body());
        assertEquals("ping",m.body());
        testComplete();
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
