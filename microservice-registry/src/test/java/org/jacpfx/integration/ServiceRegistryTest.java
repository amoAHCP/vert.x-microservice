package org.jacpfx.integration;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.*;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceRegistryTest extends VertxTestBase {
    private final static int MAX_RESPONSE_ELEMENTS = 4;
    public static final String SERVICE_REST_GET = "/wsService";
    public static final String TESTSERVICE1 = "/testservice1";

    protected int getNumNodes() {
        return 1;
    }

    protected Vertx getVertx() {
        return vertices[0];
    }

    @Override
    protected ClusterManager getClusterManager() {
        return new FakeClusterManager();
    }


    private HttpClient client;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        startNodes(getNumNodes());

    }

    @Before
    public void startVerticles() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hardecode it in your tests
        getVertx().deployVerticle("org.jacpfx.vertx.registry.ServiceRegistry", asyncResult -> {
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start entry point: " + asyncResult.succeeded());
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            latch.countDown();

        });
        awaitLatch(latch);


        client = getVertx().
                createHttpClient(new HttpClientOptions());

    }

    @Test
    public void testRegisterVerticle() throws InterruptedException, IOException {
        getVertx().eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, Serializer.serialize(getServiceInfoDesc(
                TESTSERVICE1)), onSuccess(result -> {
            assertEquals(true, result.body());
            getVertx().sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                            resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder ->
                                    {
                                        assertTrue(holder.getAll().size() >= 1);
                                        ServiceInfo info = holder.getAll().get(0);
                                        assertTrue(info.getServiceName().equals(TESTSERVICE1));
                                        System.out.printf("holder: " +info);
                                        testComplete();
                                    }
                            ))
            ));


        }));

        await();



    }

    @Test
    public void testCheckPingFromRegistry() throws InterruptedException, IOException {
        getVertx().eventBus().consumer(TESTSERVICE1+"-info", this::info);
        getVertx().eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, Serializer.serialize(getServiceInfoDesc(TESTSERVICE1)), onSuccess(reply -> {

            assertEquals(true, reply.body());


        }));
        await();
    }




    private void info(Message<String> m) {


        m.reply(getServiceInfoDesc(TESTSERVICE1));
        System.out.println("info message: "+m.body());
        assertEquals("ping",m.body());
        testComplete();
    }

   /** private void info2(Message m) {
        Logger logger = container.logger();

        m.reply(getServiceInfoDesc("/testservice2"));
        logger.info("reply to: " + m.body());
        assertEquals("ping", m.body());
    } */

    private ServiceInfo getServiceInfoDesc(String serviceName) {
        ServiceInfo info = new ServiceInfo(serviceName, getDummyOperations().toArray(new Operation[]{}));

        return info;
    }

    private List<Operation> getDummyOperations() {
        List<Operation> result = new ArrayList<>();

        result.add(new Operation("/operation1", Type.REST_GET.name(),new String[]{""},new String[]{""}, new String[]{"text"}));
        result.add(new Operation("/operation2", Type.REST_GET.name(),new String[]{""},new String[]{""}, new String[]{"text"}));
        result.add(new Operation("/operation3", Type.REST_GET.name(), new String[]{""},new String[]{""},new String[]{"text"}));
        result.add(new Operation("/operation4", Type.REST_GET.name(),new String[]{""},new String[]{""}, new String[]{"text"}));
        return result;
    }
}
