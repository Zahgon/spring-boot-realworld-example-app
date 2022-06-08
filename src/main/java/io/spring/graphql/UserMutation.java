package io.spring.graphql;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.api.exception.InvalidAuthenticationException;
import io.spring.application.user.RegisterParam;
import io.spring.application.user.UpdateUserCommand;
import io.spring.application.user.UpdateUserParam;
import io.spring.application.user.UserService;
import io.spring.core.service.PasswordHasher;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.graphql.exception.GraphQLCustomizeExceptionHandler;
import io.spring.graphql.types.CreateUserInput;
import io.spring.graphql.types.UpdateUserInput;
import io.spring.graphql.types.UserPayload;
import io.spring.graphql.types.UserResult;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class UserMutation implements GraphQLWiring {

  private UserRepository userRepository;
  private PasswordHasher passwordHasher;
  private UserService userService;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type(
        "Mutation",
        type ->
            type.dataFetcher(
                    "createUser",
                    env ->
                        createUser(
                            GraphQLWiring.inputArgument(env, "input", CreateUserInput.class), env))
                .dataFetcher(
                    "login",
                    env -> login(env.getArgument("password"), env.getArgument("email"), env))
                .dataFetcher(
                    "updateUser",
                    env ->
                        updateUser(
                            GraphQLWiring.inputArgument(env, "changes", UpdateUserInput.class),
                            env)));
  }

  public DataFetcherResult<UserResult> createUser(
      CreateUserInput input, DataFetchingEnvironment dfe) {
    RegisterParam registerParam =
        new RegisterParam(input.getEmail(), input.getUsername(), input.getPassword());
    User user;
    try {
      user = userService.createUser(registerParam);
    } catch (ConstraintViolationException cve) {
      return DataFetcherResult.<UserResult>newResult()
          .data(GraphQLCustomizeExceptionHandler.getErrorsAsData(cve))
          .build();
    }

    return DataFetcherResult.<UserResult>newResult()
        .data(UserPayload.newBuilder().build())
        .localContext(user)
        .build();
  }

  public DataFetcherResult<UserPayload> login(
      String password, String email, DataFetchingEnvironment dfe) {
    Optional<User> optional = userRepository.findByEmail(email);
    if (optional.isPresent() && passwordHasher.matches(password, optional.get().getPassword())) {
      return DataFetcherResult.<UserPayload>newResult()
          .data(UserPayload.newBuilder().build())
          .localContext(optional.get())
          .build();
    } else {
      throw new InvalidAuthenticationException();
    }
  }

  public DataFetcherResult<UserPayload> updateUser(
      UpdateUserInput updateUserInput, DataFetchingEnvironment dfe) {
    io.spring.core.user.User currentUser = SecurityUtil.getCurrentUser(dfe).orElse(null);
    if (currentUser == null) {
      return null;
    }
    UpdateUserParam param =
        UpdateUserParam.builder()
            .username(updateUserInput.getUsername())
            .email(updateUserInput.getEmail())
            .bio(updateUserInput.getBio())
            .password(updateUserInput.getPassword())
            .image(updateUserInput.getImage())
            .build();

    userService.updateUser(new UpdateUserCommand(currentUser, param));
    return DataFetcherResult.<UserPayload>newResult()
        .data(UserPayload.newBuilder().build())
        .localContext(currentUser)
        .build();
  }
}
