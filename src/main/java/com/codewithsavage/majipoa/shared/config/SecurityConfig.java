package com.codewithsavage.majipoa.shared.config;

import com.codewithsavage.majipoa.auth.AppOidcUserService;
import com.codewithsavage.majipoa.auth.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration — defines WHO can access WHAT.
 *
 * Request flow for an unauthenticated browser request:
 *   1. Browser hits /api/vendor/dashboard → Spring returns 401 (not a redirect)
 *   2. Angular intercepts 401, redirects user to /sign-in
 *   3. User clicks "Continue with Google"
 *   4. Angular redirects to /oauth2/authorization/google (this backend)
 *   5. Spring redirects to Google's sign-in page
 *   6. User signs in at Google
 *   7. Google redirects back to /login/oauth2/code/google (this backend)
 *   8. Spring calls AppOidcUserService to upsert the user in our DB
 *   9. OAuth2LoginSuccessHandler redirects to http://localhost:4200/auth/callback
 *  10. Angular /auth/callback calls GET /api/auth/me and routes by role
 *
 * Authorization rules (most specific first):
 *   /h2-console/**      → public   (H2 dev console)
 *   /oauth2/**, /login/** → public (OAuth2 handshake URLs)
 *   /api/auth/**        → any authenticated user
 *   /api/vendor/**      → VENDOR role only
 *   everything else     → authenticated
 */
@Configuration
public class SecurityConfig {

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
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/**", "/login/**").permitAll()
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
                .userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
                .successHandler(successHandler)
            );

        return http.build();
    }
}
