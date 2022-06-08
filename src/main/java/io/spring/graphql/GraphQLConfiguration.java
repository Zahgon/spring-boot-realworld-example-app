package io.spring.graphql;

import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Factory;
import io.spring.graphql.exception.GraphQLCustomizeExceptionHandler;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Builds the executable GraphQL schema from {@code schema/schema.graphqls} and the wirings. */
@Factory
public class GraphQLConfiguration {
  private static final String SCHEMA = "schema/schema.graphqls";

  @Singleton
  public GraphQL graphQL(
      List<GraphQLWiring> wirings, GraphQLCustomizeExceptionHandler exceptionHandler) {
    TypeDefinitionRegistry registry = new SchemaParser().parse(readSchema());
    RuntimeWiring.Builder wiring = RuntimeWiring.newRuntimeWiring();
    wirings.forEach(w -> w.wire(wiring));
    wiring.type(
        "UserResult",
        type ->
            type.typeResolver(
                env -> {
                  Object result = env.getObject();
                  GraphQLObjectType resolved =
                      env.getSchema().getObjectType(result.getClass().getSimpleName());
                  if (resolved == null) {
                    throw new IllegalStateException(
                        "No GraphQL type for " + result.getClass().getName());
                  }
                  return resolved;
                }));
    GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring.build());
    return GraphQL.newGraphQL(schema)
        .queryExecutionStrategy(new AsyncExecutionStrategy(exceptionHandler))
        .mutationExecutionStrategy(new AsyncSerialExecutionStrategy(exceptionHandler))
        .build();
  }

  private static String readSchema() {
    try (InputStream in = GraphQLConfiguration.class.getClassLoader().getResourceAsStream(SCHEMA)) {
      if (in == null) {
        throw new IllegalStateException("GraphQL schema not found: " + SCHEMA);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
