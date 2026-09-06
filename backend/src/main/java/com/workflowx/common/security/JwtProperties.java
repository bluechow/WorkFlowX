package com.workflowx.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性（P2-06）。
 * secret 来源优先级: 环境变量 JWT_SECRET > 配置文件；dev 默认值仅为本地开发用途，
 * 生产环境由 application-prod.yml 强制要求显式提供（缺失/为空则启动失败）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（HS256 要求 ≥32 字节）；禁止硬编码生产 secret */
    private String secret;

    /** Access Token 有效期（小时），基线 2 小时，无 Refresh Token */
    private int expireHours = 2;

    /** 签发方标识，解析时强制校验 */
    private String issuer = "workflowx";
}
