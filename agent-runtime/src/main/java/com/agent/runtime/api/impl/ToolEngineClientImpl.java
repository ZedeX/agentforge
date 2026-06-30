package com.agent.runtime.api.impl;

import com.agent.runtime.api.ToolEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具引擎客户端默认实现 (F6 act phase).
 *
 * <p>简单实现: 返回 mock 工具执行结果, 真实实现应路由到 agent-tool-engine 模块。
 */
@Component
public class ToolEngineClientImpl implements ToolEngineClient {

    private static final Logger log = LoggerFactory.getLogger(ToolEngineClientImpl.class);

    @Override
    public String invoke(String toolId, String args) {
        log.info("调用工具引擎: toolId={}, args={}", toolId, args);
        return "{\"tool\":\"" + toolId + "\",\"status\":\"success\",\"result\":\"mock-result\"}";
    }
}