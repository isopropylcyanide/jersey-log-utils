
## Jersey Log Utils
![Maven Central](https://img.shields.io/maven-central/v/com.github.isopropylcyanide/jersey-log-utils)
![Travis (.org)](https://img.shields.io/travis/isopropylcyanide/jersey-log-utils)
![Codecov](https://img.shields.io/codecov/c/github/isopropylcyanide/jersey-log-utils)
![GitHub](https://img.shields.io/github/license/isopropylcyanide/jersey-log-utils?color=blue)

A set of useful utilities for the Jersey Server and Client log filters. Have a look at the corresponding [blogpost](https://medium.com/nerd-for-tech/production-logging-feature-enhancements-atop-jersey-and-jax-rs-991127a36c88) to know more

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
| `WhitelistedServerLoggingFilter` |  Exclude server requests and responses log for certain URI, say healthchecks  |
| `WhitelistedClientLoggingFilter` |  Exclude client requests and responses log for certain URI, say heavy GET calls  |
| `DelayedServerRequestResponseLoggingFilter` |  Delay server requests and response log for specific response codes |


## Usage

- Register the filter to your Jersey Environment

```java
     excludeContexts = Sets.newHashSet(
                new ExcludeContext("v1/get"), //all Http Verbs
                new ExcludeContext(HttpMethod.PUT, "v1/update") //PUT only
        );
    environment.jersey().register(
      new WhiteListedServerLoggingFeature(excludeContexts, maxEntitySize)
    );
```

```java
    environment.jersey().register(
      new DelayedServerRequestResponseLoggingFilter(logger, responseCondition: ResponseCondition.ON_RESPONSE_4XX_5XX)
    );
```

```java
    excludeContexts = Sets.newHashSet(
            new ExcludeContext(HttpMethod.GET, "v1/get"),
            new ExcludeContext("v1/update") //all Verbs
    );
    new JerseyClientBuilder(environment).register(
            new WhitelistedClientLoggingFilter(excludeContexts, logger, maxEntitySize)
    );
```

## Why
The standard [`LoggingFilter`](http://javadox.com/org.glassfish.jersey.bundles/apidocs/2.11/org/glassfish/jersey/filter/LoggingFilter.html) which has been deprecated as of Jersey `2.26` is a universal logging filter and does a great job of logging requests and responses at the server. 

However, it does so blindly, as it encounters each phase during the request lifecycle, and does it for every request. There may be production use cases that would require modification of this *standard* `Logging Filter`

- We would not want to log all requests and responses, only the ones that match a particular criteria. Example of this would be requests that are from an ELB or health check system or static assets. Request entry need to be logged but not the payloads

- For servers operating at high QPS, all successful requests and responses are logged which quickly leads to log rotation. The other alternative is to not register the filter at all which is not what we want. We only want to log where the responses are `4xx` or `409` or `400` and `500` or simply `200` requests. Such feature is not supported in the default filter as by the time response is received, the request is already logged.

- For client network calls, we might not want to log everything. Only what is required.

## Support

Please file bug reports and feature requests in [GitHub issues](https://github.com/isopropylcyanide/jersey-log-utils/issues).


## License

Copyright (c) 2012-2020 Aman Garg

This library is licensed under the Apache License, Version 2.0.

See http://www.apache.org/licenses/LICENSE-2.0.html or the LICENSE file in this repository for the full license text.
