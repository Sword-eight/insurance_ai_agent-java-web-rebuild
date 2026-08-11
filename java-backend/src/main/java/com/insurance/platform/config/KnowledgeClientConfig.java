package com.insurance.platform.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KnowledgeClientProperties.class)
public class KnowledgeClientConfig {
    @Bean("knowledgeRestClient")
    public RestClient knowledgeRestClient(
            AiServiceProperties aiProperties,
            KnowledgeClientProperties knowledgeProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(aiProperties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(knowledgeProperties.readTimeout());
        return RestClient.builder()
                .baseUrl(aiProperties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
