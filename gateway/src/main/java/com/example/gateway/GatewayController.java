package com.example.gateway;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.gateway.config.DownstreamProperties;

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
                .bodyToMono(Map.class);
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
}
