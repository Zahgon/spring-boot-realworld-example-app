package io.spring.api.security;

import io.micronaut.http.HttpRequest;
import io.spring.core.user.User;
import java.util.Optional;

/** Access to the user authenticated for a request by {@link WebSecurityFilter}. */
public final class Authentication {
  static final String CURRENT_USER_ATTRIBUTE = Authentication.class.getName() + ".USER";

  private Authentication() {}

  public static Optional<User> currentUser(HttpRequest<?> request) {
    return request.getAttribute(CURRENT_USER_ATTRIBUTE, User.class);
  }

  static void authenticate(HttpRequest<?> request, User user) {
    request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
  }
}
