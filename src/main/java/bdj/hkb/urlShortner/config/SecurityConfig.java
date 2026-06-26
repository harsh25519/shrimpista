package bdj.hkb.urlShortner.config;

import bdj.hkb.urlShortner.security.JwtFilter;
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
                .authorizeHttpRequests(auth -> auth
                        // PUBLIC ROUTES (No token required)
                        .requestMatchers("/auth/**").permitAll() // Login/Register endpoints
                        .requestMatchers(HttpMethod.GET,"/urls").authenticated()
                        .requestMatchers("/urls/**").permitAll() // Creating a URL can be anonymous (based on your earlier code)

                        // PROTECTED ROUTES (Token required)
                        .requestMatchers("/stats/**").authenticated()
                        .requestMatchers("/clicks/**").authenticated()

                                .anyRequest().permitAll()
                )
                // Inject your copied filter right before Spring's default auth filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
