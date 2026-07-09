package com.tinet.flowfoundry.sdk;

import com.tinet.flowfoundry.config.FlowFoundryCoreConfiguration;
import com.tinet.flowfoundry.config.FlowFoundryPlatformConfiguration;
import com.tinet.flowfoundry.config.FlowFoundryProperties;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Platform boot only ({@code flowfoundry-core}). Business Worker apps use {@link EnableFlowFoundryWorker}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({FlowFoundryCoreConfiguration.class, FlowFoundryPlatformConfiguration.class})
@EnableConfigurationProperties(FlowFoundryProperties.class)
public @interface EnableFlowFoundry {}
