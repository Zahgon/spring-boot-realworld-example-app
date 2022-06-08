package io.spring.graphql;

import graphql.GraphQLError;
import java.util.List;

/**
 * graphql-java failed before producing a result, for example when no operation can be selected.
 * Answered with 200 and a result holding the failure when it is a GraphQL error, and no errors
 * otherwise.
 */
@SuppressWarnings("serial")
public class GraphQLExecutionException extends RuntimeException {
  private final String query;

  public GraphQLExecutionException(String query, RuntimeException cause) {
    super(cause);
    this.query = query;
  }

  public String getQuery() {
    return query;
  }

  public List<GraphQLError> getErrors() {
    return getCause() instanceof GraphQLError ? List.of((GraphQLError) getCause()) : List.of();
  }
}
