package io.spring.application.user;

import io.spring.core.user.UserRepository;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

@Singleton
public class DuplicatedEmailValidator
    implements ConstraintValidator<DuplicatedEmailConstraint, String> {

  private final UserRepository userRepository;

  public DuplicatedEmailValidator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return (value == null || value.isEmpty()) || !userRepository.findByEmail(value).isPresent();
  }
}
