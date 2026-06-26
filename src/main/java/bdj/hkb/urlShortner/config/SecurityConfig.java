package bdj.hkb.urlShortner.config;

import bdj.hkb.urlShortner.security.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.OffsetDateTime;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for REST APIs
                .cors(cors -> cors.configure(http))// Safe to disable for stateless APIs
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        // 401 — no authentication provided or token invalid/blacklisted
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("""
                                                       {"status":401,"message":"Authentication required","timestamp":"%s"}
                                                       """.formatted(OffsetDateTime.now()));
                        })
                        // 403 — authenticated but not authorized (e.g. @PreAuthorize failed)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("""
                                                       {"status":403,"message":"Access denied","timestamp":"%s"}
                                                       """.formatted(OffsetDateTime.now()));
                        })
                )
                .authorizeHttpRequests(auth -> auth

                        // Url controller
                        .requestMatchers(HttpMethod.GET, "/urls/{shortCode}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/urls").permitAll()
                        .requestMatchers(HttpMethod.GET, "/urls/**").hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/urls/**").hasRole("USER")
                        .requestMatchers(HttpMethod.PATCH, "/urls/**").hasRole("USER")

                        // Auth Controller
                        .requestMatchers(HttpMethod.POST, "/auth/refresh","/auth/logout").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/auth/**").permitAll()

                        .requestMatchers("/stats/**").hasAnyRole("USER","ADMIN")
                        .requestMatchers("/clicks/**").hasAnyRole("USER", "ADMIN")

                        .anyRequest().permitAll()
                )
                // Inject your copied filter right before Spring's default auth filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
