package com.hcmut.emobility.shared.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tao tai khoan demo khi bang app_user con rong.
 * Mat khau duoc ma hoa bang PasswordEncoder ngay luc chay, nen khong co chuoi hash
 * nao bi dat cung trong ma nguon hay file SQL.
 *
 * Tat bang: app.demo-users.enabled=false (nen tat khi trien khai that).
 */
@Configuration
@ConditionalOnProperty(name = "app.demo-users.enabled", havingValue = "true", matchIfMissing = true)
public class DemoUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);

    @Bean
    ApplicationRunner createDemoUsers(AppUserRepository repository, PasswordEncoder encoder) {
        return args -> {
            if (repository.count() > 0) return;
            repository.save(new AppUser("student1",  encoder.encode("123456"), "Sinh vien Demo", UserRole.STUDENT));
            repository.save(new AppUser("operator1", encoder.encode("123456"), "Van hanh Demo",  UserRole.OPERATOR));
            log.info("Da tao 2 tai khoan demo: student1 / operator1 (mat khau 123456)");
        };
    }
}
