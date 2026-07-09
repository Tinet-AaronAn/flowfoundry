package com.tinet.flowfoundry.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/** Enables {@link FlowFoundryPlatformClient} and optional App BFF proxy controllers. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FlowFoundryPlatformClientAutoConfiguration.class)
public @interface EnableFlowFoundryPlatformClient {}
