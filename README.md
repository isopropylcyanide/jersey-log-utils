
## Jersey Log Utils
![Travis (.org)](https://img.shields.io/travis/isopropylcyanide/async-metrics-codahale)
![Codecov](https://img.shields.io/codecov/c/github/isopropylcyanide/async-metrics-codahale)
![GitHub](https://img.shields.io/github/license/isopropylcyanide/async-metrics-codahale?color=blue)

A set of useful utilities for the Jersey Server and Client log filters. These are 

## Maven Artifacts

This project is available on Maven Central. To add it to your project you can add the following dependency to your
`pom.xml`:

```xml
    <dependency>
        <groupId>com.github.isopropylcyanide</groupId>
        <artifactId>jersey-log-utils/artifactId>
        <version>1.0</version>
     </dependency>
```

## Features

| Filter | Use Case |
| ------------- | ------------- |
| `WhitelistedServerLoggingFilter` |  Exclude requests and responses log for certain URI, say healthchecks  |
| `DelayedRequestResponseLoggingFilter` |  Delay requests and response log for specific response codes |


## Usage

- Register the filter to your Jersey Environment

```java
    environment.jersey().register(
      new WhiteListedServerLoggingFeature(excludedPaths, maxEntitySize)
    );
```

```java
    environment.jersey().register(
      new DelayedRequestResponseLoggingFilter(logger, responseCondition: ResponseCondition.ON_RESPONSE_4XX_5XX)
    );
```
## Why
The standard [`LoggingFilter`](http://javadox.com/org.glassfish.jersey.bundles/apidocs/2.11/org/glassfish/jersey/filter/LoggingFilter.html) which has been deprecated as of Jersey `2.26` is a universal logging filter and does a great job of logging requests and responses at the server. 

However, it does so blindly, as it encounters each phase during the request lifecycle, and does it for every request. There may be production use cases that would require modification of this *standard* `Logging Filter`

- We would not want to log all requests and responses, only the ones that match a particular criteria. Example of this would be requests that are from an ELB or health check system or static assets. Request entry need to be logged but not the payloads

- For servers operating at high QPS, all successful requests and responses are logged which quickly leads to log rotation. The other alternative is to not register the filter at all which is not what we want. We only want to log where the responses are `4xx` or `409` or `400` and `500` or simply `200` requests. Such feature is not supported in the default filter as by the time response is received, the request is already logged.
 .


## Support

Please file bug reports and feature requests in [GitHub issues](https://github.com/isopropylcyanide/jersey-log-utils/issues).


## License

Copyright (c) 2012-2020 Aman Garg

This library is licensed under the Apache License, Version 2.0.

See http://www.apache.org/licenses/LICENSE-2.0.html or the LICENSE file in this repository for the full license text.






