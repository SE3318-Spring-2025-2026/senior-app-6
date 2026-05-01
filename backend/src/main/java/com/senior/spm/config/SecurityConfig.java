package com.senior.spm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.filter.JwtAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/coordinator/deliverables/**").hasAnyRole("COORDINATOR", "PROFESSOR")
                                .requestMatchers("/api/coordinator/**").hasRole("COORDINATOR")
                                .requestMatchers("/api/professor/**").hasRole("PROFESSOR")
                                .requestMatchers("/api/professors/**").hasRole("PROFESSOR")
                                .requestMatchers("/api/committees/*/submissions").hasRole("PROFESSOR")
                                .requestMatchers("/api/committees/**").hasRole("COORDINATOR")
                                // P3: Student-facing advisor request endpoints.
                                .requestMatchers("/api/advisor").hasRole("STUDENT")
                                // P3: Professor-only endpoints live under /api/advisor/**
                                .requestMatchers("/api/advisor/**").hasRole("PROFESSOR")
                                .requestMatchers("/api/groups/*/advisor-request").hasRole("STUDENT")
                                .requestMatchers("/api/deliverables").hasRole("STUDENT")
                                .requestMatchers("/api/deliverables/*/submissions").hasRole("STUDENT")
                                .requestMatchers("/api/submissions/**").hasAnyRole("STUDENT", "PROFESSOR")
                                .anyRequest().authenticated())
                .exceptionHandling(
                        ex -> ex.accessDeniedHandler(
                                (request, response, exception) -> {
                                    response.setStatus(403);
                                    response.setContentType("application/json");
                                    var authority = (SimpleGrantedAuthority) SecurityContextHolder
                                            .getContext()
                                            .getAuthentication()
                                            .getAuthorities()
                                            .iterator().next();
                                    var message = "Role: " + authority.getAuthority() + " does not have permission to access this resource.";
                                    response.getWriter().write(
                                            objectMapper.writeValueAsString(
                                                    new ErrorMessage(message + "Exception: " + exception.getMessage())
                                            ));
                                }).authenticationEntryPoint((request, response, exception) -> {
                                    response.setStatus(401);
                                    response.setContentType("application/json");
                                    response.getWriter().write(
                                            objectMapper.writeValueAsString(
                                                    new ErrorMessage("Unauthorized: " + exception.getMessage())
                                            ));
                                }))
                .build();
    }
}
