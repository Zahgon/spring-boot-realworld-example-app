package io.spring.api.exception;

import graphql.ExecutionResultImpl;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.spring.api.web.ContentNegotiation;
import io.spring.api.web.ErrorResponses;
import io.spring.api.web.MediaRange;
import io.spring.api.web.QueryParameters;
import io.spring.api.web.RequestBodyException;
import io.spring.graphql.GraphQLApi;
import io.spring.graphql.GraphQLExecutionException;
import io.spring.graphql.GraphQLRequestException;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps exceptions raised while handling a request to the API's error responses. */
@Produces
@Singleton
public class CustomizeExceptionHandler implements ExceptionHandler<Exception, HttpResponse<?>> {
  private static final Logger logger = LoggerFactory.getLogger(CustomizeExceptionHandler.class);
  private static final HttpStatus UNPROCESSABLE_ENTITY = HttpStatus.UNPROCESSABLE_ENTITY;

  @Override
  public HttpResponse<?> handle(HttpRequest request, Exception exception) {
    if (exception instanceof InvalidRequestException) {
      return handleInvalidRequest((InvalidRequestException) exception);
    } else if (exception instanceof InvalidAuthenticationException) {
      HashMap<String, Object> body = new HashMap<>();
      body.put("message", exception.getMessage());
      return ErrorResponses.withFallback(
          HttpResponse.status(UNPROCESSABLE_ENTITY).body(body), HttpStatus.INTERNAL_SERVER_ERROR);
    } else if (exception instanceof RequestBodyValidationException) {
      return ErrorResponses.withFallback(
          HttpResponse.status(UNPROCESSABLE_ENTITY)
              .body(
                  new ErrorResource(((RequestBodyValidationException) exception).getFieldErrors())),
          HttpStatus.BAD_REQUEST);
    } else if (exception instanceof ConstraintViolationException) {
      return ErrorResponses.withFallback(
          HttpResponse.status(UNPROCESSABLE_ENTITY)
              .body(handleConstraintViolation((ConstraintViolationException) exception)),
          HttpStatus.INTERNAL_SERVER_ERROR);
    } else if (exception instanceof ResourceNotFoundException) {
      return ErrorResponses.error(request, HttpStatus.NOT_FOUND);
    } else if (exception instanceof NoAuthorizationException) {
      return ErrorResponses.error(request, HttpStatus.FORBIDDEN);
    } else if (exception instanceof RequestBodyException.UnsupportedMediaType) {
      MutableHttpResponse<?> response = HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
      List<MediaRange> supported =
          ((RequestBodyException.UnsupportedMediaType) exception).getSupported();
      if (!supported.isEmpty()) {
        response.header(
            HttpHeaders.ACCEPT,
            supported.stream().map(MediaRange::toString).collect(Collectors.joining(", ")));
      }
      return response;
    } else if (exception instanceof RequestBodyException
        || exception instanceof QueryParameters.InvalidParameterException) {
      return HttpResponse.status(HttpStatus.BAD_REQUEST);
    } else if (exception instanceof ContentNegotiation.NotAcceptableException) {
      return HttpResponse.status(HttpStatus.NOT_ACCEPTABLE);
    } else if (exception instanceof GraphQLRequestException) {
      return graphQLAnswer(request, HttpStatus.BAD_REQUEST, exception.getMessage());
    } else if (exception instanceof GraphQLExecutionException) {
      GraphQLExecutionException e = (GraphQLExecutionException) exception;
      logger.error("Encountered an exception while handling query {}", e.getQuery(), e.getCause());
      return graphQLAnswer(
          request, HttpStatus.OK, GraphQLApi.toJson(new ExecutionResultImpl(null, e.getErrors())));
    }
    logger.error(
        "Request processing failed: {} {}", request.getMethodName(), request.getPath(), exception);
    return ErrorResponses.error(request, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /** A {@code /graphql} answer with {@code text} as the body, in the negotiated JSON type. */
  private HttpResponse<?> graphQLAnswer(HttpRequest<?> request, HttpStatus status, String text) {
    Optional<MediaRange> contentType = ContentNegotiation.select(request, GraphQLApi.PRODUCES);
    if (contentType.isEmpty()) {
      return HttpResponse.status(HttpStatus.NOT_ACCEPTABLE);
    }
    try {
      return GraphQLApi.text(contentType.get(), status, text);
    } catch (IllegalArgumentException unsupportedCharset) {
      // The negotiated charset is unknown to the JVM: answered like any other failure while
      // handling the request (logged, 500).
      return handle(request, unsupportedCharset);
    }
  }

  private HttpResponse<?> handleInvalidRequest(InvalidRequestException e) {
    List<FieldErrorResource> errorResources = new ArrayList<>(e.getErrors());
    return ErrorResponses.presetJson(
        HttpResponse.status(UNPROCESSABLE_ENTITY).body(new ErrorResource(errorResources)));
  }

  private ErrorResource handleConstraintViolation(ConstraintViolationException ex) {
    List<FieldErrorResource> errors = new ArrayList<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      FieldErrorResource fieldErrorResource =
          new FieldErrorResource(
              violation.getRootBeanClass().getName(),
              getParam(violation.getPropertyPath().toString()),
              violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
              violation.getMessage());
      errors.add(fieldErrorResource);
    }

    return new ErrorResource(errors);
  }

  private String getParam(String s) {
    String[] splits = s.split("\\.");
    if (splits.length == 1) {
      return s;
    } else {
      return String.join(".", Arrays.copyOfRange(splits, 2, splits.length));
    }
  }
}
