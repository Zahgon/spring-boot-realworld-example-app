package io.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import io.spring.core.user.User;
import java.util.Optional;

public class SecurityUtil {
  public static Optional<User> getCurrentUser(DataFetchingEnvironment environment) {
    return GraphQLRequestContext.of(environment).getCurrentUser();
  }
}
