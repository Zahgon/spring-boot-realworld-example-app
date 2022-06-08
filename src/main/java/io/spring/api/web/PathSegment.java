package io.spring.api.web;

import io.micronaut.core.bind.annotation.Bindable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a variable of the route's URI template from the raw request path, percent-decoded with
 * {@code +} kept as is. The parameter's Java name must differ from the variable's name: Micronaut
 * binds a parameter named like a URI variable from its own decoding before any binder runs.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Bindable
public @interface PathSegment {
  /** The name of the URI template variable. */
  String value();
}
