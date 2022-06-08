package io.spring.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.spring.application.ProfileQueryService;
import io.spring.application.data.ProfileData;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class ProfileApiTest extends TestWithCurrentUser {
  private User anotherUser;

  @Inject private ProfileQueryService profileQueryService;

  private ProfileData profileData;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    anotherUser = new User("username@test.com", "username", "123", "", "");
    profileData =
        new ProfileData(
            anotherUser.getId(),
            anotherUser.getUsername(),
            anotherUser.getBio(),
            anotherUser.getImage(),
            false);
    when(userRepository.findByUsername(eq(anotherUser.getUsername())))
        .thenReturn(Optional.of(anotherUser));
  }

  @Test
  public void should_get_user_profile_success() throws Exception {
    when(profileQueryService.findByUsername(eq(profileData.getUsername()), eq(null)))
        .thenReturn(Optional.of(profileData));
    RestAssured.when()
        .get("/profiles/{username}", profileData.getUsername())
        .then()
        .statusCode(200)
        .body("profile.username", equalTo(profileData.getUsername()));
  }

  @Test
  public void should_follow_user_success() throws Exception {
    when(profileQueryService.findByUsername(eq(profileData.getUsername()), eq(user)))
        .thenReturn(Optional.of(profileData));
    given()
        .header("Authorization", "Token " + token)
        .when()
        .post("/profiles/{username}/follow", anotherUser.getUsername())
        .then()
        .statusCode(200);
    verify(userRepository).saveRelation(new FollowRelation(user.getId(), anotherUser.getId()));
  }

  @Test
  public void should_unfollow_user_success() throws Exception {
    FollowRelation followRelation = new FollowRelation(user.getId(), anotherUser.getId());
    when(userRepository.findRelation(eq(user.getId()), eq(anotherUser.getId())))
        .thenReturn(Optional.of(followRelation));
    when(profileQueryService.findByUsername(eq(profileData.getUsername()), eq(user)))
        .thenReturn(Optional.of(profileData));

    given()
        .header("Authorization", "Token " + token)
        .when()
        .delete("/profiles/{username}/follow", anotherUser.getUsername())
        .then()
        .statusCode(200);

    verify(userRepository).removeRelation(eq(followRelation));
  }

  @MockBean(ProfileQueryService.class)
  ProfileQueryService profileQueryServiceMock() {
    return mock(ProfileQueryService.class);
  }
}
