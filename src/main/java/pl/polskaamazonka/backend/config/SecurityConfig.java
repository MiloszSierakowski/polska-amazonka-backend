package pl.polskaamazonka.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pl.polskaamazonka.backend.dto.ApiErrorResponse;
import pl.polskaamazonka.backend.security.CustomUserDetailsService;
import pl.polskaamazonka.backend.security.JwtAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] ADMIN_ONLY_PATHS = {
            "/api/admin/users",
            "/api/admin/users/**",
            "/api/admin/changelogs",
            "/api/admin/changelogs/**"
    };

    private static final String[] PANEL_MUTATION_PATHS = {
            "/api/videos/**",
            "/api/categories/**",
            "/api/shops/**",
            "/api/products/**",
            "/api/users/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiErrorResponse body = new ApiErrorResponse();
                            body.setStatus(HttpStatus.FORBIDDEN.value());
                            body.setError(HttpStatus.FORBIDDEN.getReasonPhrase());
                            body.setMessage("Brak uprawnień do wykonania tej operacji.");
                            body.setPath(request.getRequestURI());
                            objectMapper.writeValue(response.getWriter(), body);
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/click-stats").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers(ADMIN_ONLY_PATHS).hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers(HttpMethod.POST, PANEL_MUTATION_PATHS).hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers(HttpMethod.PUT, PANEL_MUTATION_PATHS).hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers(HttpMethod.DELETE, PANEL_MUTATION_PATHS).hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers(HttpMethod.PATCH, PANEL_MUTATION_PATHS).hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers(HttpMethod.POST, "/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
