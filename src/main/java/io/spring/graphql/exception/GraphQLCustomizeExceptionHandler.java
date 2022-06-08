package io.spring.graphql.exception;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.spring.api.exception.FieldErrorResource;
import io.spring.api.exception.InvalidAuthenticationException;
import io.spring.graphql.exception.ErrorTypeGraphQLError.Kind;
import io.spring.graphql.types.Error;
import io.spring.graphql.types.ErrorItem;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GraphQLCustomizeExceptionHandler implements DataFetcherExceptionHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(GraphQLCustomizeExceptionHandler.class);

  @Override
  public DataFetcherExceptionHandlerResult onException(
      DataFetcherExceptionHandlerParameters handlerParameters) {
    if (handlerParameters.getException() instanceof InvalidAuthenticationException) {
      GraphQLError graphqlError =
          new ErrorTypeGraphQLError(
              Kind.UNAUTHENTICATED,
              handlerParameters.getException().getMessage(),
              handlerParameters.getPath());
      return DataFetcherExceptionHandlerResult.newResult().error(graphqlError).build();
    } else if (handlerParameters.getException() instanceof ConstraintViolationException) {
      List<FieldErrorResource> errors = new ArrayList<>();
      for (ConstraintViolation<?> violation :
          ((ConstraintViolationException) handlerParameters.getException())
              .getConstraintViolations()) {
        FieldErrorResource fieldErrorResource =
            new FieldErrorResource(
                violation.getRootBeanClass().getName(),
                getParam(violation.getPropertyPath().toString()),
                violation
                    .getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType()
                    .getSimpleName(),
                violation.getMessage());
        errors.add(fieldErrorResource);
      }
      GraphQLError graphqlError =
          new ErrorTypeGraphQLError(
              Kind.BAD_REQUEST,
              handlerParameters.getException().getMessage(),
              handlerParameters.getPath(),
              errorsToMap(errors));
      return DataFetcherExceptionHandlerResult.newResult().error(graphqlError).build();
    } else {
      return defaultError(handlerParameters);
    }
  }

  private DataFetcherExceptionHandlerResult defaultError(
      DataFetcherExceptionHandlerParameters handlerParameters) {
    Throwable exception = handlerParameters.getException();
    logger.error(
        "Exception while executing data fetcher for {}: {}",
        handlerParameters.getPath(),
        exception.getMessage(),
        exception);
    GraphQLError graphqlError =
        new ErrorTypeGraphQLError(
            Kind.INTERNAL,
            exception.getClass().getName() + ": " + exception.getMessage(),
            handlerParameters.getPath());
    return DataFetcherExceptionHandlerResult.newResult().error(graphqlError).build();
  }

  public static Error getErrorsAsData(ConstraintViolationException cve) {
    List<FieldErrorResource> errors = new ArrayList<>();
    for (ConstraintViolation<?> violation : cve.getConstraintViolations()) {
      FieldErrorResource fieldErrorResource =
          new FieldErrorResource(
              violation.getRootBeanClass().getName(),
              getParam(violation.getPropertyPath().toString()),
              violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
              violation.getMessage());
      errors.add(fieldErrorResource);
    }
    Map<String, List<String>> errorMap = new HashMap<>();
    for (FieldErrorResource fieldErrorResource : errors) {
      if (!errorMap.containsKey(fieldErrorResource.getField())) {
        errorMap.put(fieldErrorResource.getField(), new ArrayList<>());
      }
      errorMap.get(fieldErrorResource.getField()).add(fieldErrorResource.getMessage());
    }
    List<ErrorItem> errorItems =
        errorMap.entrySet().stream()
            .map(kv -> ErrorItem.newBuilder().key(kv.getKey()).value(kv.getValue()).build())
            .collect(Collectors.toList());
    return Error.newBuilder().message("BAD_REQUEST").errors(errorItems).build();
  }

  private static String getParam(String s) {
    String[] splits = s.split("\\.");
    if (splits.length == 1) {
      return s;
    } else {
      return String.join(".", Arrays.copyOfRange(splits, 2, splits.length));
    }
  }

  private static Map<String, Object> errorsToMap(List<FieldErrorResource> errors) {
    Map<String, Object> json = new HashMap<>();
    for (FieldErrorResource fieldErrorResource : errors) {
      if (!json.containsKey(fieldErrorResource.getField())) {
        json.put(fieldErrorResource.getField(), new ArrayList<>());
      }
      ((List) json.get(fieldErrorResource.getField())).add(fieldErrorResource.getMessage());
    }
    return json;
  }
}
