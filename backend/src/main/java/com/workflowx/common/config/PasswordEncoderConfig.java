package com.workflowx.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器统一配置（P2-04）。
 * 全项目唯一的 PasswordEncoder Bean（strength=10，ADR/任务基线）；
 * 任何需要密码哈希/校验的代码一律注入 PasswordService 或本 Bean，禁止自行 new BCryptPasswordEncoder。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
