package io.spring.api;

import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.spring.api.exception.InvalidAuthenticationException;
import io.spring.api.web.RequestBodyReader;
import io.spring.application.UserQueryService;
import io.spring.application.data.UserData;
import io.spring.application.data.UserWithToken;
import io.spring.application.user.RegisterParam;
import io.spring.application.user.UserService;
import io.spring.core.service.JwtService;
import io.spring.core.service.PasswordHasher;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Controller
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class UsersApi {
  private UserRepository userRepository;
  private UserQueryService userQueryService;
  private PasswordHasher passwordHasher;
  private JwtService jwtService;
  private UserService userService;
  private RequestBodyReader requestBodyReader;

  @Post("/users")
  public HttpResponse<?> createUser(HttpRequest<?> request, @Nullable @Body byte[] body) {
    RegisterParam registerParam = requestBodyReader.readValid(request, body, RegisterParam.class);
    User user = userService.createUser(registerParam);
    UserData userData = userQueryService.findById(user.getId()).get();
    return HttpResponse.status(HttpStatus.CREATED)
        .body(userResponse(new UserWithToken(userData, jwtService.toToken(user))));
  }

  @Post("/users/login")
  public HttpResponse<?> userLogin(HttpRequest<?> request, @Nullable @Body byte[] body) {
    LoginParam loginParam = requestBodyReader.readValid(request, body, LoginParam.class);
    Optional<User> optional = userRepository.findByEmail(loginParam.getEmail());
    if (optional.isPresent()
        && passwordHasher.matches(loginParam.getPassword(), optional.get().getPassword())) {
      UserData userData = userQueryService.findById(optional.get().getId()).get();
      return HttpResponse.ok(
          userResponse(new UserWithToken(userData, jwtService.toToken(optional.get()))));
    } else {
      throw new InvalidAuthenticationException();
    }
  }

  private Map<String, Object> userResponse(UserWithToken userWithToken) {
    return new HashMap<String, Object>() {
      {
        put("user", userWithToken);
      }
    };
  }
}

@Getter
@JsonRootName("user")
@NoArgsConstructor
class LoginParam {
  @NotBlank(message = "can't be empty")
  @Email(message = "should be an email")
  private String email;

  @NotBlank(message = "can't be empty")
  private String password;
}
