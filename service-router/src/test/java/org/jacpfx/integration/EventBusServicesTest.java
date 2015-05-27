package org.jacpfx.integration;

import com.google.gson.Gson;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.*;
import org.jacpfx.entities.PersonOne;
import org.jacpfx.entities.PersonOneX;
import org.jacpfx.vertx.services.ServiceVerticle;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Tests serialisation of message bodies in WebSocket implementation
 * Created by Andy Moncsek on 27.04.15.
 */
public class EventBusServicesTest extends VertxTestBase {
    private final static int MAX_RESPONSE_ELEMENTS = 4;
    public static final String SERVICE_REST_GET = "/wsService";
    public static final String HOST = "localhost";

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
        options.setConfig(new JsonObject().put("host", HOST));
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hardecode it in your tests
        getVertx().deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint", options, asyncResult -> {
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start org.jacpfx.vertx.entrypoint.ServiceEntryPoint: " + asyncResult.succeeded());
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            latch.countDown();

        });
        awaitLatch(latch);
        getVertx().deployVerticle(new WsServiceOne(), options, asyncResult -> {
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start WsServiceOne: " + asyncResult.succeeded());
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

    public HttpClient getClient() {
        return client;
    }

    @Test
    public void testRegisterVerticle() throws InterruptedException, IOException {
        getVertx().sharedData().<String, ServiceInfoHolder>getClusterWideMap(GlobalKeyHolder.REGISTRY_MAP_KEY, onSuccess(resultMap ->
                        resultMap.get(GlobalKeyHolder.SERVICE_HOLDER, onSuccess(holder ->
                                {
                                    assertTrue(holder.getAll().size() >= 1);
                                    ServiceInfo info = holder.getAll().get(0);

                                    System.out.printf("holder: " + info);
                                    testComplete();
                                }
                        ))
        ));

        await();


    }


    @Test
    public void testSimpleString() throws InterruptedException {

        getVertx().eventBus().send(SERVICE_REST_GET.concat("/testSimpleString"), "hello", messageAsyncResult -> {
            testComplete();
        });

        await();

    }

    @Test
    public void testSimpleJsonObject() throws InterruptedException {

        getVertx().eventBus().send(SERVICE_REST_GET.concat("/testSimpleJsonObject"), new JsonObject().put("name", "hello"), messageAsyncResult -> {
            testComplete();
        });

        await();

    }

    @Test
    public void testSimpleBinaryString() throws InterruptedException {

        getVertx().eventBus().send(SERVICE_REST_GET.concat("/testSimpleString2"), "hello".getBytes(), messageAsyncResult -> {
            testComplete();
        });

        await();

    }

    @Test
    public void testSimpleJsonString() throws InterruptedException {

        getVertx().eventBus().send(SERVICE_REST_GET.concat("/testSimpleJsonString"), "hello", messageAsyncResult -> {
            testComplete();
        });

        await();

    }

    @Test
    public void testComplexJson() throws InterruptedException, IOException {


        getVertx().eventBus().send(SERVICE_REST_GET.concat("/testComplexJson"), new Gson().toJson(new PersonOne("AAA", "BBBB")), messageAsyncResult -> {
            assertTrue(messageAsyncResult.succeeded());
            String val = String.valueOf(messageAsyncResult.result().body());
            assertTrue(val.equals("AAA"));
            testComplete();
        });

        await();

    }

    @Test
    public void testComplexBinary() throws InterruptedException, IOException {
        byte[] tmp = Serializer.serialize(new PersonOne("AAA", "BBBB"));

        getVertx().eventBus().send(SERVICE_REST_GET.concat("/testComplexBinary"), tmp, (Handler<AsyncResult<Message<byte[]>>>)messageAsyncResult -> {
            assertTrue(messageAsyncResult.succeeded());
            byte[] resultBytes = messageAsyncResult.result().body();
            try {
                PersonOne p1 = (PersonOne) Serializer.deserialize(resultBytes);
                assertTrue(p1.getName().equals("AAA"));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            testComplete();
        });

        await();

    }


    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {

        @Path("/testSimpleString")
        @OperationType(Type.EVENTBUS)
        public void testSimpleString(String name, EBMessageReply reply) {
            System.out.println(name);
            reply.reply("ass");
        }

        @Path("/testSimpleJsonObject")
        @OperationType(Type.EVENTBUS)
        public void testSimpleJSON(JsonObject name, EBMessageReply reply) {
            System.out.println(name.getString("name"));
            reply.reply(new JsonObject().put("name", "hello"));
        }

        @Path("/testSimpleJsonString")
        @OperationType(Type.EVENTBUS)
        @Consumes("application/json")
        public void testSimpleJsonString(String name, EBMessageReply reply) {
            System.out.println(name);
            reply.reply("ass");
        }



        @Path("/testSimpleString2")
        @OperationType(Type.EVENTBUS)
        @Consumes("application/octet-stream")
        public void testSimpleMessage2(String name, EBMessageReply reply) {
            System.out.println(name);
            reply.reply("ass");
        }

        @Path("/testComplexJson")
        @OperationType(Type.EVENTBUS)
        @Consumes("application/json")
        public void testComplexJson(PersonOneX p1, EBMessageReply reply) {
            System.out.println(p1.getName());
            reply.reply(p1.getName());
        }
        @Path("/testComplexBinary")
        @OperationType(Type.EVENTBUS)
        @Consumes("application/octet-stream")
        public void testComplexBinary(PersonOne p1, EBMessageReply reply) {
            System.out.println("testComplexBinary: "+p1.getName());
            reply.reply(new PersonOne("AAA", "BBBB"));
        }

    }

}
