package org.jacpfx.integration;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.OperationType;
import org.jacpfx.common.Type;
import org.jacpfx.vertx.services.ServiceVerticle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.concurrent.CountDownLatch;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceEntryPointTestPathParam extends VertxTestBase {
    private final static int MAX_RESPONSE_ELEMENTS = 4;
    public static final String SERVICE_REST_GET = "/wsService";
    private static final String HOST="localhost";
    public static final int PORT = 8080;

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
                createHttpClient(new HttpClientOptions().setDefaultPort(PORT));
        awaitLatch(latch2);

    }

    @Test
    public void testSimpleRESTGetQueryParamRoute() throws InterruptedException {
        HttpClientRequest request = client.get(SERVICE_REST_GET+"/testSimpleQueryParam/hallo1", new Handler<HttpClientResponse>() {
            public void handle(HttpClientResponse resp) {
                resp.bodyHandler(body -> {
                    System.out.println("Got a response: " + body.toString());
                    Assert.assertEquals(body.toString(), "hallo1");
                    testComplete();
                });


            }
        });
        request.end();
        await();
    }

    @Test
    public void testSimpleRESTGetComlexQueryParamRoute() throws InterruptedException {
        HttpClientRequest request = client.get(SERVICE_REST_GET+"/testComplexQueryParam/hallo1/temp/xyz", new Handler<HttpClientResponse>() {
            public void handle(HttpClientResponse resp) {
                resp.bodyHandler(body -> {
                    System.out.println("Got a response: " + body.toString());
                    Assert.assertEquals(body.toString(), "hallo1xyz");
                    testComplete();
                });


            }
        });
        request.end();
        await();
    }



    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {

        @Path("/testSimpleQueryParam/:name")
        @OperationType(Type.REST_GET)
        @Consumes("application/json")
        public void testSimpleString(@PathParam("name")String name, final Message message) {
            message.reply(name);
        }

        @Path("/testComplexQueryParam/:name/temp/:lastName")
        @OperationType(Type.REST_GET)
        public void testSimpleObjectBySerialisation(@PathParam("name")String name,@PathParam("lastName")String lastName, final Message message) {
            message.reply(name + lastName);
        }


        // TODO add POST Tests!


    }



}
