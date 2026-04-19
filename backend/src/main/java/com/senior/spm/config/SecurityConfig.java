package com.senior.spm.config;

import java.util.Collection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
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
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        .requestMatchers("/api/admin/**")
                        .hasAnyAuthority("ROLE_Admin", "ROLE_ADMIN")

                        .requestMatchers("/api/coordinator/**", "/api/committees/**")
                        .hasAnyAuthority("ROLE_Coordinator", "ROLE_COORDINATOR")

                        .requestMatchers("/api/professor/**", "/api/professors/**", "/api/advisor/**")
                        .hasAnyAuthority("ROLE_Professor", "ROLE_PROFESSOR")

                        .requestMatchers("/api/advisors", "/api/groups/*/advisor-request")
                        .hasAnyAuthority("ROLE_Student", "ROLE_STUDENT")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");

                            String authorityText = "UNKNOWN";
                            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                                Collection<? extends GrantedAuthority> authorities =
                                        SecurityContextHolder.getContext().getAuthentication().getAuthorities();
                                if (authorities != null && !authorities.isEmpty()) {
                                    authorityText = authorities.iterator().next().getAuthority();
                                }
                            }

                            String message = "Role: " + authorityText
                                    + " does not have permission to access this resource. "
                                    + "Exception: " + exception.getMessage();

                            response.getWriter().write(
                                    objectMapper.writeValueAsString(new ErrorMessage(message))
                            );
                        })
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            new ErrorMessage("Unauthorized: " + exception.getMessage())
                                    )
                            );
                        })
                )
                .build();
    }
}