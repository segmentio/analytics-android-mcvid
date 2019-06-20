package com.segment.analytics.android.middlewares.mcvid;

import android.content.Context;

import com.segment.analytics.Analytics;
import com.segment.analytics.Middleware;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public class MarketingCloudMiddlewareTest {

    @Mock private Context context;
    @Mock private VisitorIdManager manager;
    @Mock private MarketingCloudClient client;
    @Mock private VisitorIdStore store;
    private MarketingCloudMiddleware middleware;

    @Before
    public void initialize() {
        MockitoAnnotations.initMocks(this);
        middleware = new MarketingCloudMiddleware(manager);
    }

    @Test
    public void getVisitorId() {
        String visitorId = "visitorId";
        Mockito.when(manager.getVisitorId()).thenReturn(visitorId);
        Assert.assertEquals(visitorId, middleware.getVisitorId());
    }

    @Test
    public void getClient() {
        MarketingCloudMiddleware middleware = new MarketingCloudMiddleware(context, "organizationId", 3);
        Assert.assertNotNull(middleware.getClient());
    }

    @Test
    public void getClient_builder() {
        MarketingCloudMiddleware middleware = new MarketingCloudMiddleware.Builder().withOrganizationId("organizationId").withRegion(3).withContext(context).build();
        Assert.assertNotNull(middleware.getClient());
    }

    @Test
    public void getClient_null() {
        Assert.assertNull(middleware.getClient());
    }

    @Test
    public void intercept() throws ExecutionException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(manager.getVisitorId()).thenReturn(visitorId);
        final CompletableFuture<String> value = new CompletableFuture<>();

        Middleware.Chain chain = new Middleware.Chain() {

            @Override
            public BasePayload payload() {
                return new TrackPayload.Builder().event("test").userId("userId").build();
            }

            @Override
            public void proceed(BasePayload payload) {
                value.complete(payload.integrations().getValueMap("Adobe Analytics").getString("marketingCloudVisitorId"));
            }
        };

        middleware.intercept(chain);
        Assert.assertEquals(visitorId, value.get());
    }

    @Test
    public void intercept_options() throws ExecutionException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(manager.getVisitorId()).thenReturn(visitorId);
        final CompletableFuture<Map<String, Object>> value = new CompletableFuture<>();

        Middleware.Chain chain = new Middleware.Chain() {

            @Override
            public BasePayload payload() {
                Map<String, Object> options = new HashMap<>();
                options.put("option", "option");
                return new TrackPayload.Builder().event("test").userId("userId").integration("Adobe Analytics", options).build();
            }

            @Override
            public void proceed(BasePayload payload) {
                value.complete(payload.integrations().getValueMap("Adobe Analytics"));
            }
        };

        middleware.intercept(chain);
        Assert.assertEquals(visitorId, value.get().get("marketingCloudVisitorId"));
        Assert.assertEquals("option", value.get().get("option"));
    }

    @Test
    public void intercept_disabled() throws ExecutionException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(manager.getVisitorId()).thenReturn(visitorId);
        final CompletableFuture<Boolean> value = new CompletableFuture<>();

        Middleware.Chain chain = new Middleware.Chain() {

            @Override
            public BasePayload payload() {
                return new TrackPayload.Builder().event("test").userId("userId").integration("Adobe Analytics", false).build();
            }

            @Override
            public void proceed(BasePayload payload) {
                value.complete(payload.integrations().getBoolean("Adobe Analytics", true));
            }
        };

        middleware.intercept(chain);
        Assert.assertFalse(value.get());
    }

    @Test
    public void intercept_enabled() throws ExecutionException, InterruptedException {
        String visitorId = "visitorId";
        Mockito.when(manager.getVisitorId()).thenReturn(visitorId);
        final CompletableFuture<String> value = new CompletableFuture<>();

        Middleware.Chain chain = new Middleware.Chain() {

            @Override
            public BasePayload payload() {
                return new TrackPayload.Builder().event("test").userId("userId").integration("Adobe Analytics", true).build();
            }

            @Override
            public void proceed(BasePayload payload) {
                System.out.println(payload.integrations().toJsonObject().toString());
                value.complete(payload.integrations().getValueMap("Adobe Analytics").getString("marketingCloudVisitorId"));
            }
        };

        middleware.intercept(chain);
        Assert.assertEquals(visitorId, value.get());
    }

    @Test
    public void builder() {
        new MarketingCloudMiddleware.Builder().withOrganizationId("organizationId").withRegion(3).withContext(context).build();
    }

    @Test
    public void builder_manager() {
        new MarketingCloudMiddleware.Builder().withVisitorIdManager(manager).build();
    }

    @Test
    public void builder_client() {
        new MarketingCloudMiddleware.Builder().withContext(context).withClient(client).build();
    }

    @Test
    public void builder_executor() {
        new MarketingCloudMiddleware.Builder().withContext(context).withClient(client).withExecutor(Executors.newSingleThreadScheduledExecutor()).build();
    }

    @Test
    public void builder_logger() {
        new MarketingCloudMiddleware.Builder().withContext(context).withClient(client).withLogger(Logger.with(Analytics.LogLevel.INFO)).build();
    }

    @Test
    public void builder_store() {
        new MarketingCloudMiddleware.Builder().withOrganizationId("organizationId").withRegion(3).withContext(context).withStore(store).build();
    }

    @Test
    public void builder_context() {
        new MarketingCloudMiddleware.Builder().withOrganizationId("organizationId").withRegion(3).withStore(store).withContext(context).build();
    }


    @Test(expected = IllegalArgumentException.class)
    public void builder_missingContextWithStore() {
        new MarketingCloudMiddleware.Builder().withOrganizationId("organizationId").withRegion(3).withStore(store).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_missingOrganizationId() {
        new MarketingCloudMiddleware.Builder().withRegion(3).withContext(context).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_missingRegion() {
        new MarketingCloudMiddleware.Builder().withOrganizationId("organizationId").withContext(context).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_missingContext() {
        new MarketingCloudMiddleware.Builder().withOrganizationId("organizationId").withRegion(3).build();
    }
}
