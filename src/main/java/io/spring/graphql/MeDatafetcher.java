package io.spring.graphql;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.application.UserQueryService;
import io.spring.application.data.UserData;
import io.spring.application.data.UserWithToken;
import io.spring.core.service.JwtService;
import io.spring.graphql.types.User;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class MeDatafetcher implements GraphQLWiring {
  private UserQueryService userQueryService;
  private JwtService jwtService;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type(
        "Query",
        type ->
            type.dataFetcher(
                "me",
                env -> getMe(GraphQLRequestContext.of(env).requiredHeader("Authorization"), env)));
    wiring.type("UserPayload", type -> type.dataFetcher("user", env -> getUserPayloadUser(env)));
  }

  public DataFetcherResult<User> getMe(
      String authorization, DataFetchingEnvironment dataFetchingEnvironment) {
    io.spring.core.user.User user =
        SecurityUtil.getCurrentUser(dataFetchingEnvironment).orElse(null);
    if (user == null) {
      return null;
    }
    UserData userData =
        userQueryService.findById(user.getId()).orElseThrow(ResourceNotFoundException::new);
    UserWithToken userWithToken = new UserWithToken(userData, authorization.split(" ")[1]);
    User result =
        User.newBuilder()
            .email(userWithToken.getEmail())
            .username(userWithToken.getUsername())
            .token(userWithToken.getToken())
            .build();
    return DataFetcherResult.<User>newResult().data(result).localContext(user).build();
  }

  public DataFetcherResult<User> getUserPayloadUser(
      DataFetchingEnvironment dataFetchingEnvironment) {
    io.spring.core.user.User user = dataFetchingEnvironment.getLocalContext();
    User result =
        User.newBuilder()
            .email(user.getEmail())
            .username(user.getUsername())
            .token(jwtService.toToken(user))
            .build();
    return DataFetcherResult.<User>newResult().data(result).localContext(user).build();
  }
}
