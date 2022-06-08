package io.spring.api.security;

import io.micronaut.http.HttpRequest;
import java.util.List;

/**
 * URL authorization rules of the API. Rules are evaluated in order and the first rule matching the
 * request method and path decides; requests matching no rule require authentication.
 */
public final class WebSecurityConfig {

  enum Access {
    PERMIT_ALL,
    AUTHENTICATED
  }

  static final class Rule {
    private final String method;
    private final List<String> patterns;
    private final Access access;

    Rule(String method, Access access, String... patterns) {
      this.method = method;
      this.patterns = List.of(patterns);
      this.access = access;
    }

    boolean matches(String requestMethod, String path) {
      if (method != null && !method.equals(requestMethod)) {
        return false;
      }
      return patterns.stream().anyMatch(pattern -> matchesPattern(pattern, path));
    }
  }

  static final List<Rule> RULES =
      List.of(
          new Rule("OPTIONS", Access.PERMIT_ALL, "/**"),
          new Rule(null, Access.PERMIT_ALL, "/graphiql"),
          new Rule(null, Access.PERMIT_ALL, "/graphql"),
          new Rule("GET", Access.AUTHENTICATED, "/articles/feed"),
          new Rule("POST", Access.PERMIT_ALL, "/users", "/users/login"),
          new Rule("GET", Access.PERMIT_ALL, "/articles/**", "/profiles/**", "/tags"));

  private WebSecurityConfig() {}

  static boolean requiresAuthentication(HttpRequest<?> request, String path) {
    String method = request.getMethodName();
    for (Rule rule : RULES) {
      if (rule.matches(method, path)) {
        return rule.access == Access.AUTHENTICATED;
      }
    }
    return true;
  }

  /** Ant-style match supporting exact paths, {@code /**} and a trailing {@code /**}. */
  static boolean matchesPattern(String pattern, String path) {
    if ("/**".equals(pattern) || "**".equals(pattern)) {
      return true;
    }
    if (pattern.endsWith("/**")) {
      String prefix = pattern.substring(0, pattern.length() - 3);
      return path.startsWith(prefix)
          && (path.length() == prefix.length() || path.charAt(prefix.length()) == '/');
    }
    return pattern.equals(path);
  }
}
