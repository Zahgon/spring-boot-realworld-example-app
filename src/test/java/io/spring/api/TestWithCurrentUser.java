package io.spring.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import io.restassured.RestAssured;
import io.spring.application.data.UserData;
import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.infrastructure.mybatis.readservice.UserReadService;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;

abstract class TestWithCurrentUser {
  @Inject protected EmbeddedServer server;

  @Inject protected UserRepository userRepository;

  @Inject protected UserReadService userReadService;

  protected User user;
  protected UserData userData;
  protected String token;
  protected String email;
  protected String username;
  protected String defaultAvatar;

  @Inject protected JwtService jwtService;

  protected void userFixture() {
    email = "john@jacob.com";
    username = "johnjacob";
    defaultAvatar = "https://static.productionready.io/images/smiley-cyrus.jpg";

    user = new User(email, username, "123", "", defaultAvatar);
    when(userRepository.findByUsername(eq(username))).thenReturn(Optional.of(user));
    when(userRepository.findById(eq(user.getId()))).thenReturn(Optional.of(user));

    userData = new UserData(user.getId(), email, username, "", defaultAvatar);
    when(userReadService.findById(eq(user.getId()))).thenReturn(userData);

    token = "token";
    when(jwtService.getSubFromToken(eq(token))).thenReturn(Optional.of(user.getId()));
  }

  @BeforeEach
  public void setUp() throws Exception {
    RestAssured.port = server.getPort();
    userFixture();
  }

  @MockBean(UserRepository.class)
  UserRepository userRepositoryMock() {
    return mock(UserRepository.class);
  }

  @MockBean(UserReadService.class)
  UserReadService userReadServiceMock() {
    return mock(UserReadService.class);
  }

  @MockBean(JwtService.class)
  JwtService jwtServiceMock() {
    return mock(JwtService.class);
  }
}
