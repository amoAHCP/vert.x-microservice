package org.jacpfx.integration;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.OperationType;
import org.jacpfx.common.Type;
import org.jacpfx.common.WSMessageReply;
import org.jacpfx.entities.PersonOne;
import org.jacpfx.vertx.services.ServiceVerticle;
import org.junit.Before;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceEntryPointTestQueryParam extends VertxTestBase {
    private final static int MAX_RESPONSE_ELEMENTS = 4;
    public static final String SERVICE_REST_GET = "/wsService";
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

        client = getVertx().
                createHttpClient(new HttpClientOptions());
        awaitLatch(latch2);

    }

   /* @Test
    public void testSimpleRESTGetQueryParamRoute() throws InterruptedException {
        final ConcurrentSharedMap<Object, Object> map = vertx.sharedData().getMap(ServiceRegistry.SERVICE_REGISTRY);
        int size = map.size();
        vertx.eventBus().send(ServiceRegistry.SERVICE_REGISTRY_REGISTER, getServiceInfoDesc("/testservice1"), (Handler<Message<Boolean>>) reply -> {

            assertEquals(true, reply.body());
            assertTrue(vertx.sharedData().getMap(ServiceRegistry.SERVICE_REGISTRY).size() == size + 1);
            vertx.eventBus().registerHandler("/testservice1/operation2", m -> {
                Logger logger = container.logger();
                final Parameter<String> params = gson.fromJson(m.body().toString(), Parameter.class);
                m.reply(params.getValue("name"));
                logger.info("reply to: " + m.body());
            });
            HttpClientRequest request = getClient().get("/testservice1/operation2?name=hallo1", new Handler<HttpClientResponse>() {
                public void handle(HttpClientResponse resp) {
                    resp.bodyHandler(body -> {
                        System.out.println("Got a response: " + body.toString());
                        Assert.assertEquals(body.toString(), "hallo1");
                    });

                    testComplete();
                }
            });
            request.end();

        });

    }*/


    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {

        @Path("/testSimpleString")
        @OperationType(Type.REST_GET)
        @Consumes("application/json")
        public void testSimpleString(String name, WSMessageReply reply) {
            reply.reply(name);
        }

        @Path("/testSimpleObjectBySerialisation")
        @OperationType(Type.REST_GET)
        public void testSimpleObjectBySerialisation(PersonOne p1, WSMessageReply reply) {
            reply.reply(p1.getName());
        }

        @Path("/testSimpleObjectByJSONSerialisation")
        @OperationType(Type.REST_POST)
        @Consumes("application/json")
        public void testSimpleObjectByJSONSerialisation(PersonOne p1, WSMessageReply reply) {
            reply.reply(p1.getName());
        }

        @Path("/testSimpleObjectByBinarySerialisation")
        @OperationType(Type.REST_POST)
        @Consumes("application/octet-stream")
        public void testSimpleObjectByBinarySerialisation(PersonOne p1, WSMessageReply reply) {
            reply.reply(p1.getName());
        }

    }



}
