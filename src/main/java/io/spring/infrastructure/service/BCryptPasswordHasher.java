package io.spring.infrastructure.service;

import io.spring.core.service.PasswordHasher;
import jakarta.inject.Singleton;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

/** BCrypt hashes in the {@code $2a$10$} format, compatible with previously stored passwords. */
@Singleton
public class BCryptPasswordHasher implements PasswordHasher {
  private static final Pattern BCRYPT_PATTERN =
      Pattern.compile("\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}");
  private static final int STRENGTH = 10;

  @Override
  public String hash(String rawPassword) {
    if (rawPassword == null) {
      throw new IllegalArgumentException("rawPassword cannot be null");
    }
    return BCrypt.hashpw(rawPassword, BCrypt.gensalt(STRENGTH));
  }

  @Override
  public boolean matches(String rawPassword, String hash) {
    if (rawPassword == null) {
      throw new IllegalArgumentException("rawPassword cannot be null");
    }
    if (hash == null || hash.isEmpty()) {
      return false;
    }
    if (!BCRYPT_PATTERN.matcher(hash).matches()) {
      return false;
    }
    return BCrypt.checkpw(rawPassword, hash);
  }
}
