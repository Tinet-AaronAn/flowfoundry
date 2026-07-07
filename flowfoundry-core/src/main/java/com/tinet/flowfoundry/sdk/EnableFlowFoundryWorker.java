package com.tinet.flowfoundry.sdk;

import com.tinet.flowfoundry.config.FlowFoundryProperties;
import com.tinet.flowfoundry.config.FlowFoundryWorkerConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * 在业务 Worker 启动类上标注，引入 Temporal Worker 与 Activity 扩展点，不启动平台 HTTP API。
 *
 * <pre>{@code
 * @SpringBootApplication(scanBasePackages = "com.tinet.flowfoundry.demo.mymodule")
 * @EnableFlowFoundryWorker
 * public class MyWorkerApplication { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FlowFoundryWorkerConfiguration.class)
@EnableConfigurationProperties(FlowFoundryProperties.class)
public @interface EnableFlowFoundryWorker {}
