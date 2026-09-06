package com.workflowx.common.security;

import java.util.Collection;

/**
 * JWT 服务（P2-06）：签发 + 解析 + 密码学验证的唯一入口。
 * 算法服务端固定 HS256，客户端传入的任何算法声明不参与验证（防 alg=none/算法混淆）。
 * 不依赖 Controller，不做数据库/Redis 访问（会话校验属 P2-07）。
 */
public interface JwtService {

    /**
     * 生成 Access Token。
     *
     * @param userId   稳定用户 ID（写入 sub）
     * @param username 用户名（非敏感）
     * @param roles    角色编码列表（与后续 Security 角色体系一致）
     * @return 签名后的 JWT（含 jti/iat/exp，exp = now + expireHours）
     */
    String generateToken(Long userId, String username, Collection<String> roles);

    /**
     * 解析并验证 Token（签名、过期、签发方、必要 claims 全部校验）。
     *
     * @return 已验证载荷
     * @throws io.jsonwebtoken.JwtException 签名/过期/格式/算法/必要 claim 任一失败
     * @throws IllegalArgumentException     缺失必要 claim
     */
    JwtPayload parseToken(String token);

    /**
     * 验证 Token 是否有效（等价于 parseToken 成功），失败返回 false 而非抛异常。
     */
    boolean validateToken(String token);
}
