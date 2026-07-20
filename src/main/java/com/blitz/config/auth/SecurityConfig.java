package com.blitz.config.auth;

import com.blitz.domain.user.Role;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        PathPatternRequestMatcher apiRequest = PathPatternRequestMatcher.pathPattern("/api/**");

        http
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "base-uri 'self'; "
                                        + "connect-src 'self'; "
                                        + "img-src 'self' data:; "
                                        + "object-src 'none'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self'; "
                                        + "form-action 'self'; "
                                        + "frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(
                                "/", "/error", "/favicon.ico", "/css/**", "/images/**", "/js/**",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/posts/save", "/posts/*/edit").hasRole(Role.USER.name())
                        .requestMatchers(HttpMethod.GET,
                                "/posts/{id}", "/posts/update/{id}",
                                "/api/v1/posts", "/api/v1/posts/*",
                                "/api/v1/posts/*/comments").permitAll()
                        .requestMatchers("/api/v1/**").hasRole(Role.USER.name())
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiRequest)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                PathPatternRequestMatcher.pathPattern("/**"))
                        .defaultAccessDeniedHandlerFor(
                                (request, response, denied) -> response.sendError(HttpStatus.FORBIDDEN.value()),
                                apiRequest))
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .deleteCookies("JSESSIONID"))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)));

        return http.build();
    }
}
