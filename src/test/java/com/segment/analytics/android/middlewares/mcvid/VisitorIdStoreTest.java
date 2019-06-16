package com.segment.analytics.android.middlewares.mcvid;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.Charset;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
public class VisitorIdStoreTest {

    @Test
    public void get() {
        String key = randomKey();
        String visitorId = "visitorId";

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        sharedPreferences.edit().putString(key, visitorId).commit();

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);

        Assert.assertEquals(visitorId, store.get());
    }

    @Test
    public void get_missing() {
        String key = randomKey();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);

        Assert.assertNull(store.get());
    }

    @Test
    public void getSyncedAdvertisingId() {
        String key = randomKey();
        String visitorId = "visitorId";
        String advertisingId = "advertisingId";

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        sharedPreferences.edit().putString(key, visitorId).commit();
        sharedPreferences.edit().putString(key + "_advertising_id", advertisingId).commit();

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);

        Assert.assertEquals(advertisingId, store.getSyncedAdvertisingId());
    }

    @Test
    public void getSyncedAdvertisingId_missing() {
        String key = randomKey();
        String visitorId = "visitorId";

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        sharedPreferences.edit().putString(key, visitorId).commit();

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);

        Assert.assertNull(store.getSyncedAdvertisingId());
    }

    @Test
    public void getSyncedAdvertisingId_missingVisitorId() {
        String key = randomKey();
        String advertisingId = "advertisingId";

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        sharedPreferences.edit().putString(key + "_advertising_id", advertisingId).commit();

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);

        Assert.assertNull(store.getSyncedAdvertisingId());
    }


    @Test
    public void set() {

        String key = randomKey();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);
        store.set("visitorId");

        Assert.assertEquals("visitorId", sharedPreferences.getString(key, ""));
    }

    @Test
    public void set_override() {

        String key = randomKey();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        sharedPreferences.edit().putString(key, "12345").commit();

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);
        store.set("visitorId");

        Assert.assertEquals("visitorId", sharedPreferences.getString(key, ""));
    }

    @Test
    public void setSyncedAdvertisingId() {

        String key = randomKey();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);
        store.setSyncedAdvertisingId("advertisingId");

        Assert.assertEquals("advertisingId", sharedPreferences.getString(key + "_advertising_id", ""));
    }

    @Test
    public void setSyncedAdvertisingId_override() {

        String key = randomKey();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        sharedPreferences.edit().putString(key + "_advertising_id", "12345").commit();

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);
        store.setSyncedAdvertisingId("advertisingId");

        Assert.assertEquals("advertisingId", sharedPreferences.getString(key + "_advertising_id", ""));
    }

    @Test
    public void delete() {
        String key = randomKey();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        sharedPreferences.edit().putString(key, "visitorId").commit();
        sharedPreferences.edit().putString(key + "_advertising_id", "advertisingId").commit();

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);
        store.delete();

        Assert.assertEquals("empty", sharedPreferences.getString(key, "empty"));
        Assert.assertEquals("empty", sharedPreferences.getString(key + "_advertising_id", "empty"));
    }

    @Test
    public void delete_notExist() {
        String key = randomKey();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());

        VisitorIdStore store = new VisitorIdStore.SharedPreferencesStore(sharedPreferences, key);
        store.delete();

        Assert.assertEquals("empty", sharedPreferences.getString(key, "empty"));
        Assert.assertEquals("empty", sharedPreferences.getString(key + "_advertising_id", "empty"));
    }

    private String randomKey() {
        byte[] array = new byte[7];
        new Random().nextBytes(array);
        return new String(array, Charset.forName("UTF-8"));
    }
}
