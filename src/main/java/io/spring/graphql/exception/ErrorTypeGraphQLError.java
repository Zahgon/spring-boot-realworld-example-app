package io.spring.graphql.exception;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A GraphQL error as the API writes it: {@code message}, an empty {@code locations} list, the
 * {@code path} when there is one, and its kind as the {@code errorType} extension (next to any
 * other extensions) instead of graphql-java's {@code classification} entry.
 */
public class ErrorTypeGraphQLError implements GraphQLError {
  /** The values of the {@code errorType} extension. */
  public enum Kind {
    INTERNAL,
    UNAUTHENTICATED,
    BAD_REQUEST
  }

  private final String message;
  private final List<Object> path;
  private final Map<String, Object> extensions;

  public ErrorTypeGraphQLError(
      Kind kind, String message, ResultPath path, Map<String, Object> extensions) {
    this.message = message == null ? kind.name() : message;
    this.path = path == null ? null : path.toList();
    this.extensions = new HashMap<>(extensions);
    this.extensions.put("errorType", kind.name());
  }

  public ErrorTypeGraphQLError(Kind kind, String message, ResultPath path) {
    this(kind, message, path, Map.of());
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public List<SourceLocation> getLocations() {
    return List.of();
  }

  @Override
  public ErrorClassification getErrorType() {
    return null;
  }

  @Override
  public List<Object> getPath() {
    return path;
  }

  @Override
  public Map<String, Object> getExtensions() {
    return extensions;
  }
}
