package io.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import io.spring.core.user.User;
import java.util.List;
import java.util.Optional;

/** Per-request state made available to data fetchers through the GraphQL context. */
public class GraphQLRequestContext {
  private final User currentUser;
  private final RequestHeaders headers;

  public GraphQLRequestContext(User currentUser, RequestHeaders headers) {
    this.currentUser = currentUser;
    this.headers = headers;
  }

  public static GraphQLRequestContext of(DataFetchingEnvironment environment) {
    return environment.getGraphQlContext().get(GraphQLRequestContext.class);
  }

  public Optional<User> getCurrentUser() {
    return Optional.ofNullable(currentUser);
  }

  /**
   * All values of the request header {@code name} joined with {@code ", "}.
   *
   * @throws io.spring.graphql.exception.MissingHeaderException if the request has no such header
   */
  public String requiredHeader(String name) {
    List<String> values = headers.getAll(name);
    if (values.isEmpty()) {
      throw new io.spring.graphql.exception.MissingHeaderException(name);
    }
    return String.join(", ", values);
  }

  /** Case-insensitive access to the request headers. */
  public interface RequestHeaders {
    List<String> getAll(String name);
  }
}
