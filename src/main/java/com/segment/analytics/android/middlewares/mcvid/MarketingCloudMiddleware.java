package com.segment.analytics.android.middlewares.mcvid;

import android.app.Activity;
import android.content.Context;

import com.segment.analytics.Analytics;
import com.segment.analytics.Middleware;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A Segment Middleware for injecting Adobe Marketing Cloud Visitor IDs to
 * events.
 */
public class MarketingCloudMiddleware implements Middleware {

    private final static String INTEGRATIONS_KEY = "integrations";
    private final static String ADOBE_ANALYTICS_KEY = "Adobe Analytics";
    private final static String MCVID_KEY = "marketingCloudVisitorId";

    private VisitorIdManager manager;
    private MarketingCloudClient client;

    /**
     * Constructs the middleware with the default configuration and implementation.
     *
     * @param activity Android activity.
     * @param organizationId Adobe Organization ID (ex. 11AABBBC67777F0000FFF)
     * @param region Datacenter region (ex. 3)
     */
    public MarketingCloudMiddleware(Activity activity, String organizationId, int region) {
        client = new MarketingCloudClient.HttpClient(organizationId, region);
        manager = new VisitorIdManager.AsyncVisitorIdManager(activity, Executors.newSingleThreadScheduledExecutor(), client, Logger.with(Analytics.LogLevel.INFO));
    }

    /**
     * Constructs the middleware. Most of the cases you want to use the builder or the other
     * constructor. Only useful for testing.
     *
     * @param manager Visitor ID manager.
     */
    MarketingCloudMiddleware(VisitorIdManager manager) {
        this.manager = manager;
    }

    /**
     * Injects the Visitor ID in the integration options if it's not disabled. If the Visitor ID is not
     * available yet, it sends the event without it.
     *
     * @param chain Middleware chain.
     */
    @Override
    public void intercept(Chain chain) {
        BasePayload payload = chain.payload();
        ValueMap integrations = payload.integrations();
        if (integrations == null || Collections.emptyMap().equals(integrations)) {
            // Empty map does not allow to put values.
            integrations = new ValueMap();
        } else if (Boolean.FALSE.equals(integrations.get(ADOBE_ANALYTICS_KEY))) {
            // Disabled, continue
            chain.proceed(payload);
            return;
        } else {
            // Sometimes integrations is a unmodifiable map if the event is constructed with a Builder.
            // Sadly, we don't know exactly when that is, so this Middleware need to copy the integrations
            // map for each event.
            integrations = new ValueMap(new LinkedHashMap<>(integrations));
        }

        payload.putValue(INTEGRATIONS_KEY, integrations);

        ValueMap adobeOptions = integrations.getValueMap(ADOBE_ANALYTICS_KEY);
        if (adobeOptions == null || Collections.emptyMap().equals(adobeOptions)) {
            // It's not a ValueMap, it's missing or it's empty.
            adobeOptions = new ValueMap();
        } else {
            // Same here with the Adobe Analytics options
            adobeOptions = new ValueMap(new LinkedHashMap<>(adobeOptions));
        }

        integrations.put(ADOBE_ANALYTICS_KEY, adobeOptions);

        String visitorId = manager.getVisitorId();
        if (visitorId != null) {
            // Sometimes the options is a unmodifiable map if the event is constructed with a Builder.
            adobeOptions.put(MCVID_KEY, visitorId);
        }

        chain.proceed(payload);
    }

    /**
     * Retrieves the Marketing Cloud client. Only available when using the default Visitor Id Manager.
     *
     * @return The client.
     */
    public MarketingCloudClient getClient() {
        return client;
    }

    /**
     * Retrieves the visitor ID. Useful to make your own id syncs.
     *
     * @return Visitor Id if available, null otherwise.
     */
    public String getVisitorId() {
        return manager.getVisitorId();
    }

    /**
     * Allows to create the middleware with extra options.
     */
    public static class Builder {

        private String organizationId;
        private int region;
        private Activity activity;
        private Context context;
        private MarketingCloudClient client;
        private ScheduledExecutorService executor;
        private VisitorIdStore store;
        private VisitorIdManager manager;
        private Logger logger;

        public Builder() {}

        /**
         * Builds the instance.
         *
         * @throws IllegalArgumentException if some required configuration is missing.
         * @return The middleware.
         */
        public MarketingCloudMiddleware build() {
            if (manager != null) {
                return new MarketingCloudMiddleware(manager);
            }

            if (logger == null) {
                logger = Logger.with(Analytics.LogLevel.INFO);
            }

            if (client == null) {
                if (organizationId == null || region == 0) {
                    throw new IllegalArgumentException("Adobe Organization Id and Region are required");
                }
                client = new MarketingCloudClient.HttpClient(organizationId, region);
            }

            if (store == null) {
                if (activity == null) {
                    throw new IllegalArgumentException("Either Activity or the Store implementation is required");
                }
                store = new VisitorIdStore.SharedPreferencesStore(activity);
            }

            if (context == null) {
                if (activity == null) {
                    throw new IllegalArgumentException("Either Activity or Context is required");
                }
                context = activity.getApplicationContext();
            }

            if (executor == null) {
                executor = Executors.newSingleThreadScheduledExecutor();
            }

            manager = new VisitorIdManager.AsyncVisitorIdManager(context, executor, client, store, logger);

            MarketingCloudMiddleware middleware = new MarketingCloudMiddleware(manager);
            middleware.client = client;
            return middleware;
        }


        /**
         * Sets the Adobe organization Id. This value is provided by Adobe.
         *
         * @param organizationId Adobe Organization Id.
         * @return The builder instance.
         */
        public Builder withOrganizationId(String organizationId) {
            assertArgument("organization ID", organizationId);
            this.organizationId = organizationId;
            return this;
        }

        /**
         * Sets the region (dcs_region).
         *
         * For more information: https://marketing.adobe.com/resources/help/en_US/aam/dcs-regions.html
         *
         * @param region Adobe Marketing Cloud region.
         * @return The builder instance.
         */
        public Builder withRegion(int region) {
            this.region = region;
            return this;
        }

        /**
         * Sets the implementation for the Marketing Cloud client. If this method is used,
         * withRegion() and withOrganizationId() with have no effect.
         *
         * @param client Marketing Cloud client.
         * @return The builder instance.
         */
        public Builder withClient(MarketingCloudClient client) {
            assertArgument("client", client);
            this.client = client;
            return this;
        }

        /**
         * Sets the activity. This call is required unless you use a custom store.
         *
         * @param activity Android activity.
         * @return The builder instance.
         */
        public Builder withActivity(Activity activity) {
            assertArgument("activity", activity);
            this.activity = activity;
            return this;
        }

        /**
         * Sets the context. This is not required if the activity has been set.
         *
         * @param context Context.
         * @return The builder instance.
         */
        public Builder withContext(Context context) {
            assertArgument("context", context);
            this.context = context;
            return this;
        }

        /**
         * Sets the implementation of the visitor ID store.
         *
         * @param store Visitor ID Store.
         * @return The builder instance.
         */
        public Builder withStore(VisitorIdStore store) {
            assertArgument("store", store);
            this.store = store;
            return this;
        }

        /**
         * Sets the executor where all the client requests are made. By default, is a
         * `Executors.newSingleThreadExecutor()`.
         *
         * @param executor Executor.
         * @return The builder instance.
         */
        public Builder withExecutor(ScheduledExecutorService executor) {
            assertArgument("executor", executor);
            this.executor = executor;
            return this;
        }

        /**
         * Allows to use a custom implementation of the manager. If this method is called, the
         * others in this builder have no effect. Also, the middleware won't have access to the client
         * directly (middleware.getClient() will return always null).
         *
         * @param manager Custom visitor ID Manager.
         * @return The builder instance.
         */
        public Builder withVisitorIdManager(VisitorIdManager manager) {
            assertArgument("manager", manager);
            this.manager = manager;
            return this;
        }

        /**
         * Sets the custom logger. If this method is not called, we use `Logger.with(LogLevel.INFO)`.
         *
         * @param logger Custom logger.
         * @return The builder instance.
         */
        public Builder withLogger(Logger logger) {
            assertArgument("logger", logger);
            this.logger = logger;
            return this;
        }

        /**
         * Makes sure the argument is not null.
         *
         * @throws IllegalArgumentException when the argument is null.
         * @param name Name of the argument.
         * @param object Value.
         */
        private void assertArgument(String name, Object object) {
            if (object == null) {
                throw new IllegalArgumentException(String.format("Argument %s can not be null", name));
            }
        }

    }
}
