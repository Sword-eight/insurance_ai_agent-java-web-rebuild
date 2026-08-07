package com.insurance.platform.client;

import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;

/**
 * Java → Python Agent 端口。HTTP、超时与内部契约转换由 Phase 6 适配器实现。
 */
public interface AgentClient {

    AgentChatResponse chat(AgentChatRequest request, String traceId);
}
