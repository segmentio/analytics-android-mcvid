package com.segment.analytics.android.middlewares.mcvid;

import android.content.Context;

import com.segment.analytics.Analytics;
import com.segment.analytics.integrations.Logger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(RobolectricTestRunner.class)
public class VisitorIdManagerTest {

    private ExecutorService executor;
    private Logger logger;

    @Mock private MarketingCloudClient client;
    @Mock private VisitorIdStore store;
    @Mock private Context context;

    @Before
    public void initialize() {
        MockitoAnnotations.initMocks(this);
        executor = Executors.newSingleThreadExecutor();
        logger = Logger.with(Analytics.LogLevel.DEBUG);
    }

    @Test
    public void getVisitorId() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(client.getVisitorID()).thenReturn(visitorId);

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        for (int i = 0; i < 10; i++) {
            String vId = manager.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_null() throws MarketingCloudClient.MarketingCloudException, IOException {
        String visitorId = "visitorId";
        Mockito.when(client.getVisitorID()).thenReturn(visitorId);

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        Assert.assertNull(manager.getVisitorId());
    }

    @Test
    public void getVisitorId_previousValue() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(client.getVisitorID()).thenReturn("newVisitorId");
        Mockito.when(store.get()).thenReturn(visitorId);

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        for (int i = 0; i < 10; i++) {
            String vId = manager.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_ignorePreviousValueException() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(client.getVisitorID()).thenReturn(visitorId);
        Mockito.when(store.get()).thenThrow(new RuntimeException("Error!"));

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        for (int i = 0; i < 10; i++) {
            String vId = manager.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_ignoreStoreException() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(client.getVisitorID()).thenReturn(visitorId);
        Mockito.doThrow(new RuntimeException("Error!")).when(store).set(visitorId);

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        for (int i = 0; i < 10; i++) {
            String vId = manager.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_exception() throws MarketingCloudClient.MarketingCloudException, IOException {
        String visitorId = "visitorId";
        Mockito.when(client.getVisitorID()).thenThrow(new MarketingCloudClient.MarketingCloudException("Error!!!!"));

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        Assert.assertNull(manager.getVisitorId());
    }

    @Test
    public void getVisitorId_scheduleRetry() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(client.getVisitorID()).thenThrow(new MarketingCloudClient.MarketingCloudException("Error!!!!")).thenReturn(visitorId);

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        for (int i = 0; i < 10; i++) {
            String vId = manager.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_sync() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException, ExecutionException {
        String visitorId = "visitorId";
        String advertisingId = "advertisingId";
        // Due to the Spy, we need to make it fail the first time.
        Mockito.when(client.getVisitorID()).thenThrow(new MarketingCloudClient.MarketingCloudException("Error!")).thenReturn(visitorId);

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        VisitorIdManager.ExecutorVisitorIdManager spy = Mockito.spy(manager);
        Mockito.when(spy.getAdvertisingId()).thenReturn(advertisingId);

        for (int i = 0; i < 10; i++) {
            String vId = spy.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                Assert.assertTrue(spy.synced.get());
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_notSync() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException, ExecutionException {
        String visitorId = "visitorId";
        // Due to the Spy, we need to make it fail the first time.
        Mockito.when(client.getVisitorID()).thenThrow(new MarketingCloudClient.MarketingCloudException("Error!")).thenReturn(visitorId);

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        VisitorIdManager.ExecutorVisitorIdManager spy = Mockito.spy(manager);
        Mockito.when(spy.getAdvertisingId()).thenReturn(null);

        for (int i = 0; i < 10; i++) {
            String vId = spy.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                Assert.assertFalse(spy.synced.get());
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_previouslySynced() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException, ExecutionException {
        String visitorId = "visitorId";
        String advertisingId = "advertisingId";
        // Due to the Spy, we need to make it fail the first time.
        Mockito.when(client.getVisitorID()).thenThrow(new MarketingCloudClient.MarketingCloudException("Error!")).thenReturn(visitorId);
        Mockito.doThrow(new MarketingCloudClient.MarketingCloudException("Error!")).when(client).idSync(visitorId, advertisingId);
        Mockito.when(store.getSyncedAdvertisingId()).thenReturn(advertisingId);


        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        VisitorIdManager.ExecutorVisitorIdManager spy = Mockito.spy(manager);
        Mockito.when(spy.getAdvertisingId()).thenReturn(advertisingId);

        for (int i = 0; i < 10; i++) {
            String vId = spy.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                Assert.assertTrue(spy.synced.get());
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getVisitorId_notPreviouslySynced() throws MarketingCloudClient.MarketingCloudException, IOException, InterruptedException, ExecutionException {
        String visitorId = "visitorId";
        String advertisingId = "advertisingId";
        // Due to the Spy, we need to make it fail the first time.
        Mockito.when(client.getVisitorID()).thenThrow(new MarketingCloudClient.MarketingCloudException("Error!")).thenReturn(visitorId);
        Mockito.when(store.getSyncedAdvertisingId()).thenReturn("oldAdvertisingId");


        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        VisitorIdManager.ExecutorVisitorIdManager spy = Mockito.spy(manager);
        Mockito.when(spy.getAdvertisingId()).thenReturn(advertisingId);

        for (int i = 0; i < 10; i++) {
            String vId = spy.getVisitorId();
            if (vId != null) {
                Assert.assertEquals(visitorId, vId);
                Assert.assertTrue(spy.synced.get());
                return;
            }
            Thread.sleep(100);
        }

        Assert.fail();
    }

    @Test
    public void getAdvertisingId() throws ExecutionException, InterruptedException {
        // There are very few things we can test with unitests and Google Play Services

        final VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        Future<String> advertisingId = executor.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return manager.getAdvertisingId();
            }
        });

        advertisingId.get();
    }

    @Test(expected = IllegalArgumentException.class)
    public void handleError() {
        VisitorIdManager.ExecutorVisitorIdManager manager = new VisitorIdManager.ExecutorVisitorIdManager(context, executor, client, store, logger);
        manager.setErrorListener(new VisitorIdManager.ErrorListener() {
            @Override
            public void onError(Exception exception) {
                throw new IllegalArgumentException("All good");
            }
        });

        manager.handleError("This is a test: %s", new IOException("Yeey"));
    }

}
