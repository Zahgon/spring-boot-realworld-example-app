package io.spring.api.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;

public class WebSecurityConfigTest {

  @Test
  public void should_match_exact_and_trailing_wildcard_patterns() {
    assertTrue(WebSecurityConfig.matchesPattern("/**", "/anything/at/all"));
    assertTrue(WebSecurityConfig.matchesPattern("**", "/"));
    assertTrue(WebSecurityConfig.matchesPattern("/articles/**", "/articles"));
    assertTrue(WebSecurityConfig.matchesPattern("/articles/**", "/articles/a/comments"));
    assertFalse(WebSecurityConfig.matchesPattern("/articles/**", "/articlesx"));
    assertTrue(WebSecurityConfig.matchesPattern("/tags", "/tags"));
    assertFalse(WebSecurityConfig.matchesPattern("/tags", "/tags/"));
  }

  @Test
  public void should_permit_public_endpoints() {
    assertPublic(HttpMethod.POST, "/users");
    assertPublic(HttpMethod.POST, "/users/login");
    assertPublic(HttpMethod.GET, "/articles");
    assertPublic(HttpMethod.GET, "/articles/some-slug/comments");
    assertPublic(HttpMethod.GET, "/profiles/jake");
    assertPublic(HttpMethod.GET, "/tags");
    assertPublic(HttpMethod.OPTIONS, "/user");
    assertPublic(HttpMethod.DELETE, "/graphql");
    assertPublic(HttpMethod.PUT, "/graphiql");
  }

  @Test
  public void should_require_authentication_for_everything_else() {
    assertProtected(HttpMethod.GET, "/articles/feed");
    assertProtected(HttpMethod.POST, "/articles");
    assertProtected(HttpMethod.PUT, "/articles/some-slug");
    assertProtected(HttpMethod.DELETE, "/articles/some-slug/comments/1");
    assertProtected(HttpMethod.POST, "/profiles/jake/follow");
    assertProtected(HttpMethod.GET, "/user");
    assertProtected(HttpMethod.GET, "/users");
    assertProtected(HttpMethod.HEAD, "/tags");
    assertProtected(HttpMethod.GET, "/graphql/schema");
    assertProtected(HttpMethod.GET, "/unknown");
  }

  private static void assertPublic(HttpMethod method, String path) {
    assertFalse(
        WebSecurityConfig.requiresAuthentication(HttpRequest.create(method, path), path),
        method + " " + path);
  }

  private static void assertProtected(HttpMethod method, String path) {
    assertTrue(
        WebSecurityConfig.requiresAuthentication(HttpRequest.create(method, path), path),
        method + " " + path);
  }
}
