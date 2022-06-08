package io.spring.infrastructure;

import io.micronaut.context.annotation.Context;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Applies the Flyway migrations in {@code db/migration} before the application serves requests. */
@Context
public class DatabaseMigration {

  public DatabaseMigration(DataSource dataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
  }
}
