package org.jacpfx.integration;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceEntryPointWSBasicTest extends VertxTestBase {

    private final static int MAX_RESPONSE_ELEMENTS = 4;

    @Override
    protected ClusterManager getClusterManager() {
        return new FakeClusterManager();
    }


    private HttpClient client;

    @Override
    public void setUp() throws Exception {
        super.setUp();
/*        CountDownLatch latch = new CountDownLatch(2);
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hardecode it in your tests
        vertx.deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint",asyncResult ->{
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start entry point: "+asyncResult.succeeded());
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            latch.countDown();

        });

        vertx.deployVerticle("org.jacpfx.service.RESTGetVerticleService",asyncResult ->{
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            System.out.println("start service: "+asyncResult.succeeded());
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            latch.countDown();

        });
        awaitLatch(latch);*/
        client = vertx.
                createHttpClient(new HttpClientOptions());
    }


    private HttpClient getClient(final Handler<WebSocket> handler, final String path) {

        HttpClient client = vertx.
                createHttpClient(new HttpClientOptions()).connectWebsocket(8080, "localhost", path, handler);

        return client;
    }

    @Test
    public void simpleConnectAndWrite() throws InterruptedException {

        getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/hello", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("client data handler 1:" + new String(data.getBytes()));
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

        getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/asyncReply", ws -> {
            long startTime = System.currentTimeMillis();
            ws.handler((data) -> {
                System.out.println("client data handler 1:" + new String(data.getBytes()));
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
            getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/hello", ws -> {

                ws.handler((data) -> {
                    System.out.println("client data handler 2:" + new String(data.getBytes()));
                    assertNotNull(data.getString(0, data.length()));
                    ws.close();
                    latchMain.countDown();
                });

                ws.writeFrame(new WebSocketFrameImpl("yhello"));
            });


        };

        s.submit(r);
        s.submit(r);

        latchMain.await();
        s.awaitTermination(6000, TimeUnit.MILLISECONDS);

        testComplete();

    }

    @Test
    @Ignore
    public void simpleConnectOnTenThreads() throws InterruptedException {
        ExecutorService s = Executors.newFixedThreadPool(10);
        CountDownLatch latchMain = new CountDownLatch(10);
        Runnable r = () -> {
            getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/hello", ws -> {

                ws.handler((data) -> {
                    System.out.println("client data handler 3:" + new String(data.getBytes()));
                    assertNotNull(data.getString(0, data.length()));
                    ws.close();
                    latchMain.countDown();
                });

                ws.writeFrame(new WebSocketFrameImpl("zhello"));
            });


        };

        s.submit(r);
        s.submit(r);
        s.submit(r);
        s.submit(r);
        s.submit(r);
        s.submit(r);
        s.submit(r);
        s.submit(r);
        s.submit(r);
        s.submit(r);

        latchMain.await();
        s.awaitTermination(5000, TimeUnit.MILLISECONDS);

        testComplete();

    }

    @Test
    public void simpleMutilpeReply() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger(0);
        getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/wsEndpintTwo", ws -> {

            ws.handler((data) -> {
                System.out.println("client data handler 4:" + new String(data.getBytes()));
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
        getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/wsEndpintThree", ws -> {

            ws.handler((data) -> {
                System.out.println("client data handler 4:" + new String(data.getBytes()));
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
        getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/wsEndpintFour", ws -> {

            ws.handler((data) -> {
                System.out.println("client data handler 4:" + new String(data.getBytes()));
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
        getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/wsEndpintFour", ws -> {

            ws.handler((data) -> {
                System.out.println("client data handler 5:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                latch.countDown();
                ws.close();

            });


        });

        getClient().connectWebsocket(8080, "localhost", "/service-REST-GET/wsEndpintFour", ws -> {

            ws.handler((data) -> {
                System.out.println("client data handler 5.1:" + new String(data.getBytes()));
                assertNotNull(data.getString(0, data.length()));
                latch.countDown();
                ws.close();



            });

            ws.writeFrame(new WebSocketFrameImpl("xhello simpleMutilpeReplyToAllThreaded"));

        });


        latch.await();
        testComplete();
        await();
    }

    public synchronized HttpClient getClient() {
        return client;
    }

}
