package com.tinet.flowfoundry.config;

import com.tinet.flowfoundry.sdk.EnableFlowFoundry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot 自动配置：当 classpath 存在 flowfoundry-core 且为 Web 应用时，注册平台 Bean。
 * 业务应用仍推荐在启动类显式使用 {@link EnableFlowFoundry}，以便清晰表达依赖关系。
 */
@AutoConfiguration
@ConditionalOnFlowFoundryPlatform
@ConditionalOnWebApplication
@ConditionalOnClass(FlowFoundryCoreConfiguration.class)
@EnableConfigurationProperties(FlowFoundryProperties.class)
@Import({FlowFoundryCoreConfiguration.class, FlowFoundryPlatformConfiguration.class})
public class FlowFoundryAutoConfiguration {}
