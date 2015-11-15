package org.jacpfx.integration;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.jacpfx.common.OperationType;
import org.jacpfx.common.Type;
import org.jacpfx.common.WSResponse;
import org.jacpfx.vertx.services.ServiceVerticle;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Andy Moncsek on 23.04.15.
 */
public class WSServiceTest extends VertxTestBase {
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


    @Test

    public void simpleConnectAndWrite() throws InterruptedException {


        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/hello", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("client data simpleConnectAndWrite:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                ws.close();
                long endTime = System.currentTimeMillis();
                System.out.println("Total execution time simpleConnectAndWrite: " + (endTime - startTime) + "ms");
                testComplete();
            });

            ws.writeFrame(new WebSocketFrameImpl("xhello"));
        });


        await();

    }

    @Test
    public void simpleConnectAndAsyncWrite() throws InterruptedException {

        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/asyncReply", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("client data simpleConnectAndAsyncWrite:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                ws.close();
                long endTime = System.currentTimeMillis();
                System.out.println("Total execution time simpleConnectAndAsyncWrite: " + (endTime - startTime) + "ms");
                testComplete();
            });

            ws.writeFrame(new WebSocketFrameImpl("xhello"));
        });


        await();

    }

    @Test
    public void simpleConnectOnTwoThreads() throws InterruptedException {
        ExecutorService s = Executors.newFixedThreadPool(2);
        CountDownLatch latchMain = new CountDownLatch(2);
        Runnable r = () -> {

            getClient().websocket(8080, HOST, SERVICE_REST_GET + "/hello", ws -> {
                long startTime = System.currentTimeMillis();
                ws.handler((data) -> {
                    System.out.println("client data simpleConnectOnTwoThreads:" + new String(data.getBytes()));
                    assertNotNull(data.getString(0, data.length()));
                    ws.close();
                    latchMain.countDown();
                    long endTime = System.currentTimeMillis();
                    System.out.println("round trip time simpleConnectOnTwoThreads: " + (endTime - startTime) + "ms");
                });

                ws.writeFrame(new WebSocketFrameImpl("yhello"));
            });

        };

        s.submit(r);
        s.submit(r);

        latchMain.await();



    }

    @Test
    public void simpleConnectOnTenThreads() throws InterruptedException {
        int counter =100;
        ExecutorService s = Executors.newFixedThreadPool(counter);
        CountDownLatch latchMain = new CountDownLatch(counter);


        for(int i =0; i<=counter; i++) {
            Runnable r = () -> {

                getClient().websocket(8080, HOST, SERVICE_REST_GET + "/hello", ws -> {
                    long startTime = System.currentTimeMillis();
                    ws.handler((data) -> {
                        System.out.println("client data simpleConnectOnTenThreads:" + new String(data.getBytes()));
                        assertNotNull(data.getString(0, data.length()));
                        ws.close();
                        latchMain.countDown();
                        long endTime = System.currentTimeMillis();
                        System.out.println("round trip time simpleConnectOnTenThreads: " + (endTime - startTime) + "ms");
                    });

                    ws.writeFrame(new WebSocketFrameImpl("zhello"));
                });


            };

            s.submit(r);
        }



        latchMain.await();



    }

    @Test
    public void simpleMutilpeReply() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger(0);
        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/wsEndpintTwo", ws -> {

            ws.handler((data) -> {
                System.out.println("client data simpleMutilpeReply:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                if (counter.incrementAndGet() == MAX_RESPONSE_ELEMENTS) {
                    ws.close();
                    testComplete();
                }

            });

            ws.writeFrame(new WebSocketFrameImpl("xhello"));
        });


        await();

    }

    @Test
    public void simpleMutilpeReplyToAll() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger(0);
        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/wsEndpintThree", ws -> {

            ws.handler((data) -> {
                System.out.println("client data simpleMutilpeReplyToAll:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                if (counter.incrementAndGet() == MAX_RESPONSE_ELEMENTS) {
                    ws.close();
                    testComplete();
                }

            });

            ws.writeFrame(new WebSocketFrameImpl("xhello"));
        });


        await();
    }

    @Test
    public void simpleMutilpeReplyToAll_1() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger(0);
        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/wsEndpintFour", ws -> {

            ws.handler((data) -> {
                System.out.println("client data simpleMutilpeReplyToAll_1:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                ws.close();
                testComplete();

            });

            ws.writeFrame(new WebSocketFrameImpl("xhello"));
        });


        await();
    }

    @Test
    public void simpleMutilpeReplyToAllThreaded() throws InterruptedException {
        ExecutorService s = Executors.newFixedThreadPool(10);
        final CountDownLatch latch = new CountDownLatch(2);
        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/wsEndpintFour", ws -> {

            ws.handler((data) -> {
                System.out.println("client data simpleMutilpeReplyToAllThreaded:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                latch.countDown();
                ws.close();

            });


        });

        getClient().websocket(8080, HOST, SERVICE_REST_GET + "/wsEndpintFour", ws -> {

            ws.handler((data) -> {
                System.out.println("client datasimpleMutilpeReplyToAllThreaded 5.1:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                latch.countDown();
                ws.close();


            });

            ws.writeFrame(new WebSocketFrameImpl("xhello simpleMutilpeReplyToAllThreaded"));

        });


        latch.await();
    }

    public HttpClient getClient() {
        return client;
    }


    @ApplicationPath(SERVICE_REST_GET)
    public class WsServiceOne extends ServiceVerticle {
        @Path("/wsEndpintOne")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointOne(String name, WSResponse reply) {

        }

        @Path("/wsEndpintTwo")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointTwo(String name, WSResponse reply) {

            replyAsyncTwo(name + "-3", reply);
            replyAsyncTwo(name + "-4", reply);
            replyAsyncTwo(name + "-5", reply);
            replyAsyncTwo(name + "-6",reply);
            System.out.println("wsEndpointTwo-2: " + name + "   :::" + this);
        }

        private void replyAsyncTwo(String name, WSResponse reply) {
            reply.reply(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return name + Thread.currentThread();
            });
        }

        private void replyToAllAsync(String name, WSResponse reply) {
            reply.replyToAll(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return name + Thread.currentThread();
            });
        }

        @Path("/wsEndpintThree")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointThreeReplyToAll(String name, WSResponse reply) {
            replyToAllAsync(name + "-3", reply);
            replyToAllAsync(name + "-4", reply);
            replyToAllAsync(name + "-5", reply);
            replyToAllAsync(name + "-6", reply);

            System.out.println("wsEndpointThreeReplyToAll-2: " + name + "   :::" + this);
        }


        @Path("/wsEndpintFour")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointThreeReplyToAllTwo(String name, WSResponse reply) {
            replyToAllAsync(name + "-3", reply);
            System.out.println("wsEndpointThreeReplyToAllTwo-4: " + name + "   :::" + this);
        }

        @Path("/hello")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointHello(String name, WSResponse reply) {

            reply.reply(() -> name + "-2");
            System.out.println("wsEndpointHello-1: " + name + "   :::" + this);
        }

        @Path("/asyncReply")
        @OperationType(Type.WEBSOCKET)
        public void wsEndpointAsyncReply(String name, WSResponse reply) {

            reply.reply(() -> name + "-2");
            System.out.println("wsEndpointAsyncReply-1: " + name + "   :::" + this);
        }
    }
}
