package com.codewithsavage.majipoa.shared.config;

import com.codewithsavage.majipoa.auth.AppOidcUserService;
import com.codewithsavage.majipoa.auth.OAuth2LoginSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration — defines WHO can access WHAT.
 *
 * Authorization rules (most specific first):
 *   /h2-console/**        → public (H2 dev console)
 *   /oauth2/**, /login/** → public (OAuth2 handshake URLs, both "google" and "google-admin")
 *   /api/riders/**        → RIDER
 *   /api/admin-auth/**    → authenticated (admin password step; checked inside the controller)
 *   /api/admin/**         → ADMIN role, plus AdminGateInterceptor (admin Google login + password)
 *   /api/vendor/**        → VENDOR
 *   everything else       → authenticated
 *
 * Google callback URLs:
 *   normal users → /login/oauth2/code/google
 *   admin portal → /login/oauth2/code/google/admin   (two path segments, so the
 *                  redirectionEndpoint below is widened to "/login/oauth2/code/**")
 */
@Configuration
public class SecurityConfig {

    @Value("${app.oauth2.admin-login-uri:http://localhost:4200/admin/login}")
    private String adminLoginUri;

    @Value("${app.oauth2.failure-uri:http://localhost:4200/sign-in}")
    private String failureUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppOidcUserService oidcUserService,
                                                   OAuth2LoginSuccessHandler successHandler,
                                                   CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/h2-console/**")
            )

            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )

            .authorizeHttpRequests(auth -> auth
                   .requestMatchers("/api/riders/me/**").authenticated()
   .requestMatchers("/api/riders/**").hasRole("RIDER")
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/api/riders/**").hasRole("RIDER")
                .requestMatchers("/api/admin-auth/**").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/vendor/**").hasRole("VENDOR")
                .requestMatchers("/api/auth/**").authenticated()
                .anyRequest().authenticated()
            )

            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**")
                )
            )

            .oauth2Login(oauth -> oauth
                // accept callbacks with more than one path segment after /code/
                .redirectionEndpoint(redirect -> redirect.baseUri("/login/oauth2/code/**"))
                .userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
                .successHandler(successHandler)
                .failureHandler((request, response, exception) -> {
                    String code = (exception instanceof OAuth2AuthenticationException oe)
                            ? oe.getError().getErrorCode() : "login_failed";
                    String target = "not_admin".equals(code)
                            ? adminLoginUri + "?error=not_admin"
                            : failureUri + "?error=" + code;
                    response.sendRedirect(target);
                })
            );

        return http.build();
    }
}