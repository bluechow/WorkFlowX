package com.workflowx.auth.service;

import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.auth.dto.LoginResponse;
import com.workflowx.user.vo.UserVO;

import java.util.List;

/**
 * 认证服务（P2-08 login，P2-09 logout，P2-10 /me）。
 * 登录流程: 查用户 → PasswordService.matches（统一错误防枚举）→ 状态检查（DISABLED/LOCKED 拒绝）
 * → JwtService 签发 → Redis 会话写入（fail-closed）→ 更新 last_login_at → 返回。
 * 失败计数/锁定（P2-13）不在本服务。
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);

    /**
     * 登出（P2-09）：删除当前用户的 Redis 会话，原 Token 立即失效。
     * 仅操作当前认证用户的会话（userId 来自 SecurityContext，不接受客户端传入）；
     * 幂等：会话不存在时静默成功。
     */
    void logout(Long userId);

    /**
     * 当前用户信息（P2-10）：从数据库读取最新资料（而非直接信任 JWT claims），
     * 保证 email/nickname 等变更后立即生效；用户已被删除时抛 ResourceNotFoundException(404)。
     */
    UserVO getCurrentUser(Long userId);

    /**
     * 当前用户的实时权限编码（P3-05）：供前端按钮级 UX（纯展示层）；
     * 后端 authority 才是安全边界。数据与 Filter 权限接线同源（user→role→permission 实时查询）。
     */
    List<String> getMyPermissions(Long userId);
}
