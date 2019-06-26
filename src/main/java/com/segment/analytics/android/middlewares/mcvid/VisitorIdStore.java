package com.segment.analytics.android.middlewares.mcvid;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A common interface to store and retrieve Adobe Marketing Cloud visitor IDs between application
 * restarts.
 */
public interface VisitorIdStore {

    /**
     * Retrieves the visitor ID.
     *
     * @return The Visitor ID if found, null otherwise.
     *
     * Note: Due to API limitations, we can't use Optional<String>.
     */
    public String get();

    /**
     * Retrieves the last advertising ID associated with the stored visitor ID.
     *
     * @return The last advertising ID if found, null otherwise or if visitor ID is not stored.
     */
    public String getSyncedAdvertisingId();

    /**
     * Stores or overrides the visitor ID with the correspondent Advertising ID.
     *
     * @param visitorId Adobe Marketing Cloud visitor ID.
     */
    public void set(String visitorId);

    /**
     * Stores the last advertising ID synced with Visitor ID. Call this only if an successful ID sync
     * has been performed.
     *
     * @param advertisingId Device's Advertising ID.
     */
    public void setSyncedAdvertisingId(String advertisingId);

    /**
     * Deletes the visitor ID and advertising ID if exist.
     */
    public void delete();

    /**
     * A thread-safe implementation of the store using the SharedPreferences API.
     */
    public class SharedPreferencesStore implements VisitorIdStore {

        private final static String DEFAULT_VALUE = "";
        private final static String DEFAULT_KEY = "mcvid";
        private final static String DEFAULT_SHARED_PREFERENCES = "mcvid";

        private String key;
        private String advertisingIdKey;
        private SharedPreferences preferences;

        /**
         * Creates a new store using the provided preferences.
         *
         * @param context Application context to retrieve the shared preferences.
         */
        public SharedPreferencesStore(Context context) {
            this(context.getSharedPreferences(DEFAULT_SHARED_PREFERENCES, Context.MODE_PRIVATE), DEFAULT_KEY);
        }

        /**
         * Creates a new store using the key and the provided preferences.
         *
         * @param preferences Shared preferences where to store the id.
         * @param key Key to store the Visitor ID.
         */
        public SharedPreferencesStore(SharedPreferences preferences, String key) {
            this.preferences = preferences;
            this.key = key;
            this.advertisingIdKey = key + "_advertising_id";
        }

        /**
         * Retrieves the visitor ID.
         *
         * @return The Visitor ID if found, null otherwise.
         *
         * Note: Due to API limitations, we can't use Optional<String>.
         */
        @Override
        public String get() {

            String value = preferences.getString(key, DEFAULT_VALUE);
            if (DEFAULT_VALUE.equals(value)) {
                return null;
            }

            return value;
        }

        /**
         * Retrieves the last advertising ID associated with the stored visitor ID.
         *
         * @return The last advertising ID if found, null otherwise or if visitor ID is not stored.
         */
        @Override
        public String getSyncedAdvertisingId() {
            String visitorId = preferences.getString(key, DEFAULT_VALUE);
            if (DEFAULT_VALUE.equals(visitorId)) {
                return null;
            }

            String advertisingId = preferences.getString(advertisingIdKey, DEFAULT_VALUE);
            if (DEFAULT_VALUE.equals(advertisingId)) {
                return null;
            }

            return advertisingId;
        }

        /**
         * Stores or overrides the visitor ID with the correspondent Advertising ID.
         *
         * @param visitorId Adobe Marketing Cloud visitor ID.
         */
        @Override
        public void set(String visitorId) {
            this.preferences.edit().putString(key, visitorId).apply();
        }

        /**
         * Stores the last advertising ID synced with Visitor ID. Call this only if an successful ID sync
         * has been performed.
         *
         * @param advertisingId Device's Advertising ID.
         */
        @Override
        public void setSyncedAdvertisingId(String advertisingId) {
            this.preferences.edit().putString(advertisingIdKey, advertisingId).apply();
        }

        /**
         * Deletes the visitor ID and advertising ID if exist.
         */
        @Override
        public void delete() {
            this.preferences.edit().remove(key).apply();
            this.preferences.edit().remove(advertisingIdKey).apply();
        }
    }
}
