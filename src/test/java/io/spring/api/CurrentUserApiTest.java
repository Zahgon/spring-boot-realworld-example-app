package io.spring.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.spring.application.UserQueryService;
import io.spring.core.user.User;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class CurrentUserApiTest extends TestWithCurrentUser {

  @Inject private UserQueryService userQueryService;

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void should_get_current_user_with_token() throws Exception {
    when(userQueryService.findById(any())).thenReturn(Optional.of(userData));

    given()
        .header("Authorization", "Token " + token)
        .contentType("application/json")
        .when()
        .get("/user")
        .then()
        .statusCode(200)
        .body("user.email", equalTo(email))
        .body("user.username", equalTo(username))
        .body("user.bio", equalTo(""))
        .body("user.image", equalTo(defaultAvatar))
        .body("user.token", equalTo(token));
  }

  @Test
  public void should_get_401_without_token() throws Exception {
    given().contentType("application/json").when().get("/user").then().statusCode(401);
  }

  @Test
  public void should_get_401_with_invalid_token() throws Exception {
    String invalidToken = "asdfasd";
    when(jwtService.getSubFromToken(eq(invalidToken))).thenReturn(Optional.empty());
    given()
        .contentType("application/json")
        .header("Authorization", "Token " + invalidToken)
        .when()
        .get("/user")
        .then()
        .statusCode(401);
  }

  @Test
  public void should_update_current_user_profile() throws Exception {
    String newEmail = "newemail@example.com";
    String newBio = "updated";
    String newUsername = "newusernamee";

    Map<String, Object> param =
        new HashMap<String, Object>() {
          {
            put(
                "user",
                new HashMap<String, Object>() {
                  {
                    put("email", newEmail);
                    put("bio", newBio);
                    put("username", newUsername);
                  }
                });
          }
        };

    when(userRepository.findByUsername(eq(newUsername))).thenReturn(Optional.empty());
    when(userRepository.findByEmail(eq(newEmail))).thenReturn(Optional.empty());

    when(userQueryService.findById(eq(user.getId()))).thenReturn(Optional.of(userData));

    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(param)
        .when()
        .put("/user")
        .then()
        .statusCode(200);
  }

  @Test
  public void should_get_error_if_email_exists_when_update_user_profile() throws Exception {
    String newEmail = "newemail@example.com";
    String newBio = "updated";
    String newUsername = "newusernamee";

    Map<String, Object> param = prepareUpdateParam(newEmail, newBio, newUsername);

    when(userRepository.findByEmail(eq(newEmail)))
        .thenReturn(Optional.of(new User(newEmail, "username", "123", "", "")));
    when(userRepository.findByUsername(eq(newUsername))).thenReturn(Optional.empty());

    when(userQueryService.findById(eq(user.getId()))).thenReturn(Optional.of(userData));

    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(param)
        .when()
        .put("/user")
        .then()
        .statusCode(422)
        .body("errors.email[0]", equalTo("email already exist"));
  }

  private HashMap<String, Object> prepareUpdateParam(
      final String newEmail, final String newBio, final String newUsername) {
    return new HashMap<String, Object>() {
      {
        put(
            "user",
            new HashMap<String, Object>() {
              {
                put("email", newEmail);
                put("bio", newBio);
                put("username", newUsername);
              }
            });
      }
    };
  }

  @Test
  public void should_get_401_if_not_login() throws Exception {
    given()
        .contentType("application/json")
        .body(
            new HashMap<String, Object>() {
              {
                put("user", new HashMap<String, Object>());
              }
            })
        .when()
        .put("/user")
        .then()
        .statusCode(401);
  }

  @MockBean(UserQueryService.class)
  UserQueryService userQueryServiceMock() {
    return mock(UserQueryService.class);
  }
}
