package com.insurance.platform.client;

import com.insurance.platform.client.dto.KnowledgeIndexMetadata;
import com.insurance.platform.client.dto.KnowledgeIndexResponse;
import java.util.Map;
import org.springframework.core.io.Resource;

/**
 * Java → Python Knowledge 端口。Phase 10 才完成文件与业务状态编排。
 */
public interface KnowledgeClient {

    KnowledgeIndexResponse indexDocument(
            KnowledgeIndexMetadata metadata,
            Resource content,
            String traceId);

    void rebuild(String traceId);

    Map<String, Object> status(String traceId);
}
