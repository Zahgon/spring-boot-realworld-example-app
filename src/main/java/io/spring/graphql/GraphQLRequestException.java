package io.spring.graphql;

/**
 * A {@code /graphql} request that cannot be executed (no body, a body that is not a JSON object, a
 * non-string {@code operationName}); answered with 400 and the message as the body.
 */
@SuppressWarnings("serial")
public class GraphQLRequestException extends RuntimeException {
  public GraphQLRequestException(String message) {
    super(message);
  }
}
