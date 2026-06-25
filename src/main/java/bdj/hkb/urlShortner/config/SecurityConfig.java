package bdj.hkb.urlShortner.config;

import bdj.hkb.urlShortner.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Safe to disable for stateless APIs
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public Routes
//                        .requestMatchers("/ping").permitAll()
//                        // The actual short-link redirect MUST be public
//                        .requestMatchers(HttpMethod.GET, "/{shortCode}").permitAll()
//
//                        // Protected Routes
//                        // All dashboard and link creation endpoints require a valid JWT
//                        .requestMatchers("/api/v1/Url/**").authenticated()
//
//                        // Catch-all
//                        .anyRequest().a

                                .anyRequest().permitAll()
                )
                // Inject your copied filter right before Spring's default auth filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
