package it.unisa.ddditserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Rotte
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/csrf", "/auth/signup", "/auth/login", "/auth/logout").permitAll()
                        .anyRequest().authenticated()
                )

                // CSRF attivo + token in cookie leggibile dal frontend
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                );

        return http.build();
    }
}