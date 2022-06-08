package io.spring.infrastructure;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

/**
 * Repository and query tests run against a fresh in-memory database: every test method gets a new
 * application context, so the single pooled connection (and with it the database) is recreated.
 */
@MicronautTest(environments = "test", startApplication = false, rebuildContext = true)
public abstract class DbTestBase {}
