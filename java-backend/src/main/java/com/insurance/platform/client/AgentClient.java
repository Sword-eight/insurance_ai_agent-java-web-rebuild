package com.insurance.platform.client;

import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;

/**
 * Java → Python Agent 端口。Phase 6 才提供真实 HTTP 适配器。
 */
public interface AgentClient {

    AgentChatResponse chat(AgentChatRequest request, String traceId);
}
