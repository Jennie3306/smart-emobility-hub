package com.hcmut.emobility.shared.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                    "UNAUTHENTICATED", "Sign in to continue");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())   // ban demo; bat lai thi FE phai gui CSRF token
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/ws/**").permitAll()
                // Swagger: can du 3 duong dan - "/swagger-ui.html" chuyen huong sang "/swagger-ui/index.html"
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // M6-D08 (TV2 duyet 21/10): toan bo /api/m6/** chi danh cho OPERATOR
                .requestMatchers("/api/m6/**", "/api/m7/**", "/api/m8/**").hasRole("OPERATOR")
                .requestMatchers("/api/m1/dashboard/**", "/api/m1/alerts/**").hasRole("OPERATOR")
                // M5: hang doi cua hub, doi lich, doi uu tien va nhat ky chi danh cho OPERATOR.
                // Sinh vien van goi /api/m5/hubs, /sessions, /sessions/mine, /sessions/schedule (POST),
                // /sessions/{id}/start va /sessions/{id}/cancel (kiem tra chu so huu trong ChargingService).
                .requestMatchers(HttpMethod.GET, "/api/m5/hubs/*/queue", "/api/m5/sessions/*/audit").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.PUT, "/api/m5/sessions/*/schedule").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.PATCH, "/api/m5/sessions/*/priority").hasRole("OPERATOR")
                .anyRequest().authenticated())
            // Loi 401/403 do bo loc Security nem ra KHONG di qua GlobalExceptionHandler,
            // nen phai tu ghi JSON o day - neu khong Spring tra ve trang HTML "Whitelabel Error Page".
            // Chi ap dung cho /api/** de trang dang nhap /login cua trinh duyet van dung duoc khi test.
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(apiEntryPoint, new AntPathRequestMatcher("/api/**"))
                .accessDeniedHandler((req, res, e) ->
                        writeJsonError(res, HttpServletResponse.SC_FORBIDDEN,
                                "ACCESS_DENIED", req.getRequestURI().startsWith("/api/m8/")
                                    ? "What-if Studio requires an Operator account. Sign in with operator1 for the demo."
                                    : "You do not have permission to perform this action")))
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
                .failureHandler((req, res, ex) ->
                        writeJsonError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                "LOGIN_FAILED", "Invalid username or password")))
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
