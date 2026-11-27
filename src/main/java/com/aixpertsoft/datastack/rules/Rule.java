package com.aixpertsoft.datastack.rules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a business rule. The annotation is the rule's only metadata: which object it
 * applies to, on which events, and in which order. The class needs one method
 * {@code public void apply(BeforeSave<X> event)}.
 *
 * <p>Rules are Spring beans, so they can inject DAOs and services.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@org.springframework.stereotype.Component
public @interface Rule {

    /** The entity class the rule applies to; subclasses are included. */
    Class<?> on();

    Event[] events();

    /** Lower runs first. */
    int order() default 100;
}
