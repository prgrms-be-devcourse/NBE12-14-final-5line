package com.outpick.global.config;

import com.outpick.domain.member.security.AccessCookieAuthenticationFilter;
import com.outpick.domain.member.security.AuthTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthTokenService tokens) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(new AccessCookieAuthenticationFilter(tokens), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/validate", "/api/v1/auth/password-reset/confirm").permitAll()
                        .requestMatchers("/api/v1/auth/password-reset/validate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/places", "/api/v1/places/filters", "/api/v1/places/{placeId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/places/{placeId}/verified-details", "/api/v1/places/{placeId}/snorkeling-safety", "/api/v1/places/{placeId}/fishery-protection").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/places/{placeId}/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/review-photos/{assetId}").permitAll()
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, error) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .logout(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
