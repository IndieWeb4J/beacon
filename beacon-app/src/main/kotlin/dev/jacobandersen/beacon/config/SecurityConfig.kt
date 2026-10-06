package dev.jacobandersen.beacon.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * The webmention receiver is intentionally unauthenticated (the spec has no
 * auth); abuse is bounded by the submission limiter and host validation.
 * Everything else is permit-all (actuator health, etc.).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
