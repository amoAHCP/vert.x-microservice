package org.jacpfx.integration;

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
import org.jacpfx.common.Type;
import org.jacpfx.vertx.services.ServiceVerticle;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Tests serialisation of message bodies in WebSocket implementation
 * Created by Andy Moncsek on 27.04.15.
 */
public class WSTimerTest extends VertxTestBase {
    private final static int MAX_RESPONSE_ELEMENTS = 4;
    public static final String SERVICE_REST_GET = "/wsService";

    protected int getNumNodes() {
        return 1;
    }

    protected Vertx getVertx() {
        return vertices[0];
    }
    
    private static final String HOST="localhost";

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
    public void testSimpleTimerToAll() throws InterruptedException {
        final String message = "42";
        CountDownLatch latch = new CountDownLatch(6);

        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/testSimpleTimerToAll", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                wsHandleBody(message, latch, startTime, data);
            });


        });
        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/testSimpleTimerToAll", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                wsHandleBody(message, latch, startTime, data);
            });


        });
        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/testSimpleTimerToAll", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                wsHandleBody(message, latch, startTime, data);
            });
            // send connect message
            ws.writeFrame(WebSocketFrame.textFrame(message, true));
        });

        latch.await();
        testComplete();

    }

    private void wsHandleBody(String message, CountDownLatch latch, long startTime, Buffer data) {
        System.out.println("client testSimpleString:" + new String(data.getBytes()));
        assertNotNull(data.getString(0, data.length()));
        assertTrue(data.getString(0, data.length()).equals(message));
        long endTime = System.currentTimeMillis();
        System.out.println("Total execution time testSimpleString: " + (endTime - startTime) + "ms");
        latch.countDown();
    }


    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {

        @Path("/testSimpleTimerToAll")
        @OperationType(Type.WEBSOCKET)
        @Consumes("application/json")
        public void testSimpleString(String name, WSMessageReply reply) {
            System.out.println("GOT MESSAGE");
            this.getVertx().setPeriodic(2000, (arg) -> {
                System.out.println("SEND MESSAGE");
                reply.replyToAllAsync(() -> "42");
            });
        }


    }



    // Notiz Devoxx....

    // Problem serialisation/deserialisation messages (json, binary)
    // coupling --- shared data problem
    // Java Object vs. json string
}
