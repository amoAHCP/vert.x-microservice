package org.jacpfx.integration;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Created by amo on 13.11.14.
 */
public class ServiceEntryPointWSBasicTest extends VertxTestBase {

    @Override
    protected ClusterManager getClusterManager() {
        return new FakeClusterManager();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        CountDownLatch latch = new CountDownLatch(1);
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hardecode it in your tests
        vertx.deployVerticle("org.jacpfx.vertx.entrypoint.ServiceEntryPoint",asyncResult ->{
            // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());
            // If deployed correctly then start the tests!
            latch.countDown();

        });
        awaitLatch(latch);
    }


    private HttpClient getClient(final Handler<WebSocket> handler, final String path) {


        HttpClient client = vertx.
                createHttpClient(new HttpClientOptions()).connectWebsocket(8080, "localhost", path, handler);

        return client;
    }



    @Test
    public void getSimpleConnection() throws InterruptedException, IOException {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(2);
        final WebSocket[] wsTemp = new WebSocket[1];
        HttpClient client = getClient((ws) -> {
            latch.countDown();
            wsTemp[0] = ws;
            ws.handler((data) -> {
                System.out.println("client data handler 1:" + data);
                assertNotNull(data.getString(0, data.length()));
                latch2.countDown();
            });
        }, "/all");

        latch.await();

        assertNotNull(wsTemp[0]);

        wsTemp[0].writeFrame(new WebSocketFrameImpl("hello"));
        wsTemp[0].writeFrame(new WebSocketFrameImpl("hello"));
        latch2.await();

        client.close();
    }
}
