package io.spring.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.spring.api.security.CurrentUser;
import io.spring.api.web.RequestBodyReader;
import io.spring.application.UserQueryService;
import io.spring.application.data.UserData;
import io.spring.application.data.UserWithToken;
import io.spring.application.user.UpdateUserCommand;
import io.spring.application.user.UpdateUserParam;
import io.spring.application.user.UserService;
import io.spring.core.user.User;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;

@Controller("/user")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class CurrentUserApi {

  private UserQueryService userQueryService;
  private UserService userService;
  private RequestBodyReader requestBodyReader;

  @Get
  public HttpResponse<?> currentUser(
      @Nullable @CurrentUser User currentUser, @Header("Authorization") String authorization) {
    UserData userData = userQueryService.findById(currentUser.getId()).get();
    return HttpResponse.ok(userResponse(new UserWithToken(userData, authorization.split(" ")[1])));
  }

  @Put
  public HttpResponse<?> updateProfile(
      HttpRequest<?> request,
      @Nullable @CurrentUser User currentUser,
      @Header("Authorization") String token,
      @Nullable @Body byte[] body) {
    UpdateUserParam updateUserParam =
        requestBodyReader.readValid(request, body, UpdateUserParam.class);
    userService.updateUser(new UpdateUserCommand(currentUser, updateUserParam));
    UserData userData = userQueryService.findById(currentUser.getId()).get();
    return HttpResponse.ok(userResponse(new UserWithToken(userData, token.split(" ")[1])));
  }

  private Map<String, Object> userResponse(UserWithToken userWithToken) {
    return new HashMap<String, Object>() {
      {
        put("user", userWithToken);
      }
    };
  }
}
