package io.spring.graphql.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import graphql.execution.ResultPath;
import io.spring.graphql.exception.ErrorTypeGraphQLError.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ErrorTypeGraphQLErrorTest {

  @Test
  public void should_write_error_type_as_extension_in_specification() {
    ErrorTypeGraphQLError error =
        new ErrorTypeGraphQLError(
            Kind.INTERNAL,
            "java.lang.IllegalStateException: boom",
            ResultPath.parse("/article/comments"));

    Map<String, Object> specification = error.toSpecification();

    assertEquals(
        List.of("message", "locations", "path", "extensions"), List.copyOf(specification.keySet()));
    assertEquals("java.lang.IllegalStateException: boom", specification.get("message"));
    assertEquals(List.of(), specification.get("locations"));
    assertEquals(List.of("article", "comments"), specification.get("path"));
    assertEquals(Map.of("errorType", "INTERNAL"), specification.get("extensions"));
    assertNull(error.getErrorType());
  }

  @Test
  public void should_default_message_to_error_type_and_omit_absent_path() {
    ErrorTypeGraphQLError error = new ErrorTypeGraphQLError(Kind.UNAUTHENTICATED, null, null);

    Map<String, Object> specification = error.toSpecification();

    assertEquals("UNAUTHENTICATED", error.getMessage());
    assertFalse(specification.containsKey("path"));
    assertEquals(Map.of("errorType", "UNAUTHENTICATED"), specification.get("extensions"));
  }

  @Test
  public void should_merge_extensions_with_error_type_taking_precedence() {
    ErrorTypeGraphQLError error =
        new ErrorTypeGraphQLError(
            Kind.BAD_REQUEST,
            null,
            ResultPath.parse("/articles[1]/title"),
            Map.of("title", List.of("can't be empty"), "errorType", "OVERRIDDEN"));

    assertEquals("BAD_REQUEST", error.getMessage());
    assertEquals(
        Map.of("title", List.of("can't be empty"), "errorType", "BAD_REQUEST"),
        error.getExtensions());
    assertEquals(List.of("articles", 1, "title"), error.getPath());
  }
}
