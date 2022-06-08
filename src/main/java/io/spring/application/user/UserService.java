package io.spring.application.user;

import io.micronaut.context.annotation.Value;
import io.spring.application.ServiceArgumentValidator;
import io.spring.core.service.PasswordHasher;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Valid;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Singleton
public class UserService {
  private UserRepository userRepository;
  private String defaultImage;
  private PasswordHasher passwordHasher;
  private ServiceArgumentValidator argumentValidator;

  @Inject
  public UserService(
      UserRepository userRepository,
      @Value("${image.default}") String defaultImage,
      PasswordHasher passwordHasher,
      ServiceArgumentValidator argumentValidator) {
    this.userRepository = userRepository;
    this.defaultImage = defaultImage;
    this.passwordHasher = passwordHasher;
    this.argumentValidator = argumentValidator;
  }

  public User createUser(@Valid RegisterParam registerParam) {
    argumentValidator.validate(this, "createUser", registerParam);
    User user =
        new User(
            registerParam.getEmail(),
            registerParam.getUsername(),
            passwordHasher.hash(registerParam.getPassword()),
            "",
            defaultImage);
    userRepository.save(user);
    return user;
  }

  public void updateUser(@Valid UpdateUserCommand command) {
    argumentValidator.validate(this, "updateUser", command);
    User user = command.getTargetUser();
    UpdateUserParam updateUserParam = command.getParam();
    user.update(
        updateUserParam.getEmail(),
        updateUserParam.getUsername(),
        updateUserParam.getPassword(),
        updateUserParam.getBio(),
        updateUserParam.getImage());
    userRepository.save(user);
  }
}

@Constraint(validatedBy = UpdateUserValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@interface UpdateUserConstraint {

  String message() default "invalid update param";

  Class[] groups() default {};

  Class[] payload() default {};
}

@Singleton
class UpdateUserValidator implements ConstraintValidator<UpdateUserConstraint, UpdateUserCommand> {

  private final UserRepository userRepository;

  UpdateUserValidator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public boolean isValid(UpdateUserCommand value, ConstraintValidatorContext context) {
    String inputEmail = value.getParam().getEmail();
    String inputUsername = value.getParam().getUsername();
    final User targetUser = value.getTargetUser();

    boolean isEmailValid =
        userRepository.findByEmail(inputEmail).map(user -> user.equals(targetUser)).orElse(true);
    boolean isUsernameValid =
        userRepository
            .findByUsername(inputUsername)
            .map(user -> user.equals(targetUser))
            .orElse(true);
    if (isEmailValid && isUsernameValid) {
      return true;
    } else {
      context.disableDefaultConstraintViolation();
      if (!isEmailValid) {
        context
            .buildConstraintViolationWithTemplate("email already exist")
            .addPropertyNode("email")
            .addConstraintViolation();
      }
      if (!isUsernameValid) {
        context
            .buildConstraintViolationWithTemplate("username already exist")
            .addPropertyNode("username")
            .addConstraintViolation();
      }
      return false;
    }
  }
}
