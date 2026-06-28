package com.agent.gateway.handler;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器：
 *  - 捕获 BusinessException，按 ErrorCode.getHttpStatus() 返回对应 HTTP 状态 + JSON body
 *  - 其他未捕获异常返回 500 INTERNAL
 *
 * <p>响应体格式：{"code":"...","message":"...","details":{...}}</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("BusinessException code={} status={} msg={}",
                errorCode.getCode(), errorCode.getHttpStatus(), ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", errorCode.getCode());
        body.put("message", ex.getMessage());
        if (!ex.getDetails().isEmpty()) {
            body.put("details", ex.getDetails());
        }

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknownException(Exception ex) {
        log.error("未捕获异常", ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ErrorCode.INTERNAL.getCode());
        body.put("message", "内部错误");

        return ResponseEntity
                .status(ErrorCode.INTERNAL.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
