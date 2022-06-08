package io.spring.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.spring.api.security.CurrentUser;
import io.spring.api.web.ContentNegotiation;
import io.spring.api.web.ContentNegotiation.NotAcceptableException;
import io.spring.api.web.FormParameters;
import io.spring.api.web.MediaRange;
import io.spring.api.web.RequestBodyReader;
import io.spring.core.user.User;
import io.spring.graphql.exception.ErrorTypeGraphQLError;
import io.spring.graphql.exception.ErrorTypeGraphQLError.Kind;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code /graphql} endpoint. Any HTTP method is accepted; the request body is either a JSON
 * object with {@code query}, {@code variables}, {@code operationName} and {@code extensions}, or
 * (with an {@code application/graphql} content type) the bare query text.
 */
@Controller("/graphql")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
public class GraphQLApi {
  /** The media types {@code /graphql} answers in. */
  public static final List<MediaRange> PRODUCES = List.of(MediaRange.APPLICATION_JSON);

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final GraphQL graphQL;
  private static final ObjectMapper mapper =
      JsonMapper.builder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();

  public GraphQLApi(GraphQL graphQL) {
    this.graphQL = graphQL;
  }

  @Get
  public HttpResponse<byte[]> get(HttpRequest<?> request, @Nullable @CurrentUser User user) {
    return graphql(request, user);
  }

  @Post
  public HttpResponse<byte[]> post(HttpRequest<?> request, @Nullable @CurrentUser User user) {
    return graphql(request, user);
  }

  @Put
  public HttpResponse<byte[]> put(HttpRequest<?> request, @Nullable @CurrentUser User user) {
    return graphql(request, user);
  }

  @Patch
  public HttpResponse<byte[]> patch(HttpRequest<?> request, @Nullable @CurrentUser User user) {
    return graphql(request, user);
  }

  @Delete
  public HttpResponse<byte[]> delete(HttpRequest<?> request, @Nullable @CurrentUser User user) {
    return graphql(request, user);
  }

  private HttpResponse<byte[]> graphql(HttpRequest<?> request, User user) {
    if (!ContentNegotiation.accepts(request, PRODUCES)) {
      throw new NotAcceptableException();
    }
    String body = readBody(request, FormParameters.rawBody(request, bodyBytes(request)));

    Map<String, Object> inputQuery;
    Map<String, Object> queryVariables;
    Map<String, Object> extensions;
    if (body == null) {
      throw new GraphQLRequestException("Invalid GraphQL request - no request body was provided");
    }
    String contentType = request.getHeaders().get(HttpHeaders.CONTENT_TYPE);
    if (contentType != null && contentType.contains("application/graphql")) {
      inputQuery = Map.of("query", body);
      queryVariables = Collections.emptyMap();
      extensions = Collections.emptyMap();
    } else {
      inputQuery = readQuery(body);
      queryVariables = mapValue(inputQuery.get("variables"));
      extensions = mapValue(inputQuery.get("extensions"));
    }

    Object operationName = inputQuery.get("operationName");
    if (operationName != null && !(operationName instanceof String)) {
      throw new GraphQLRequestException("Invalid GraphQL request - operationName must be a String");
    }
    Object queryValue = inputQuery.get("query");
    String query = queryValue instanceof String ? (String) queryValue : null;

    ExecutionResult executionResult =
        execute(query, queryVariables, extensions, (String) operationName, request, user);
    return text(request, HttpStatus.OK, toJson(executionResult));
  }

  /** The JSON form of {@code result}: its specification map of plain JSON values. */
  public static String toJson(ExecutionResult result) {
    try {
      return mapper.writeValueAsString(result.toSpecification());
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The JSON request body as a map; a body that does not parse is a {@link
   * GraphQLRequestException}.
   */
  private static Map<String, Object> readQuery(String body) {
    Map<String, Object> inputQuery;
    try {
      inputQuery = mapper.readValue(body, MAP_TYPE);
    } catch (MismatchedInputException e) {
      throw new GraphQLRequestException("Invalid query - No content to map to input.");
    } catch (JsonProcessingException e) {
      throw new GraphQLRequestException("Invalid query - " + e.getMessage() + ".");
    }
    if (inputQuery == null) {
      throw new IllegalStateException("The request body is the JSON literal null");
    }
    return inputQuery;
  }

  private ExecutionResult execute(
      String query,
      Map<String, Object> variables,
      Map<String, Object> extensions,
      String operationName,
      HttpRequest<?> request,
      User user) {
    if (query == null || query.isBlank()) {
      return ExecutionResultImpl.newExecutionResult()
          .addError(
              new ErrorTypeGraphQLError(Kind.BAD_REQUEST, "The query is null or empty.", null))
          .build();
    }
    GraphQLRequestContext context =
        new GraphQLRequestContext(user, name -> request.getHeaders().getAll(name));
    ExecutionInput input =
        ExecutionInput.newExecutionInput()
            .query(query)
            .operationName(operationName)
            .variables(variables)
            .extensions(extensions)
            .graphQLContext(builder -> builder.of(GraphQLRequestContext.class, context))
            .build();
    CompletableFuture<ExecutionResult> result;
    try {
      result = graphQL.executeAsync(input);
    } catch (RuntimeException e) {
      throw new GraphQLExecutionException(query, e);
    }
    return result.join();
  }

  /** The full request body, whatever the request method. */
  private static byte[] bodyBytes(HttpRequest<?> request) {
    if (!(request instanceof ServerHttpRequest)) {
      return new byte[0];
    }
    try (CloseableAvailableByteBody body =
        ((ServerHttpRequest<?>) request).byteBody().buffer().join()) {
      return body.toByteArray();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapValue(Object value) {
    return value == null ? Collections.emptyMap() : (Map<String, Object>) value;
  }

  /**
   * The request body decoded with the charset of its content type (UTF-8 by default), or null when
   * there is none.
   */
  private static String readBody(HttpRequest<?> request, byte[] body) {
    MediaRange contentType = RequestBodyReader.contentType(request);
    if (body == null || body.length == 0) {
      return null;
    }
    Charset charset = contentType == null ? null : contentType.charset();
    return new String(body, charset == null ? StandardCharsets.UTF_8 : charset);
  }

  /** {@code text} as the response body, in the charset of the negotiated JSON media type. */
  private static MutableHttpResponse<byte[]> text(
      HttpRequest<?> request, HttpStatus status, String text) {
    return text(
        ContentNegotiation.select(request, PRODUCES).orElseThrow(NotAcceptableException::new),
        status,
        text);
  }

  /** {@code text} as a response body of {@code contentType}, in its charset (UTF-8 by default). */
  public static MutableHttpResponse<byte[]> text(
      MediaRange contentType, HttpStatus status, String text) {
    Charset charset = contentType.charset();
    return HttpResponse.<byte[]>status(status)
        .header(HttpHeaders.CONTENT_TYPE, contentType.toString())
        .body(text.getBytes(charset == null ? StandardCharsets.UTF_8 : charset));
  }
}
