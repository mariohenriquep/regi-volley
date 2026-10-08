package com.regivolley.api.infrastructure.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the controller parameter that receives the caller: {@code @CurrentActor AuthenticatedActor caller}. It is
 * {@code @AuthenticationPrincipal} under a name of ours, so controllers import nothing from Spring Security.
 */
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentActor {
}
