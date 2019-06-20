package com.segment.analytics.android.middlewares.mcvid;

import android.net.Uri;
import android.util.JsonReader;
import android.util.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client to create Marketing Cloud Visitor IDs and perform the sync between that an other user or device IDs.
 */
public interface MarketingCloudClient {

    /**
     * Retrieves a new Visitor ID.
     * @return Visitor ID.
     *
     * @throws MarketingCloudException if the response from the service is unexpected
     * @throws IOException if an I/O exception occurs
     */
    public String getVisitorID() throws MarketingCloudException, IOException;

    /**
     * Performs an ID sync between the provided Visitor ID and other customer ID.
     * @param visitorId Marketing Cloud Visitor ID (generated with the method above).
     * @param integrationCode Code associated with the integration (ex. Android: DSID_20914)
     * @param customerId Other user/device ID (advertisingId, userId, etc).
     *
     * @throws MarketingCloudException if the response from the service is unexpected
     * @throws IOException if an I/O exception occurs
     */
    public void idSync(String visitorId, String integrationCode, String customerId) throws MarketingCloudException, IOException;

    /**
     * Represents any unexpected response from the Marketing Cloud service. Usually anything that is not a 200
     * if using the default HTTP client.
     */
    public class MarketingCloudException extends Exception {

        private boolean badInput;

        public MarketingCloudException(String message) {
            this(message, false);
        }

        public MarketingCloudException(String message, boolean badInput) {
            super(message);
            this.badInput = badInput;
        }

        public MarketingCloudException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Set when the exception was caused by bad input in the request.
         *
         * @return <code>true</code> if the exception was caused by bad input (invalid
         * organization ID, invalid advertisingId, etc). <code>false</code> otherwise.
         */
        public boolean isBadInput() {
            return badInput;
        }
    }

    /**
     * Default implementation of the client, using Android's default HTTP client.
     * All requests are made in the same thread where are called.
     */
    public class HttpClient implements MarketingCloudClient {

        private final static String SCHEME = "https";
        private final static String HOST = "dpm.demdex.net";
        private final static String PATH = "id";
        private final static String FORMAT = "json";
        private final static int VERSION = 2;
        private final static String ORGANIZATION_SUFFIX = "@AdobeOrg";
        private final static String VID_FIELD = "d_mid";
        private final static String ERRORS_FIELD = "errors";
        private final static String ERROR_MSG_FIELD = "msg";
        private final static String CODE_FIELD = "code";
        private final static String VERSION_FIELD = "d_ver";
        private final static String FORMAT_FIELD = "d_rtbd";
        private final static String ORGANIZATION_FIELD = "d_orgid";
        private final static String REGION_FIELD = "dcs_region";
        private final static String CUSTOMER_ID_FIELD = "d_cid_ic";
        private final static String CHARSET = "UTF-8";
        private final static String RESPONSE_CHARSET = "application/json;charset=utf-8";

        private String organizationId;
        private int region;

        /**
         * Constructor.
         * @param organizationId Organization ID provided by Adobe.
         * @param region Data Center region.
         */
        public HttpClient(String organizationId, int region) {
            this.organizationId = organizationId;
            this.region = region;
        }

        @Override
        public String getVisitorID() throws MarketingCloudException, IOException {
            URL url = createUrl(Collections.EMPTY_MAP);
            return sendRequest(url);
        }

        @Override
        public void idSync(String visitorId, String integrationCode, String customerId) throws MarketingCloudException, IOException {
            Map<String, String> parameters = new HashMap<>();
            parameters.put(VID_FIELD, visitorId);
            parameters.put(CUSTOMER_ID_FIELD, String.format("%s%%01%s", integrationCode, customerId));

            URL url = createUrl(parameters);
            sendRequest(url);
        }

        /**
         * Performs a GET request to the provided URL and tries to parse the response as JSON only if the code returned is 200.
         *
         * @param url URL to send the request.
         *
         * @return The Visitor ID returned as part of the JSON response.
         *
         * @throws MarketingCloudException if the response from the service is unexpected
         * @throws IOException if an I/O exception occurs
         *
         */
        protected String sendRequest(URL url) throws MarketingCloudException, IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url.openConnection();

                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new MarketingCloudException(String.format("Unexpected response: %d - %s", code, connection.getResponseMessage()));
                }

                String contentType = connection.getHeaderField("Content-Type");
                if (!contentType.equals(RESPONSE_CHARSET)) {
                    throw new MarketingCloudException(String.format("Unexpected content type: %s", contentType));
                }

                return readResponse(connection.getInputStream());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

        }

        /**
         * Creates the request URL given the encoded query parameters. The Marketing Cloud ID Service does not
         * require all parameters to be encoded.
         *
         * @param encodedQueryParameters Map with query parameters already encoded.
         * @return Constructed URL
         *
         * @throws IllegalArgumentException when the parameters of the query are invalid.
         */
        protected URL createUrl(Map<String, String> encodedQueryParameters) {

            Map<String, String> queryParameters = new HashMap<>();
            queryParameters.put(VERSION_FIELD, Integer.toString(VERSION));
            queryParameters.put(REGION_FIELD, Integer.toString(region));
            queryParameters.put(FORMAT_FIELD, FORMAT);
            queryParameters.put(ORGANIZATION_FIELD, organizationId + ORGANIZATION_SUFFIX);
            queryParameters.putAll(encodedQueryParameters);

            // Manual query building
            StringBuilder query = new StringBuilder();
            for (String field : queryParameters.keySet()) {
                query.append(field).append('=').append(queryParameters.get(field)).append('&');
            }

            // Remove the last '&'
            if (queryParameters.size() > 0) {
                query.setLength(query.length() - 1);
            }

            Uri.Builder uri = new Uri.Builder().scheme(SCHEME).authority(HOST).path(PATH).encodedQuery(query.toString());

            try {
                URL url = new URL(uri.build().toString());
                return url;
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }

        }

        protected String readResponse(InputStream input) throws MarketingCloudException, IOException {
            JsonReader reader = null;
            try {
                reader = new JsonReader(new InputStreamReader(input, CHARSET));

                // { ..., "d_mid": "visitorId", ... }
                // or { ..., "errors": [{ "code": 2, "msg": "error" } ... ], ... }
                reader.beginObject();
                while (reader.hasNext()) {
                    String fieldName = reader.nextName();
                    if (fieldName.equals(ERRORS_FIELD)) {
                        reader.beginArray();
                        reader.beginObject();

                        int code = -1;
                        String errorMessage = "";

                        // We only parse the first error.
                        while (reader.hasNext()) {
                            String errorFieldName = reader.nextName();
                            if (errorFieldName.equals(CODE_FIELD)) {
                                code = reader.nextInt();
                            } else if (errorFieldName.equals(ERROR_MSG_FIELD)) {
                                errorMessage = reader.nextString();
                            } else {
                                reader.skipValue();
                            }
                        }

                        throw new MarketingCloudException(String.format("Received error (%d): %s", code, errorMessage), true);

                    } else if (fieldName.equals(VID_FIELD)) {
                        return reader.nextString();
                    }
                    reader.skipValue();
                }
            } catch (EOFException | MalformedJsonException e) {
                throw new MarketingCloudException(String.format("Invalid JSON response: %s", e.getMessage()), e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

            throw new MarketingCloudException("Visitor ID not found in the response body");
        }

    }

}
