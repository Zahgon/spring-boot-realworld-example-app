package io.spring.validation;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Bean Validation wiring. Constraint validators that are beans (for example those that query a
 * repository) are taken from the application context; the others are instantiated directly.
 */
@Factory
public class ValidationFactory {

  @Bean(preDestroy = "close")
  @Singleton
  public ValidatorFactory validatorFactory(BeanContext beanContext) {
    ConstraintValidatorFactory defaults =
        Validation.byProvider(HibernateValidator.class)
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .getDefaultConstraintValidatorFactory();
    return Validation.byProvider(HibernateValidator.class)
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .constraintValidatorFactory(
            new BeanContextConstraintValidatorFactory(beanContext, defaults))
        .buildValidatorFactory();
  }

  @Singleton
  public Validator validator(ValidatorFactory validatorFactory) {
    return validatorFactory.getValidator();
  }

  static final class BeanContextConstraintValidatorFactory implements ConstraintValidatorFactory {
    private final BeanContext beanContext;
    private final ConstraintValidatorFactory defaults;

    BeanContextConstraintValidatorFactory(
        BeanContext beanContext, ConstraintValidatorFactory defaults) {
      this.beanContext = beanContext;
      this.defaults = defaults;
    }

    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
      return beanContext.findBean(key).orElseGet(() -> defaults.getInstance(key));
    }

    @Override
    public void releaseInstance(ConstraintValidator<?, ?> instance) {
      if (!beanContext.findBeanRegistration(instance).isPresent()) {
        defaults.releaseInstance(instance);
      }
    }
  }
}
