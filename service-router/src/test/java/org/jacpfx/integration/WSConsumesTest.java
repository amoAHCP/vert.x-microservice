package org.jacpfx.integration;

import com.google.gson.Gson;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.WSMessageReply;
import org.jacpfx.common.OperationType;
import org.jacpfx.common.Serializer;
import org.jacpfx.common.Type;
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
public class WSConsumesTest extends VertxTestBase {
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
        getVertx().deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint",options, asyncResult -> {
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
    public void testSimpleString() throws InterruptedException {
        final String message = "xhello";

        getClient().websocket(8080, "localhost", SERVICE_REST_GET + "/testSimpleString", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("client testSimpleString:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                assertTrue(data.getString(0, data.length()).equals(message));
                ws.close();
                long endTime = System.currentTimeMillis();
                System.out.println("Total execution time testSimpleString: " + (endTime - startTime) + "ms");
                testComplete();
            });

            ws.writeFrame(WebSocketFrame.textFrame(message, true));
        });


        await();

    }


    @Test
    public void testSimpleObjectBySerialisation() throws InterruptedException {
        final PersonOne message = new PersonOne("Andy","M");

        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/testSimpleObjectBySerialisation", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("testSimpleObjectBySerialisation :" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                assertTrue(new String(data.getBytes()).equals(message.getName()));

                ws.close();
                long endTime = System.currentTimeMillis();
                System.out.println("Total execution time testSimpleObjectBySerialisation: " + (endTime - startTime) + "ms");
                testComplete();
            });

            try {
                byte[] tmp = Serializer.serialize(message);
                ws.writeMessage(Buffer.buffer(tmp));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        await();

    }
    @Test
    public void testSimpleObjectByJSONSerialisation() throws InterruptedException {
        final PersonOneX message = new PersonOneX("Andy","M");

        getClient().websocket(8080, "localhost", SERVICE_REST_GET + "/testSimpleObjectByJSONSerialisation", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("testSimpleObjectBySerialisation :" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                assertTrue(new String(data.getBytes()).equals(message.getName()));

                ws.close();
                long endTime = System.currentTimeMillis();
                System.out.println("Total execution time testSimpleObjectByJSONSerialisation: " + (endTime - startTime) + "ms");
                testComplete();
            });

            Gson gg = new Gson();
            ws.writeMessage(Buffer.buffer(gg.toJson(message)));
        });


        await();

    }

    @Test
    public void testSimpleObjectByBinarySerialisation() throws InterruptedException {
        final PersonOne message = new PersonOne("Andy","M");

        getClient().websocket(8080, "localhost", SERVICE_REST_GET + "/testSimpleObjectByBinarySerialisation", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("testSimpleObjectBySerialisation :" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                assertTrue(new String(data.getBytes()).equals(message.getName()));

                ws.close();
                long endTime = System.currentTimeMillis();
                System.out.println("Total execution time testSimpleObjectByBinarySerialisation: " + (endTime - startTime) + "ms");
                testComplete();
            });

            try {
                byte[] tmp = Serializer.serialize(message);
                ws.writeMessage(Buffer.buffer(tmp));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        await();

    }

    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {

        @Path("/testSimpleString")
        @OperationType(Type.WEBSOCKET)
        @Consumes("application/json")
        public void testSimpleString(String name, WSMessageReply reply) {
              reply.reply(name);
        }

        @Path("/testSimpleObjectBySerialisation")
        @OperationType(Type.WEBSOCKET)
        public void testSimpleObjectBySerialisation(PersonOne p1, WSMessageReply reply) {
            reply.reply(p1.getName());
        }

        @Path("/testSimpleObjectByJSONSerialisation")
        @OperationType(Type.WEBSOCKET)
        @Consumes("application/json")
        public void testSimpleObjectByJSONSerialisation(PersonOne p1, WSMessageReply reply) {
            reply.reply(p1.getName());
        }

        @Path("/testSimpleObjectByBinarySerialisation")
        @OperationType(Type.WEBSOCKET)
        @Consumes("application/octet-stream")
        public void testSimpleObjectByBinarySerialisation(PersonOne p1, WSMessageReply reply) {
            reply.reply(p1.getName());
        }

    }



    // Notiz Devoxx....

    // Problem serialisation/deserialisation messages (json, binary)
    // coupling --- shared data problem
    // Java Object vs. json string
}
