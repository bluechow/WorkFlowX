package com.workflowx.user.service;

/**
 * 统一密码服务（P2-04）：全项目唯一的密码哈希/校验入口。
 * 密码格式规则（≥8 位、含字母和数字）属于 DTO Bean Validation 职责，本服务不做规则校验。
 * 禁止在任何其他位置直接实例化 BCryptPasswordEncoder 或实现等价哈希逻辑（Master Prompt §29）。
 */
public interface PasswordService {

    /**
     * 哈希原始密码（BCrypt, strength 10）。
     *
     * @param rawPassword 原始密码（调用后不得以明文形式存储/记日志）
     * @return BCrypt 哈希串
     */
    String encode(String rawPassword);

    /**
     * 校验原始密码与哈希是否匹配。
     */
    boolean matches(String rawPassword, String encodedPassword);
}
