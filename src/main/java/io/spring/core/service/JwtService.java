package io.spring.core.service;

import io.spring.core.user.User;
import java.util.Optional;

public interface JwtService {
  String toToken(User user);

  Optional<String> getSubFromToken(String token);
}
