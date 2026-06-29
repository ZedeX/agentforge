package com.agent.gateway.fixture;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P7-5 (COV-04) HTTP E2E 测试 fixture：
 *
 * <p>提供一个 {@code /api/e2e/errors/{ERROR_CODE}} 端点，根据 path 变量解析出 {@link ErrorCode}
 * 后抛出对应 {@link BusinessException}。配合 {@link com.agent.gateway.handler.GlobalExceptionHandler}
 * 可端到端验证「ErrorCode → HTTP 状态码 + JSON body」的触发路径。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>路径变量直接用 {@link ErrorCode} 枚举名（如 {@code UNAUTHENTICATED}），通过 {@link ErrorCode#valueOf}
 *       解析；非法名字将抛 {@link IllegalArgumentException}，由 GlobalExceptionHandler 的兜底分支
 *       翻译为 500 INTERNAL。</li>
 *   <li>构造异常时显式传入 {@code "e2e:" + ec.getCode()} 作为消息，便于断言 message 字段不被
 *       默认 defaultMessage 干扰。</li>
 *   <li>支持 {@code ?details=key:value} 查询参数，触发带 details 的构造器以覆盖 details 序列化路径。</li>
 * </ul>
 *
 * <p>该 fixture 仅在测试 classpath 中可见，不进入产品代码，不影响生产路由。</p>
 */
@RestController
@RequestMapping("/api/e2e/errors")
public class ErrorCodeE2EFixtureController {

    /**
     * 触发指定错误码的 BusinessException。
     *
     * @param errorCode ErrorCode 枚举名（如 UNAUTHENTICATED）
     */
    @GetMapping("/{errorCode}")
    public Map<String, Object> trigger(@PathVariable String errorCode) {
        ErrorCode ec = ErrorCode.valueOf(errorCode);
        throw new BusinessException(ec, "e2e:" + ec.getCode());
    }

    /**
     * 触发指定错误码的 BusinessException，并携带结构化 details。
     *
     * @param errorCode ErrorCode 枚举名
     */
    @GetMapping("/{errorCode}/withDetails")
    public Map<String, Object> triggerWithDetails(@PathVariable String errorCode) {
        ErrorCode ec = ErrorCode.valueOf(errorCode);
        throw new BusinessException(ec, "e2e:" + ec.getCode(), Map.of("field", "goal", "reason", "test"));
    }

    /**
     * 触发非 BusinessException 的未知异常，验证 GlobalExceptionHandler 的兜底分支。
     */
    @GetMapping("/_unknown")
    public Map<String, Object> triggerUnknown() {
        throw new IllegalStateException("unexpected internal state");
    }
}
