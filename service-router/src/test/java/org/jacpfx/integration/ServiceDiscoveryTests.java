package org.jacpfx.integration;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.*;
import org.jacpfx.vertx.registry.ServiceDiscovery;
import org.jacpfx.vertx.services.ServiceVerticle;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * Created by Andy Moncsek on 07.05.15.
 */
public class ServiceDiscoveryTests extends VertxTestBase {
    private final static int MAX_RESPONSE_ELEMENTS = 4;
    public static final String SERVICE_REST_GET = "/wsService";
    public static final String SERVICE_REST_GET2 = "/restService";
    private static final String HOST="localhost";

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
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        DeploymentOptions options = new DeploymentOptions().setInstances(1);
        options.setConfig(new JsonObject().put("clustered", false).put("host", HOST));
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hardecode it in your tests
        getVertx().deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint",options, asyncResult -> {
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start entry point: " + asyncResult.succeeded());
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            latch.countDown();

        });
        awaitLatch(latch);
        getVertx().deployVerticle(new WsServiceOne(), options, asyncResult -> {
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start service: " + asyncResult.succeeded());
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            //   latch2.countDown();

            latch2.countDown();

        });
        awaitLatch(latch2);

        getVertx().deployVerticle(new WsServiceTwo(), options, asyncResult -> {
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start service: " + asyncResult.succeeded());
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            //   latch2.countDown();

            latch3.countDown();

        });

        client = getVertx().
                createHttpClient(new HttpClientOptions());
        awaitLatch(latch3);

    }


    @Test

    public void discoverServiceAndCallWSMethod() throws InterruptedException {
         final String message ="hello";
        final ServiceDiscovery dicovery = ServiceDiscovery.getInstance(this.getVertx());
        dicovery.getService(SERVICE_REST_GET, (serviceResult) -> {
            assertEquals(true, serviceResult.succeeded());
            Optional<ServiceInfo> optional = serviceResult.getServiceInfo();
            boolean present = optional.isPresent();
            assertEquals(true, present);
            optional.ifPresent(si -> {
                assertEquals(true, si.getServiceName().equals(SERVICE_REST_GET));
                si.getOperation("/wsEndpointOne").ifPresent(operation -> operation.websocketConnection(ws -> {
                    System.out.println("websocket: " + ws);
                    ws.handler(data -> {
                        assertNotNull(data.getString(0, data.length()));
                        assertTrue(new String(data.getBytes()).equals(message));
                        System.out.println("discoverServiceAndCallWSMethod message: " + new String(data.getBytes()));
                        testComplete();
                        ws.close();
                    });
                    ws.writeMessage(Buffer.buffer(message));
                }));
            });

        });


        await();

    }






    public HttpClient getClient() {
        return client;
    }


    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {
        @Path("/wsEndpointOne")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointOne(String name, MessageReply reply) {
            reply.reply(name);
        }

        @Path("/wsEndpointTwo")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointTwo(String name, MessageReply reply) {


            System.out.println("wsEndpointTwo-2: " + name + "   :::" + this);
        }

        @Path("/wsEndpointThree")
        @OperationType(Type.REST_GET)
        public void wsEndpointThree(String name,Message reply) {


            System.out.println("wsEndpointThree-2: " + name + "   :::" + this);
        }


    }

    @ApplicationPath(SERVICE_REST_GET2)
    public class WsServiceTwo extends ServiceVerticle {
        @Path("/wsServiceTwoOne")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointOne(String name, MessageReply reply) {

        }

        @Path("/wsServiceTwoTwo")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointTwo(String name, MessageReply reply) {


            System.out.println("wsEndpointTwo-2: " + name + "   :::" + this);
        }

        @Path("/wsServiceTwoThree")
        @OperationType(Type.REST_GET)
        public void wsEndpointThree(String name,Message reply) {


            System.out.println("wsEndpointThree-2: " + name + "   :::" + this);
        }
        @Path("/wsServiceTwoFour")
        @OperationType(Type.REST_GET)
        public void wsEndpointFour(String name,Message reply) {


            System.out.println("wsServiceTwoFour-2: " + name + "   :::" + this);
        }

    }
}
