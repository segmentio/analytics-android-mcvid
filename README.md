analytics-android-mcvid
=======================================

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.segment.analytics.android.middlewares/mcvid/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.segment.analytics.android.middlewares/mcvid)
[![Javadocs](http://javadoc-badge.appspot.com/com.segment.analytics.android.middlewares/mcvid.svg?label=javadoc)](http://javadoc-badge.appspot.com/com.segment.analytics.android.middlewares/mcvid)

A middleware to inject Adobe Marketing Cloud Visitor IDs to your events.

## Installation

To install the middleware, simply add this line to your gradle file:

```
compile 'com.segment.analytics.android.middlewares.mcvid:+'
```

## Permissions

The middleware performs HTTP requests in an executor, so it requires internet access:
```
<uses-permission android:name="android.permission.INTERNET"/>
```
Syncing the Visitor ID with the device's advertising ID requires access to Google Play Services.

## Testing

You will need to provide some parameters to make the tests work end to end:
```
export TEST_ADOBE_ORGANIZATION_ID="myTestOrganizationId";
export TEST_ADOBE_REGION="3";
export TEST_ADOBE_CUSTOMER_ID="DSID_20914%01myTestCustomerId"
```

Then use gradle to run the tests:
```
$ ./gradlew test
```

If you are using Android Studio, you will need to pass that configuration as part of the JVM arguments. In the Run... dialog, add
as VM options
```
-Dtest.adobe.organization_id=myTestOrganizationId -Dtest.adobe.region=3 -Dtest.adobe.customer_id=DSID_20914%01myTestCustomerId
```

## Usage

After adding the dependency, you must register the middleware with our SDK.  To do this, import the package:


```
import com.segment.analytics.android.middlewares.mcvid.MCVIDMiddleware;

```

And add the following line:

```
analytics = new Analytics.Builder(this, "write_key")
                ...
                .middleware(new MarketingCloudMiddleware(this, "my organization ID", region))
                .build();
```

You can also customize the implementation for the Visitor ID client or store using the builder.
```
MarketingCloudMiddleware mcvid = new MarketingCloudMiddleware.Builder().withClient(client).withStore(store).withActivity(this).build();
analytics = new Analytics.Builder(this, "write_key")
                ...
                .middleware(mcvid)
                .build();
```


Please see [our documentation](https://segment.com/docs/sources/mobile/android/) for more information.

## Other documentation

* [Marketing Cloud Service](https://marketing.adobe.com/resources/help/en_US/mcvid/)
* [Direct integration with Cloud ID Service](https://marketing.adobe.com/resources/help/en_US/mcvid/mcvid-direct-integration.html)

## License

```
WWWWWW||WWWWWW
 W W W||W W W
      ||
    ( OO )__________
     /  |           \
    /o o|    MIT     \
    \___/||_||__||_|| *
         || ||  || ||
        _||_|| _||_||
       (__|__|(__|__|

The MIT License (MIT)

Copyright (c) 2019 Segment, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
