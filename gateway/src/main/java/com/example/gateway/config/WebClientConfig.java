package com.example.gateway.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder, DownstreamProperties properties) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("downstream")
                .maxConnections(properties.getMaxConnections())
                .pendingAcquireMaxCount(properties.getPendingAcquireMaxCount())
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider);

        Integer connectTimeoutMillis = toMillis(properties.getConnectTimeout());
        if (connectTimeoutMillis != null) {
            httpClient = httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
        }
        Duration responseTimeout = properties.getResponseTimeout();
        if (isPositive(responseTimeout)) {
            httpClient = httpClient.responseTimeout(responseTimeout);
        }

        return builder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private Integer toMillis(Duration duration) {
        if (!isPositive(duration)) {
            return null;
        }
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
