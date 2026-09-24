package com.workflowx.common.web;

import com.workflowx.common.exception.BaseException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理（Master Prompt §10）：
 * - 统一响应结构 + 真实 HTTP 状态码
 * - 服务端记录完整日志，客户端只见安全信息，禁止暴露堆栈 / 数据库异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Result<Void>> handleBaseException(BaseException ex) {
        log.warn("business exception: status={}, message={}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(Result.of(ex.getStatus(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("validation failed: {}", detail);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Result.of(422, "validation failed: " + detail, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("constraint violation: {}", detail);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Result.of(422, "validation failed: " + detail, null));
    }

    /** 未匹配路径（Spring Boot 3.2+ 静态资源链路抛出）→ 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.of(404, "resource not found", null));
    }

    /** 请求体不可读（JSON 格式错误 / 非法枚举值如 status:"FOO"）→ 422，不泄漏反序列化细节 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Result.of(422, "request body is invalid", null));
    }

    /** 查询/绑定参数校验失败（如 page=0、size=101）→ 422（@ModelAttribute @Valid） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("parameter binding failed: {}", detail);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Result.of(422, "invalid request parameters: " + detail, null));
    }

    /** 路径/查询参数类型错误（如 /users/{id} 传入非数字）→ 400 请求格式错误 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("argument type mismatch: name={}", ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.of(400, "invalid request parameter", null));
    }

    /**
     * @PreAuthorize 拒绝（方法级授权）：必须重抛给 Security 的 ExceptionTranslationFilter，
     * 由 RestAccessDeniedHandler 输出 403；若被本兜底拦截会错误地变成 500。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    /** multipart 缺失必需 part（Phase 15 安全测试发现：空 filename 的 part 被 Spring 视为缺失）→ 422。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("missing multipart part: {}", ex.getRequestPartName());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Result.of(422, "缺少必需的请求部分: " + ex.getRequestPartName(), null));
    }

    /** multipart 超过 Spring 层大小限制（Phase 8; ADR-018）：业务上限由 AttachmentFilePolicy 校验，此处兜底转 413。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.of(413, "附件超过大小限制", null));
    }

    /** 兜底：完整堆栈只进日志，对外隐藏一切内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.of(500, "internal server error", null));
    }
}
