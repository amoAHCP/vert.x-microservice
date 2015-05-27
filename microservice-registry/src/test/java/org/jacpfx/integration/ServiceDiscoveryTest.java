package org.jacpfx.integration;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.*;
import org.jacpfx.vertx.registry.ServiceDiscovery;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Created by Andy Moncsek on 07.05.15.
 */
public class ServiceDiscoveryTest extends VertxTestBase {
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
    public void testSimpleGetServiceByVetrx() throws IOException {
        getVertx().eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, Serializer.serialize(getServiceInfoDesc(
                TESTSERVICE1)), onSuccess(result -> {
            assertEquals(true, result.body());
            ServiceDiscovery.getInstance(this.getVertx()).service(TESTSERVICE1, (serviceResult) -> {
                assertEquals(true, serviceResult.succeeded());
                ServiceInfo si = serviceResult.getServiceInfo();
                assertEquals(true, si.getServiceName().equals(TESTSERVICE1));
                System.out.println("testSimpleGetServiceByVetrx finished");
                testComplete();
            });


        }));

        await();
    }

    @Test
    public void testSimpleGetServiceOperationByVetrx() throws IOException {
        getVertx().eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, Serializer.serialize(getServiceInfoDesc(
                TESTSERVICE1)), onSuccess(result -> {
            assertEquals(true, result.body());
            ServiceDiscovery.getInstance(this.getVertx()).service(TESTSERVICE1, (serviceResult) -> {
                assertEquals(true, serviceResult.succeeded());
                ServiceInfo si = serviceResult.getServiceInfo();
                assertEquals(true, si.getServiceName().equals(TESTSERVICE1));
                si.operation("/operation1", opResult -> {
                    assertEquals(true, opResult.succeeded());
                    assertEquals(true, opResult.getOperation().getName().equals("/operation1"));
                });
                testComplete();
                System.out.println("testSimpleGetServiceOperationByVetrx finished");
            });


        }));

        await();
    }

    @Test
    public void testSimpleGetServiceTypedOperationByVetrx() throws IOException {
        getVertx().eventBus().send(GlobalKeyHolder.SERVICE_REGISTRY_REGISTER, Serializer.serialize(getServiceInfoDesc(
                TESTSERVICE1)), onSuccess(result -> {
            assertEquals(true, result.body());
            ServiceDiscovery.getInstance(this.getVertx()).service(TESTSERVICE1, (serviceResult) -> {
                assertEquals(true, serviceResult.succeeded());
                ServiceInfo si = serviceResult.getServiceInfo();
                assertEquals(true, si.getServiceName().equals(TESTSERVICE1));
                assertEquals(true, si.getOperationsByType(Type.REST_GET).collect(Collectors.toList()).size() == 4);
                assertEquals(true, si.getOperationsByType(Type.WEBSOCKET).collect(Collectors.toList()).size() == 2);
                testComplete();
                System.out.println("testSimpleGetServiceTypedOperationByVetrx finished");

            });


        }));

        await();
    }


    private ServiceInfo getServiceInfoDesc(String serviceName) {
        ServiceInfo info = new ServiceInfo(serviceName, getDummyOperations().toArray(new Operation[]{}));

        return info;
    }

    private List<Operation> getDummyOperations() {
        List<Operation> result = new ArrayList<>();

        result.add(new Operation("/operation1", Type.REST_GET.name(), new String[]{""}, new String[]{""}, new String[]{"text"}));
        result.add(new Operation("/operation2", Type.REST_GET.name(), new String[]{""}, new String[]{""}, new String[]{"text"}));
        result.add(new Operation("/operation3", Type.REST_GET.name(), new String[]{""}, new String[]{""}, new String[]{"text"}));
        result.add(new Operation("/operation4", Type.REST_GET.name(), new String[]{""}, new String[]{""}, new String[]{"text"}));

        result.add(new Operation("/operation5", Type.WEBSOCKET.name(), new String[]{""}, new String[]{""}, new String[]{"text"}));
        result.add(new Operation("/operation6", Type.WEBSOCKET.name(), new String[]{""}, new String[]{""}, new String[]{"text"}));
        return result;
    }
}
