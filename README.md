# ![RealWorld Example App using Micronaut](example-logo.png)

[![Actions](https://github.com/gothinkster/spring-boot-realworld-example-app/workflows/Java%20CI/badge.svg)](https://github.com/gothinkster/spring-boot-realworld-example-app/actions)

> ### Micronaut + MyBatis codebase containing real world examples (CRUD, auth, advanced patterns, etc) that adheres to the [RealWorld](https://github.com/gothinkster/realworld-example-apps) spec and API.

This codebase was created to demonstrate a fully fledged full-stack application built with Micronaut + MyBatis including CRUD operations, authentication, routing, pagination, and more.

For more information on how to this works with other frontends/backends, head over to the [RealWorld](https://github.com/gothinkster/realworld) repo.

# *NEW* GraphQL Support  

Following some DDD principles. REST or GraphQL is just a kind of adapter. And the domain layer will be consistent all the time. So this repository implement GraphQL and REST at the same time.

The GraphQL schema is https://github.com/gothinkster/spring-boot-realworld-example-app/blob/master/src/main/resources/schema/schema.graphqls and the visualization looks like below.

![](graphql-schema.png)

The GraphQL endpoint `/graphql` is served by [graphql-java](https://www.graphql-java.com/) on Micronaut, wired from the same schema file.
# How it works

The application uses [Micronaut 4](https://micronaut.io/) (the Netty HTTP server, dependency injection and configuration), plain MyBatis, Flyway and graphql-java.

* Use the idea of Domain Driven Design to separate the business term and infrastructure term.
* Use MyBatis to implement the [Data Mapper](https://martinfowler.com/eaaCatalog/dataMapper.html) pattern for persistence.
* Use [CQRS](https://martinfowler.com/bliki/CQRS.html) pattern to separate the read model and write model.

And the code is organized as this:

1. `api` is the web layer implemented with Micronaut controllers and filters
2. `core` is the business model including entities and services
3. `application` is the high-level services for querying the data transfer objects
4. `infrastructure`  contains all the implementation classes as the technique details (MyBatis sessions and transactions, the Flyway migration run at startup)
5. `graphql` is the GraphQL layer: the `/graphql` endpoint and its data fetchers

# Security

A Micronaut server filter reads the `Authorization: Token <jwt>` header, authenticates the user and applies the CORS and security headers.

The secret key is stored in `application.yml`.

# Database

It uses a ~~H2 in-memory database~~ sqlite database (for easy local test without losing test data after every restart), can be changed easily in the `application.yml` (`datasources.default`) for any other database. The Flyway migrations in `src/main/resources/db/migration` run when the service starts.

# Getting started

You'll need Java 17 installed.

    ./gradlew run

or build the distribution and start it (the port is `SERVER_PORT`, 8080 by default):

    ./gradlew installDist
    build/install/realworld/bin/realworld

To test that it works, open a browser tab at http://localhost:8080/tags .  
Alternatively, you can run

    curl http://localhost:8080/tags

# Try it out with [Docker](https://www.docker.com/)

You'll need Docker installed.
	
    ./gradlew installDist
    docker run -p 8081:8080 -v "$PWD/build/install/realworld:/app" -w /tmp eclipse-temurin:17-jre /app/bin/realworld

# Try it out with a RealWorld frontend

The entry point address of the backend API is at http://localhost:8080, **not** http://localhost:8080/api as some of the frontend documentation suggests.

# Run test

The repository contains a lot of test cases to cover both api test and repository test.

    ./gradlew test

# Code format

Use spotless for code format.

    ./gradlew spotlessApply

# Help

Please fork and PR to improve the project.
