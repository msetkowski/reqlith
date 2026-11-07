package io.reqlith.config

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class LoggingConfig {

    /** Request id and user in every log line; registered right after the Spring Security filter chain. */
    @Bean
    fun requestLoggingFilter() = FilterRegistrationBean(RequestLoggingFilter()).apply {
        order = SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1
        addUrlPatterns("/*")
    }
}
