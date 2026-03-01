package com.arcadigitalis.backend.api;

import com.arcadigitalis.backend.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (no JWT required)
                .requestMatchers(HttpMethod.POST, "/auth/nonce").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/verify").permitAll()
                .requestMatchers(HttpMethod.GET, "/config").permitAll()
                .requestMatchers(HttpMethod.GET, "/packages/*/status").permitAll()
                .requestMatchers(HttpMethod.GET, "/packages/*/recovery-kit").permitAll()
                .requestMatchers(HttpMethod.POST, "/packages/*/tx/renew").permitAll()
                .requestMatchers(HttpMethod.POST, "/packages/*/tx/rescue").permitAll()
                .requestMatchers(HttpMethod.GET, "/acc-template").permitAll()
                .requestMatchers(HttpMethod.POST, "/validate-manifest").permitAll()
                .requestMatchers(HttpMethod.GET, "/events").permitAll()
                .requestMatchers(HttpMethod.GET, "/artifacts/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/health/**").permitAll()
                // SpringDoc / Swagger
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
