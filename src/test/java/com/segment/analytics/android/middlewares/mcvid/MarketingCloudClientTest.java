package com.segment.analytics.android.middlewares.mcvid;

import android.net.Uri;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class MarketingCloudClientTest {

    private final String DEFAULT_TEST_ORGANIZATION_ID = "organizationId";
    private final String DEFAULT_TEST_REGION = "3";
    private final String DEFAULT_INTEGRATION_CODE = "DSID_20914";
    private final String DEFAULT_CUSTOMER_ID = "customerId";

    private int region;
    private String organizationId;
    private String customerId;
    private MarketingCloudClient.HttpClient client;


    @Before
    public void initialize() {

        organizationId = System.getProperty("test.adobe.organization_id", DEFAULT_TEST_ORGANIZATION_ID);
        customerId = System.getProperty("test.adobe.customer_id", DEFAULT_CUSTOMER_ID);

        String regionVar = System.getProperty("test.adobe.region", DEFAULT_TEST_REGION);
        if (regionVar != null && !regionVar.equals("")) {
            region = Integer.parseInt(regionVar);
        }

        client = new MarketingCloudClient.HttpClient(organizationId, region);
    }

    @Test
    public void createVisitor() throws MarketingCloudClient.MarketingCloudException, IOException {
        client.getVisitorID();
    }

    @Test(expected = MarketingCloudClient.MarketingCloudException.class)
    public void createVisitor_invalidOrganizationId() throws MarketingCloudClient.MarketingCloudException, IOException {
        MarketingCloudClient client = new MarketingCloudClient.HttpClient("123123123123123123123123", region);
        client.getVisitorID();
    }

    @Test
    public void idSync() throws MarketingCloudClient.MarketingCloudException, IOException {
        String visitorId = client.getVisitorID();
        final String expectedUrl = String.format("https://dpm.demdex.net/id?d_mid=%s&d_ver=2&dcs_region=%d&d_orgid=%s@AdobeOrg&d_rtbd=json&d_cid_ic=%s%%01%s", visitorId, region, organizationId, DEFAULT_INTEGRATION_CODE, customerId);

        MarketingCloudClient.HttpClient spy = Mockito.spy(client);
        spy.idSync(visitorId, DEFAULT_INTEGRATION_CODE, customerId);
        Mockito.verify(spy, Mockito.times(1)).sendRequest(Mockito.argThat(new ArgumentMatcher<URL>() {
            @Override
            public boolean matches(URL argument) {
                return expectedUrl.equals(argument.toString());
            }
        }));
    }

    @Test(expected = MarketingCloudClient.MarketingCloudException.class)
    public void idSync_invalidVisitorId() throws MarketingCloudClient.MarketingCloudException, IOException {
        String vid = "this is invalid big time";
        client.idSync(vid, DEFAULT_INTEGRATION_CODE, customerId);
    }

    @Test
    public void readResponse() throws MarketingCloudClient.MarketingCloudException, IOException {
        String input = "{ \"d_mid\": \"testVisitorId\" }";
        client.readResponse(new ByteArrayInputStream(input.getBytes()));
    }

    @Test(expected = MarketingCloudClient.MarketingCloudException.class)
    public void readResponse_errors() throws MarketingCloudClient.MarketingCloudException, IOException {
        String input = "{ \"errors\": [ { \"msg\": \"my error\", \"code\": 12 } ] }";
        client.readResponse(new ByteArrayInputStream(input.getBytes()));
    }

    @Test(expected = MarketingCloudClient.MarketingCloudException.class)
    public void readResponse_eofJson() throws MarketingCloudClient.MarketingCloudException, IOException {
        String input = "{ \"errors\": [ { ";
        client.readResponse(new ByteArrayInputStream(input.getBytes()));
    }

    @Test(expected = MarketingCloudClient.MarketingCloudException.class)
    public void readResponse_invalidJson() throws MarketingCloudClient.MarketingCloudException, IOException {
        String input = "sdad [ { ";
        client.readResponse(new ByteArrayInputStream(input.getBytes()));
    }

    @Test(expected = MarketingCloudClient.MarketingCloudException.class)
    public void sendRequest_not200() throws MarketingCloudClient.MarketingCloudException, IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getResponseCode()).thenReturn(500);

        client.sendRequest(mockUrl(connection));
    }

    @Test(expected = MarketingCloudClient.MarketingCloudException.class)
    public void sendRequest_invalidContentType() throws MarketingCloudClient.MarketingCloudException, IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getHeaderField("Content-Type")).thenReturn("text/plain");

        client.sendRequest(mockUrl(connection));
    }

    @Test
    public void createVisitorIdUrl() {

        String builtUrl = client.createUrl(Collections.EMPTY_MAP).toString();
        String expectedUrl = String.format("https://dpm.demdex.net/id?d_ver=2&dcs_region=%d&d_orgid=%s@AdobeOrg&d_rtbd=json", region, organizationId);
        Assert.assertEquals(expectedUrl, builtUrl);

        Uri uri = Uri.parse(builtUrl);
        Assert.assertEquals("https", uri.getScheme());
        Assert.assertEquals("dpm.demdex.net", uri.getAuthority());
        Assert.assertEquals("/id", uri.getPath());
        String expectedQuery = String.format("d_ver=2&dcs_region=%d&d_orgid=%s@AdobeOrg&d_rtbd=json", region, organizationId);
        Assert.assertEquals(expectedQuery, uri.getEncodedQuery());
    }

    @Test
    public void idSyncUrl() {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("d_mid", "visitorId");
        parameters.put("d_cid_ic", customerId);

        String builtUrl = client.createUrl(parameters).toString();
        String expectedUrl = String.format("https://dpm.demdex.net/id?d_mid=visitorId&d_ver=2&dcs_region=%d&d_orgid=%s@AdobeOrg&d_rtbd=json&d_cid_ic=%s", region, organizationId, customerId);
        Assert.assertEquals(expectedUrl, builtUrl);

        Uri uri = Uri.parse(builtUrl);
        Assert.assertEquals("https", uri.getScheme());
        Assert.assertEquals("dpm.demdex.net", uri.getAuthority());
        Assert.assertEquals("/id", uri.getPath());
        String expectedQuery = String.format("d_mid=visitorId&d_ver=2&dcs_region=%d&d_orgid=%s@AdobeOrg&d_rtbd=json&d_cid_ic=%s", region, organizationId, customerId);
        Assert.assertEquals(expectedQuery, uri.getEncodedQuery());
    }

    private URL mockUrl(final HttpURLConnection connection) throws MalformedURLException {
        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return connection;
            }
        };

        return new URL("foo", "bar", 99, "/foobar", handler);
    }
}
