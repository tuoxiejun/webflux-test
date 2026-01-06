package com.example.gateway;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.gateway.config.DownstreamProperties;

import io.netty.handler.timeout.ReadTimeoutException;
import reactor.core.publisher.Mono;

@RestController
public class GatewayController {

    private final WebClient webClient;
    private final DownstreamProperties downstreamProperties;

    public GatewayController(WebClient webClient, DownstreamProperties downstreamProperties) {
        this.webClient = webClient;
        this.downstreamProperties = downstreamProperties;
    }

    @PostMapping(value = "/gateway/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> process(
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> incomingBody = body == null ? Collections.emptyMap() : body;
        Map<String, Object> outgoingBody = buildOutgoingBody(incomingBody);
        String source = resolveSource(headers);

        return webClient.post()
                .uri(downstreamProperties.getPath())
                .headers(clientHeaders -> clientHeaders.set(downstreamProperties.getSourceHeader(), source))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(outgoingBody)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(this::mapError);
    }

    private Map<String, Object> buildOutgoingBody(Map<String, Object> incomingBody) {
        Map<String, Object> filtered = filterBodyByWhitelist(incomingBody, downstreamProperties.getForwardedKeys());

        Map<String, Object> merged = new LinkedHashMap<>(filtered);
        merged.putAll(downstreamProperties.getAdditionalFields());
        return merged;
    }

    private Map<String, Object> filterBodyByWhitelist(Map<String, Object> body, Set<String> whitelist) {
        if (CollectionUtils.isEmpty(whitelist)) {
            return new LinkedHashMap<>(body);
        }
        return whitelist.stream()
                .filter(body::containsKey)
                .collect(Collectors.toMap(
                        key -> key,
                        body::get,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new));
    }

    private String resolveSource(HttpHeaders headers) {
        String sourceHeader = downstreamProperties.getSourceHeader();
        String headerValue = headers.getFirst(sourceHeader);
        return headerValue != null ? headerValue : downstreamProperties.getDefaultSource();
    }

    private Mono<Map<String, Object>> mapError(Throwable throwable) {
        if (isConnectionFailure(throwable)) {
            return Mono.just(Collections.singletonMap("error", "ERR01"));
        }
        if (isDownstreamTimeout(throwable)) {
            return Mono.just(Collections.singletonMap("error", "ERR02"));
        }
        return Mono.error(throwable);
    }

    private boolean isConnectionFailure(Throwable throwable) {
        return hasCause(throwable, ConnectException.class)
                || hasCause(throwable, NoRouteToHostException.class)
                || hasCause(throwable, SocketTimeoutException.class)
                || hasCause(throwable, UnknownHostException.class);
    }

    private boolean isDownstreamTimeout(Throwable throwable) {
        return hasCause(throwable, TimeoutException.class)
                || hasCause(throwable, ReadTimeoutException.class);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
