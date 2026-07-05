package com.agent.tool.engine.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

/**
 * Docker 客户端配置（doc 05 §5.2 SandboxBorrower）。
 *
 * <p>条件装配：{@code tool.docker.enabled=true} 时激活。测试环境关闭以避免依赖 Docker daemon。</p>
 */
@Configuration
@ConditionalOnProperty(name = "tool.docker.enabled", havingValue = "true")
public class DockerClientConfig {

    @Bean
    public DockerClient dockerClient(ToolEngineProperties properties) {
        ToolEngineProperties.Docker dockerProps = properties.getDocker();
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerProps.getHost())
                .withDockerTlsVerify(false)
                .withApiVersion(dockerProps.getApiVersion())
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerProps.getHost()))
                .maxConnections(20)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
