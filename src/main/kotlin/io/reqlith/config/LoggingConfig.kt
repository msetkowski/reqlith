package io.reqlith.config

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Request id and user id on every log line (see `logging.pattern.correlation`). */
@Configuration(proxyBeanMethods = false)
class LoggingConfig {

    /** Before Spring Security, so authentication logs carry the request id. */
    @Bean
    fun requestLoggingFilter() = FilterRegistrationBean(RequestLoggingFilter()).apply {
        order = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1
        addUrlPatterns("/*")
    }

    /** After Spring Security, which has resolved the signed-in user by then. */
    @Bean
    fun userLoggingFilter() = FilterRegistrationBean(UserLoggingFilter()).apply {
        order = SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1
        addUrlPatterns("/*")
    }
}
