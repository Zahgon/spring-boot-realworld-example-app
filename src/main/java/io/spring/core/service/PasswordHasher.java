package io.spring.core.service;

/** One-way hashing of user passwords for storage, and checking a password against a stored hash. */
public interface PasswordHasher {
  String hash(String rawPassword);

  boolean matches(String rawPassword, String hash);
}
