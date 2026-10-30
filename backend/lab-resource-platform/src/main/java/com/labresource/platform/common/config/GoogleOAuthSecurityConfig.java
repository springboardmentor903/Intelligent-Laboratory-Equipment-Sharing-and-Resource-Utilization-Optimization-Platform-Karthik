package com.labresource.platform.common.config;

import com.labresource.platform.auth.controller.GoogleOAuthSuccessHandler;
import com.labresource.platform.auth.service.OAuthLoginTicketService;
import com.labresource.platform.user.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "app.oauth.google.enabled", havingValue = "true")
public class GoogleOAuthSecurityConfig {

    @Bean
    GoogleOAuthSuccessHandler googleOAuthSuccessHandler(
            UserService users,
            OAuthLoginTicketService tickets,
            org.springframework.core.env.Environment environment) {
        return new GoogleOAuthSuccessHandler(
                users, tickets,
                environment.getProperty("app.oauth.google.frontend-url", "http://localhost:5173"));
    }

    @Bean
    @Order(1)
    SecurityFilterChain googleOAuthSecurityFilterChain(
            HttpSecurity http,
            GoogleOAuthSuccessHandler successHandler) throws Exception {
        http.securityMatcher("/oauth2/**", "/login/oauth2/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth.successHandler(successHandler));
        return http.build();
    }
}
