package com.agent.memory.exception;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

/**
 * T5: 嵌入服务调用失败异常（对齐 doc 04-memory §12.4 EMBEDDING_FAILED）。
 *
 * <p>触发场景：
 * <ul>
 *   <li>HTTP 调用 agent-model-gateway /v1/embeddings 超时或网络错误</li>
 *   <li>网关返回 5xx 错误，且达到最大重试次数</li>
 *   <li>响应体无法解析（JSON 格式异常 / 维度不符）</li>
 * </ul>
 */
public class EmbeddingServiceFailureException extends BusinessException {

    public EmbeddingServiceFailureException(String message) {
        super(ErrorCode.EMBEDDING_FAILED, message);
    }

    public EmbeddingServiceFailureException(String message, Throwable cause) {
        super(ErrorCode.EMBEDDING_FAILED, message, cause);
    }
}
