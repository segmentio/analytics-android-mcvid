package com.segment.analytics.android.middlewares.mcvid;

import android.content.Context;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.segment.analytics.integrations.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     * The default implementation of the manager that uses a scheduler for requesting the Visitor ID, and
     * sync it with the device's advertising ID.
     */
    public class AsyncVisitorIdManager implements VisitorIdManager {

        private final static String ANDROID_INTEGRATION_CODE = "DSID_20914";

        static int MAX_RETRIES = 10;
        static boolean AUTOSTART = true;

        private Context context;
        private ScheduledExecutorService executor;
        private AsyncFuture<String> visitorId;
        private MarketingCloudClient client;
        private VisitorIdStore store;
        private ErrorListener errorListener;
        private Logger logger;
        private final Object listenerLock = new Object();

        AsyncFuture<Boolean> synced;

        /**
         * Creates the manager with the default parameters.
         *
         * @param context Application context. Used to retrieve the advertising ID.
         * @param executor Executor where the different calls are going to be executed.
         * @param client Marketing Cloud client to retrieve the Visitor ID and sync advertising ID.
         * @param logger Logger.
         */
        public AsyncVisitorIdManager(Context context, ScheduledExecutorService executor, MarketingCloudClient client, Logger logger) {
            this(context, executor, client, new VisitorIdStore.SharedPreferencesStore(context), logger);
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
        public AsyncVisitorIdManager(Context context, ScheduledExecutorService executor, MarketingCloudClient client, VisitorIdStore store, Logger logger) {
            this.context = context;
            this.executor = executor;
            this.logger = logger;
            this.client = client;
            this.store = store;
            this.errorListener = null;
            this.visitorId = new AsyncFuture<>();
            this.synced = new AsyncFuture<>();
            if (AUTOSTART) {
                this.start();
            }
        }

        void start() {
            this.executor.submit(this.exponentialBackoffRetry(visitorId, this.retrieveVisitorId()));
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
                            synced.reset();
                            executor.submit(exponentialBackoffRetry(synced, syncVisitorId(previousVisitorId)));
                            return previousVisitorId;
                        }
                    } catch (Exception e) {
                        handleError("Error retrieving existing visitor ID: %s. Ignored", e);
                        // We don't retry here.
                    }

                    String visitorId;
                    try {
                        visitorId = client.getVisitorID();
                    } catch (MarketingCloudClient.MarketingCloudException e) {
                        handleError("Error getting visitor ID from service: %s", e);
                        if (e.isBadInput()) {
                            // Do not retry bad input.
                            return null;
                        }
                        // Retry
                        throw e;
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

                    synced.reset();
                    executor.submit(exponentialBackoffRetry(synced, syncVisitorId(visitorId)));

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
                        // According to Adobe, Unknown is applied by default when AuthState is not used with a visitor ID or not explicitly set on each page or app context.
                        client.idSync(visitorId, ANDROID_INTEGRATION_CODE, advertisingId, MCVIDAuthState.MCVIDAuthStateUnknown);
                    } catch (MarketingCloudClient.MarketingCloudException e) {
                        handleError("Error syncing visitor ID and advertising ID: %s", e);
                        if (e.isBadInput()) {
                            // Do not retry bad input.
                            return null;
                        }
                        // Retry
                        throw e;
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
         * Performs the operation, storing the result in the future object. If the operation fails, it will retry
         * using a simple backoff exponential algorithm.
         *
         * @param result Future where to store the result.
         * @param operation Operation to perform.
         * @param <T> Type of the value.
         *
         * @return A runnable to execute.
         */
        protected <T> Runnable exponentialBackoffRetry(AsyncFuture<T> result, Callable<T> operation) {
            return exponentialBackoffRetry(result, operation, 0);
        }

        /**
         * Performs the operation, storing the result in the future object. If the operation fails, it will retry
         * using a simple backoff exponential algorithm.
         *
         * @param result Future where to store the result.
         * @param operation Operation to perform.
         * @param retryNumber Number of the current retry.
         * @param <T> Type of the value.
         *
         * @return A runnable to execute.
         */
        protected <T> Runnable exponentialBackoffRetry(final AsyncFuture<T> result, final Callable<T> operation, final int retryNumber) {

            return new Runnable() {

                @Override
                public void run() {
                    T value;
                    try {
                        // Perform the action
                        value = operation.call();
                    } catch (Exception e) {
                        if (retryNumber > MAX_RETRIES) {
                            result.putException(e);
                            return;
                        }

                        // Schedule next retry (no need for random delays)
                        long delay = Math.round(Math.pow(2, retryNumber));
                        executor.schedule(exponentialBackoffRetry(result, operation, retryNumber + 1), delay, TimeUnit.SECONDS);
                        return;
                    }

                    result.put(value);
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
                String vId;
                try {
                    vId = visitorId.get();
                } catch (InterruptedException | ExecutionException e) {
                    handleError("Error getting visitor ID: %s. Retrying", e);

                    // Retry
                    visitorId.reset();
                    this.executor.submit(this.exponentialBackoffRetry(visitorId, this.retrieveVisitorId()));
                    return null;
                }

                // Only to perform a retry if needed.
                if (synced.isDone()) {
                    try {
                        synced.get();
                    } catch (InterruptedException | ExecutionException e) {
                        handleError("Error syncing visitor ID: %s. Retrying", e);

                        // Retry
                        synced.reset();
                        this.executor.submit(this.exponentialBackoffRetry(synced, this.syncVisitorId(vId)));
                    }
                }

                return vId;
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

    /**
     * Basic implementation of a future for async operations. It only works for sequential
     * operations (one thread setting the result).
     * @param <T> Result of the future.
     */
    class AsyncFuture<T> implements Future<T> {

        private CountDownLatch latch;
        private Exception exception;
        private T value;

        public AsyncFuture() {
            latch = new CountDownLatch(1);
        }

        /**
         * Not supported.
         * @param mayInterruptIfRunning Ignored.
         * @throws UnsupportedOperationException always.
         * @return nothing.
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("Cancel not supported");
        }

        /**
         * Not supported.
         * @throws UnsupportedOperationException always.
         * @return nothing.
         */
        @Override
        public boolean isCancelled() {
            throw new UnsupportedOperationException("Cancel not supported");
        }

        @Override
        public boolean isDone() {
            return latch.getCount() == 0;
        }

        /**
         * Blocks the thread until the value is available.
         *
         * @throws ExecutionException If the execution has thrown any exception.
         * @throws InterruptedException If the thread got interrupted.
         * @return The value.
         */
        @Override
        public T get() throws ExecutionException, InterruptedException {
            latch.await();
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return value;
        }

        /**
         * Blocks the thread until the value is available. Or times out.
         * @param timeout Timeout.
         * @param unit Unit for the timeout.
         *
         * @throws ExecutionException If the execution has thrown any exception.
         * @throws InterruptedException If the thread got interrupted.
         * @throws TimeoutException If the value is not available for the defined timeout.
         *
         * @return The value.
         */
        @Override
        public T get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
            if (latch.await(timeout, unit)) {
                if (exception != null) {
                    throw new ExecutionException(exception);
                }
                return value;
            } else {
                throw new TimeoutException();
            }
        }

        void put(T value) {
            this.value = value;
            latch.countDown();
        }

        void putException(Exception exception) {
            this.exception = exception;
            latch.countDown();
        }

        synchronized void reset() {
            latch = new CountDownLatch(1);
            value = null;
            exception = null;
        }
    }

}
