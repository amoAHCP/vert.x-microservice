package org.jacpfx.integration;

import com.google.gson.Gson;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.WSMessageReply;
import org.jacpfx.common.OperationType;
import org.jacpfx.common.ServiceInfo;
import org.jacpfx.common.Type;
import org.jacpfx.entities.PersonOne;
import org.jacpfx.entities.PersonOneX;
import org.jacpfx.vertx.registry.ServiceDiscovery;
import org.jacpfx.vertx.services.ServiceVerticle;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Created by Andy Moncsek on 07.05.15.
 */
public class ServiceDiscoveryTests extends VertxTestBase {
    private final static int MAX_RESPONSE_ELEMENTS = 4;
    public static final String SERVICE_REST_GET = "/wsService";
    public static final String SERVICE_REST_GET2 = "/restService";
    private static final String HOST = "localhost";

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
        getVertx().deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint", options, asyncResult -> {
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
        final String message = "hello";
        final ServiceDiscovery dicovery = ServiceDiscovery.getInstance(this.getVertx());
        dicovery.getService(SERVICE_REST_GET, (serviceResult) -> {
            assertEquals(true, serviceResult.succeeded());
            ServiceInfo si = serviceResult.getServiceInfo();
            assertEquals(true, si.getServiceName().equals(SERVICE_REST_GET));
            si.getOperation("/wsEndpointOne", operation -> operation.getOperation().websocketConnection(ws -> {
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


        await();

    }

    @Test
    /**
     * calls a WS servicesA which uses discovery to get data from ServiceB
     */

    public void discoverServiceAndCallTransientWSMethod() throws InterruptedException {
        final String message = "hello";
        final ServiceDiscovery dicovery = ServiceDiscovery.getInstance(this.getVertx());
        dicovery.getService(SERVICE_REST_GET, (serviceResult) -> {
            assertEquals(true, serviceResult.succeeded());
            ServiceInfo si = serviceResult.getServiceInfo();
            assertEquals(true, si.getServiceName().equals(SERVICE_REST_GET));
            si.getOperation("/wsEndpointTwo",operation -> operation.getOperation().websocketConnection(ws -> {
                ws.handler(data -> {
                    assertNotNull(data.getString(0, data.length()));
                    assertTrue(new String(data.getBytes()).equals(message + "-" + "wsServiceTwoTwo" + "-" + "wsEndpointTwo"));
                    System.out.println("discoverServiceAndCallWSMethod message: " + new String(data.getBytes()));
                    testComplete();
                    ws.close();
                });
                ws.writeMessage(Buffer.buffer(message));
            }));

        });


        await();

    }

    @Test
    /**
     * calls a WS servicesA which uses discovery to get data from ServiceB
     */

    public void discoverServiceAndCallTransientTypedWSMethod() throws InterruptedException {
        final PersonOneX message = new PersonOneX("Andy", "M");
        final ServiceDiscovery dicovery = ServiceDiscovery.getInstance(this.getVertx());
        dicovery.getService(SERVICE_REST_GET, (serviceResult) -> {
            assertEquals(true, serviceResult.succeeded());
            ServiceInfo si = serviceResult.getServiceInfo();
            assertEquals(true, si.getServiceName().equals(SERVICE_REST_GET));
            si.getOperation("/wsEndpointThree",operation -> operation.getOperation().websocketConnection(ws -> {
                ws.handler(data -> {
                    assertNotNull(data.getString(0, data.length()));

                    Gson gg = new Gson();
                    PersonOne pReply = gg.fromJson(new String(data.getBytes()), PersonOne.class);
                    assertTrue(pReply.getName().equals(message.getName() + "_wsServiceTwoThree"));
                    System.out.println("discoverServiceAndCallWSMethod message: " + pReply);
                    testComplete();
                    ws.close();
                });
                Gson gg = new Gson();
                ws.writeMessage(Buffer.buffer(gg.toJson(message)));
            }));

        });


        await();
    }

    public void discoverServiceFailOfNonExtistingService() {

    }

    public void discoverServiceFailOfNonExtistingTransientService() {

    }

    public void discoverServiceFailOfNonExtistingOperation() {

    }

    public void discoverServiceFailOfNonExtistingTransientOperation() {

    }


    public HttpClient getClient() {
        return client;
    }


    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {
        @Path("/wsEndpointOne")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointOne(String name, WSMessageReply reply) {
            reply.reply(name);
        }

        @Path("/wsEndpointTwo")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointTwo(String name, WSMessageReply reply) {
            final ServiceDiscovery dicovery = ServiceDiscovery.getInstance(this.getVertx());
            dicovery.getService(SERVICE_REST_GET2, (serviceResult) -> {
                if (serviceResult.succeeded()) {
                    ServiceInfo si =serviceResult.getServiceInfo();
                    si.getOperation("/wsServiceTwoTwo",operation -> operation.getOperation().websocketConnection(ws -> {
                        ws.handler(data -> {
                            reply.reply(new String(data.getBytes()) + "-" + "wsEndpointTwo");
                            ws.close();
                        });
                        ws.writeMessage(Buffer.buffer(name));
                    }));
                }
            });

        }

        @Path("/wsEndpointThree")
        @OperationType(Type.WEBSOCKET)
        @Consumes("application/json")
        public void wsEndpointThree(final PersonOne p1, WSMessageReply reply) {

            final ServiceDiscovery dicovery = ServiceDiscovery.getInstance(this.getVertx());
            dicovery.getService(SERVICE_REST_GET2, (serviceResult) -> {
                if (serviceResult.succeeded()) {
                    ServiceInfo si =serviceResult.getServiceInfo();
                    si.getOperation("/wsServiceTwoThree",operation -> operation.getOperation().websocketConnection(ws -> {
                        ws.handler(data -> {
                            reply.reply(new String(data.getBytes()));
                            ws.close();
                        });
                        Gson gg = new Gson();
                        ws.writeMessage(Buffer.buffer(gg.toJson(p1)));
                    }));
                }
            });
        }


    }

    @ApplicationPath(SERVICE_REST_GET2)
    public class WsServiceTwo extends ServiceVerticle {
        @Path("/wsServiceTwoOne")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointOne(String name, WSMessageReply reply) {

        }

        @Path("/wsServiceTwoTwo")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointTwo(String name, WSMessageReply reply) {

            reply.reply(name + "-" + "wsServiceTwoTwo");
        }

        @Path("/wsServiceTwoThree")
        @OperationType(Type.WEBSOCKET)
        @Consumes("application/json")
        public void wsEndpointThree(PersonOneX p2, WSMessageReply reply) {
            Gson gg = new Gson();
            reply.reply(gg.toJson(new PersonOne(p2.getName() + "_wsServiceTwoThree", p2.getLastname())));
            System.out.println("wsServiceTwoThree-2: " + p2 + "   :::" + this);
        }

        @Path("/wsServiceTwoFour")
        @OperationType(Type.WEBSOCKET)
        @Consumes("application/json")
        public void wsEndpointFour(PersonOneX p2, WSMessageReply reply) {


            System.out.println("wsServiceTwoFour-2: " + name + "   :::" + this);
        }

    }
}
