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
 * 在平台或单体应用启动类上标注，引入 FlowFoundry 平台（建模器、REST API、Workflow 持久化、Temporal Worker）。
 *
 * <p>业务场景 Worker 请使用 {@link EnableFlowFoundryWorker}，通过 {@code flowfoundry-core :8081} 访问平台 HTTP。
 *
 * <pre>{@code
 * @SpringBootApplication(scanBasePackages = "com.tinet.flowfoundry")
 * @EnableFlowFoundry
 * public class FlowFoundryCoreApplication { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({FlowFoundryCoreConfiguration.class, FlowFoundryPlatformConfiguration.class})
@EnableConfigurationProperties(FlowFoundryProperties.class)
public @interface EnableFlowFoundry {}
