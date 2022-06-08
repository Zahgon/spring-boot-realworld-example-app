package io.spring.application.user;

import io.spring.core.user.UserRepository;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

@Singleton
class DuplicatedUsernameValidator
    implements ConstraintValidator<DuplicatedUsernameConstraint, String> {

  private final UserRepository userRepository;

  DuplicatedUsernameValidator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return (value == null || value.isEmpty()) || !userRepository.findByUsername(value).isPresent();
  }
}
