package com.shortvideo.backend.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;

    public SecurityConfig(TokenAuthenticationFilter tokenAuthenticationFilter) {
        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling((exceptions) -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> writeError(response, 401, "Unauthorized"))
                        .accessDeniedHandler((request, response, ex) -> writeError(response, 403, "Forbidden")))
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/api/health", "/api/health/**").permitAll()
                        .requestMatchers("/static/**", "/images/**", "/videos/**", "/demo-upload/**").permitAll()
                        .requestMatchers("/api/admin/auth/login").permitAll()
                        .requestMatchers("/api/h5/auth/**").permitAll()
                        .requestMatchers("/api/h5/payments/callback").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/h5/dramas",
                                "/api/h5/dramas/**",
                                "/api/h5/episodes",
                                "/api/h5/storylines",
                                "/api/h5/snapshot",
                                "/h5/snapshot"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/h5/episodes/access-check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/snapshot", "/admin/snapshot").hasAuthority("dashboard")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/snapshot", "/admin/snapshot").hasAuthority("settings")
                        .requestMatchers("/api/admin/dashboard", "/api/admin/dashboard/**").hasAuthority("dashboard")
                        .requestMatchers("/api/admin/dramas", "/api/admin/dramas/**").hasAuthority("content")
                        .requestMatchers("/api/admin/story-pools", "/api/admin/story-pools/**").hasAuthority("storyline")
                        .requestMatchers("/api/admin/orders", "/api/admin/orders/**").hasAuthority("orders")
                        .requestMatchers("/api/admin/finance", "/api/admin/finance/**").hasAuthority("finance")
                        .requestMatchers("/api/admin/channels", "/api/admin/channels/**").hasAuthority("channels")
                        .requestMatchers("/api/admin/media-assets", "/api/admin/media-assets/**").hasAuthority("media")
                        .requestMatchers("/api/admin/users", "/api/admin/users/**").hasAuthority("users")
                        .requestMatchers("/api/admin/roles", "/api/admin/roles/**").hasAuthority("roles")
                        .requestMatchers("/api/admin/settings", "/api/admin/settings/**").hasAuthority("settings")
                        .requestMatchers("/api/admin/audit-logs", "/api/admin/audit-logs/**").hasAuthority("settings")
                        .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/h5/me",
                                "/api/h5/me/**",
                                "/api/h5/payments",
                                "/api/h5/payments/**",
                                "/api/h5/storylines/draw",
                                "/api/h5/play-events",
                                "/api/h5/episodes/*/like"
                        ).hasRole("H5_USER")
                        .anyRequest().denyAll())
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return (username) -> {
            throw new UsernameNotFoundException(username);
        };
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
