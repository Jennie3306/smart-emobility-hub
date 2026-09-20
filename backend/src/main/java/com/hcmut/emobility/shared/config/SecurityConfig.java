package com.hcmut.emobility.shared.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Xac thuc bang session cookie (JSESSIONID) - khong dung JWT.
 * Hai vai tro: ROLE_STUDENT, ROLE_OPERATOR.
 */
@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    /** Chua dang nhap ma goi API -> tra JSON 401 (frontend tu chuyen sang trang dang nhap). */
    private final AuthenticationEntryPoint apiEntryPoint = (req, res, e) ->
            writeJsonError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Ban chua dang nhap");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())   // ban demo; bat lai thi FE phai gui CSRF token
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/ws/**").permitAll()
                // Swagger: can du 3 duong dan - "/swagger-ui.html" chuyen huong sang "/swagger-ui/index.html"
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/m7/**", "/api/m8/**").hasRole("OPERATOR")
                .requestMatchers("/api/m1/dashboard/**", "/api/m6/incidents/**").hasRole("OPERATOR")
                .anyRequest().authenticated())
            // Loi 401/403 do bo loc Security nem ra KHONG di qua GlobalExceptionHandler,
            // nen phai tu ghi JSON o day - neu khong Spring tra ve trang HTML "Whitelabel Error Page".
            // Chi ap dung cho /api/** de trang dang nhap /login cua trinh duyet van dung duoc khi test.
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(apiEntryPoint, new AntPathRequestMatcher("/api/**"))
                .accessDeniedHandler((req, res, e) ->
                        writeJsonError(res, HttpServletResponse.SC_FORBIDDEN,
                                "ACCESS_DENIED", "Ban khong co quyen thuc hien thao tac nay")))
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
                .failureHandler((req, res, ex) ->
                        writeJsonError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                "LOGIN_FAILED", "Sai tai khoan hoac mat khau")))
            .logout(out -> out
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK)));
        return http.build();
    }

    /** Ghi loi theo dung dang JSON chung cua he thong: { code, message, details, timestamp }. */
    private static void writeJsonError(HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(
                "{\"code\":\"" + code + "\","
                + "\"message\":\"" + message + "\","
                + "\"details\":{},"
                + "\"timestamp\":\"" + OffsetDateTime.now() + "\"}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigin));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);   // bat buoc de trinh duyet gui kem JSESSIONID
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}