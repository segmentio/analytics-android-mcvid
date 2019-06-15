package com.segment.analytics.android.middlewares.mcvid;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.segment.analytics.integrations.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Main API to get the Visitor ID. Most implementations should use directly
 * this, rather than the client or the store.
 */
public interface VisitorIdManager {

    /**
     * Retrieves Adobe's Marketing Cloud Visitor ID for the device's advertising ID. This
     * call is thread-safe and not blocking.
     *
     * @return The visitor ID. It could return <code>null</code> if the visitor ID is not
     * available yet.
     */
    public String getVisitorId();

    /**
     * The default implementation of the manager that uses an executor for requesting the Visitor ID, and
     * the Shared Preferences store.
     */
    public class ExecutorVisitorIdManager implements VisitorIdManager {

        private Context context;
        private ExecutorService executor;
        private Future<String> visitorId;
        private MarketingCloudClient client;
        private VisitorIdStore store;
        private ErrorListener errorListener;
        private Logger logger;
        private final Object listenerLock = new Object();

        Future<Boolean> synced;

        /**
         * Creates the manager with the default parameters.
         *
         * @param activity Main activity. Used to retrieve the preferences store and context.
         * @param executor Executor where the different calls are going to be executed.
         * @param client Marketing Cloud client to retrieve the Visitor ID and sync advertising ID.
         * @param logger Logger.
         */
        public ExecutorVisitorIdManager(Activity activity, ExecutorService executor, MarketingCloudClient client, Logger logger) {
            this(activity.getApplicationContext(), executor, client, new VisitorIdStore.SharedPreferencesStore(activity), logger);
        }

        /**
         * Creates the manager.
         *
         * @param context Application context. Used to retrieve the advertising ID.
         * @param executor Executor where the different calls are going to be executed.
         * @param client Marketing Cloud client to retrieve the Visitor ID and sync advertising ID.
         * @param store Local store to get and set the visitor ID between different application executions.
         * @param logger Logger.
         */
        public ExecutorVisitorIdManager(Context context, ExecutorService executor, MarketingCloudClient client, VisitorIdStore store, Logger logger) {
            this.context = context;
            this.executor = executor;
            this.logger = logger;
            this.client = client;
            this.store = store;
            this.errorListener = null;
            this.visitorId = this.executor.submit(this.retrieveVisitorId());
        }

        /**
         * Retrieves the visitor ID from local storage or the client. It also schedules the ID sync when needed.
         *
         * @return The action for the executor.
         */
        protected Callable<String> retrieveVisitorId() {
            return new Callable<String>() {
                @Override
                public String call() throws Exception {

                    try {
                        String previousVisitorId = store.get();
                        if (previousVisitorId != null) {
                            synced = executor.submit(syncVisitorId(previousVisitorId));
                            return previousVisitorId;
                        }
                    } catch (Exception e) {
                        handleError("Error retrieving existing visitor ID: %s. Ignored", e);
                        // We don't retry here.
                    }

                    String visitorId;
                    try {
                        // TODO: Implement exponential backoff retries
                        visitorId = client.getVisitorID();
                    } catch (Exception e) {
                        handleError("Error getting visitor ID from service: %s", e);
                        // Retry
                        throw e;
                    }

                    try {
                        store.set(visitorId);
                    } catch (Exception e) {
                        handleError("Error storing visitor ID: %s. Ignored", e);
                    }

                    synced = executor.submit(syncVisitorId(visitorId));

                    return visitorId;
                }
            };
        }

        /**
         * Syncs the provider Visitor ID with the device's advertising ID if possible or required.
         *
         * @param visitorId Marketing Cloud visitor ID.
         * @return The action for the executor.
         */
        protected Callable<Boolean> syncVisitorId(final String visitorId) {
            return new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {

                    String advertisingId = null;
                    try {
                        advertisingId = getAdvertisingId();
                    } catch (Exception e) {
                        handleError("Error thrown retrieving advertising ID: %s. Ignored", e);
                    }

                    if (advertisingId == null) {
                        return Boolean.FALSE;
                    }

                    String previousSyncedAdvertisingId = null;
                    try {
                        previousSyncedAdvertisingId = store.getSyncedAdvertisingId();
                    } catch (Exception e) {
                        handleError("Error thrown retrieving previous synced advertising ID: %s. Ignored", e);
                    }

                    if (advertisingId.equals(previousSyncedAdvertisingId)) {
                        return Boolean.TRUE;
                    }

                    try {
                        // TODO: Implement exponential backoff retries
                        client.idSync(visitorId, advertisingId);
                    } catch (Exception e) {
                        handleError("Error syncing visitor ID and advertising ID: %s", e);
                        // Retry
                        throw e;
                    }

                    try {
                        store.setSyncedAdvertisingId(advertisingId);
                    } catch (Exception e) {
                        handleError("Error storing advertising ID: %s. Ignored", e);
                    }

                    return Boolean.TRUE;
                }
            };
        }

        /**
         * Retrieves Adobe's Marketing Cloud Visitor ID for the device's advertising ID. This
         * call is thread-safe and not blocking.
         *
         * @return The visitor ID. It could return <code>null</code> if the visitor ID is not
         * available yet.
         */
        @Override
        public String getVisitorId() {
            if (visitorId.isDone()) {
                try {
                    String vId = visitorId.get();

                    // Only to perform a retry if needed.
                    if (synced.isDone()) {
                        try {
                            synced.get();
                        } catch (InterruptedException | ExecutionException e) {
                            handleError("Error syncing visitor ID: %s. Retrying", e);

                            // Retry
                            synced = this.executor.submit(this.syncVisitorId(vId));
                        }
                    }

                    return vId;
                } catch (InterruptedException | ExecutionException e) {
                    handleError("Error getting visitor ID: %s. Retrying", e);

                    // Retry
                    visitorId = this.executor.submit(this.retrieveVisitorId());
                }
            }
            return null;
        }

        /**
         * Retrieves the advertising ID if present. This method should be executed in a different thread, since it's
         * quite slow.
         *
         * @return Advertising ID if available. <code>null</code> otherwise, or if the user has enabled limit ad tracking.
         */
        protected String getAdvertisingId() {
            AdvertisingIdClient.Info adInfo;
            try {
                adInfo = AdvertisingIdClient.getAdvertisingIdInfo(this.context);
            } catch (Exception e) {
                handleError("Error thrown retrieving advertising ID information: %s. Ignored", e);
                return null;
            }

            String advertisingId;
            boolean limitAdTracking;

            try {
                advertisingId = adInfo.getId();
                limitAdTracking = adInfo.isLimitAdTrackingEnabled();
            } catch (Exception e){
                handleError("Error thrown retrieving advertising ID: %s. Ignored", e);
                return null;
            }

            if (limitAdTracking) {
                return null;
            }

            return advertisingId;
        }

        /**
         * Use this to perform some custom action when an error is received. This method is thread-safe.
         * @param errorListener Error listener.
         */
        public void setErrorListener(ErrorListener errorListener) {
            synchronized (listenerLock) {
                this.errorListener = errorListener;
            }
        }

        /**
         * Logs the error and executes the listener (if exists) in the same execution thread.
         *
         * @param formatMessage Message using String.format() formatting options.
         * @param exception Exception to be handled.
         */
        protected void handleError(String formatMessage, Exception exception) {
            this.logger.debug(formatMessage, exception.getMessage());
            ErrorListener listener;

            synchronized (listenerLock) {
                listener = errorListener;
            }

            if (listener != null) {
                listener.onError(exception);
            }
        }

    }

    /**
     * A error listener to trigger actions when the client receives an error.
     *
     * IMPORTANT: The listener gets executed in the same thread that the executor, and might
     * block other operations. Do not do heavy stuff on it.
     */
    public interface ErrorListener {

        /**
         * Executed when any kind of error gets thrown by the client or store.
         * @param exception Exception.
         */
        public void onError(Exception exception);

    }

}
