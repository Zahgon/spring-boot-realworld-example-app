package io.spring.graphql;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** A component contributing data fetchers to the GraphQL runtime wiring. */
public interface GraphQLWiring {
  ObjectMapper INPUT_MAPPER =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  void wire(RuntimeWiring.Builder wiring);

  /**
   * The input object argument {@code name} converted to {@code type}, or null if absent. Null
   * elements of list fields are dropped.
   */
  static <T> T inputArgument(DataFetchingEnvironment environment, String name, Class<T> type) {
    Map<String, Object> value = environment.getArgument(name);
    return value == null ? null : INPUT_MAPPER.convertValue(withoutNullElements(value), type);
  }

  private static Object withoutNullElements(Object value) {
    if (value instanceof Map) {
      Map<Object, Object> copy = new LinkedHashMap<>();
      ((Map<?, ?>) value).forEach((k, v) -> copy.put(k, withoutNullElements(v)));
      return copy;
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      return list.stream()
          .filter(Objects::nonNull)
          .map(GraphQLWiring::withoutNullElements)
          .collect(Collectors.toList());
    }
    return value;
  }
}
