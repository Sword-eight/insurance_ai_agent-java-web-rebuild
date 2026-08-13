package com.insurance.platform.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiHealthProperties.class)
public class AiHealthConfig {
    @Bean("aiHealthRestClient")
    public RestClient aiHealthRestClient(
            AiServiceProperties aiProperties,
            AiHealthProperties healthProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(healthProperties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(healthProperties.readTimeout());
        return RestClient.builder()
                .baseUrl(aiProperties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
