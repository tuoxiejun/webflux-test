package com.example.gateway;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

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
    public Mono<ResponseEntity<Map<String, Object>>> process(
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> incoming = body == null ? Collections.emptyMap() : body;
        Map<String, Object> requestHead = extractSection(incoming, "head");
        Map<String, Object> outgoingBody = buildOutgoingBody(incoming);
        String source = resolveSource(headers);

        Mono<ResponseEntity<Map<String, Object>>> responseMono = webClient.post()
                .uri(downstreamProperties.getPath())
                .headers(clientHeaders -> clientHeaders.set(downstreamProperties.getSourceHeader(), source))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(outgoingBody)
                .retrieve()
                .bodyToMono(mapTypeReference())
                .map(downstreamResponse -> buildResponse(headers, requestHead, downstreamResponse, null))
                .onErrorResume(error -> Mono.just(buildResponse(headers, requestHead, Collections.emptyMap(), error)));
        return responseMono;
    }

    private Map<String, Object> extractSection(Map<String, Object> incoming, String key) {
        Object section = incoming.get(key);
        if (section instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) section);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> buildOutgoingBody(Map<String, Object> incoming) {
        Map<String, Object> incomingHead = extractSection(incoming, "head");
        Map<String, Object> incomingPayload = extractSection(incoming, "body");

        Map<String, Object> filtered = filterBodyByWhitelist(incomingPayload, downstreamProperties.getForwardedKeys());

        Map<String, Object> merged = new LinkedHashMap<>(filtered);
        merged.putAll(downstreamProperties.getAdditionalFields());

        Map<String, Object> outgoing = new LinkedHashMap<>();
        outgoing.put("head", incomingHead);
        outgoing.put("body", merged);
        return outgoing;
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

    private ResponseEntity<Map<String, Object>> buildResponse(HttpHeaders requestHeaders,
                                                              Map<String, Object> requestHead,
                                                              Map<String, Object> downstreamResponse,
                                                              Throwable error) {
        Map<String, Object> head = buildResponseHead(requestHead);
        Map<String, Object> errorSection = buildErrorSection(downstreamResponse, error);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("head", head);
        responseBody.put("error", errorSection);

        return ResponseEntity
                .ok()
                .headers(copyHeaders(requestHeaders))
                .body(responseBody);
    }

    private Map<String, Object> buildResponseHead(Map<String, Object> requestHead) {
        Map<String, Object> head = new LinkedHashMap<>(requestHead);
        head.put("responseDate", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        head.put("responseTime", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        return head;
    }

    private Map<String, Object> buildErrorSection(Map<String, Object> downstreamResponse, Throwable error) {
        if (error != null) {
            return buildGatewayError(error);
        }
        Map<String, Object> downstreamError = extractError(downstreamResponse);
        if (downstreamError.containsKey("errCode")) {
            return downstreamError;
        }
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("errCode", "AAAAAA");
        success.put("errMessage", "success");
        return success;
    }

    private Map<String, Object> buildGatewayError(Throwable error) {
        Map<String, Object> err = new LinkedHashMap<>();
        if (isConnectionFailure(error)) {
            err.put("errCode", "ERR01");
            err.put("errMessage", "downstream connection failed");
            return err;
        }
        if (isDownstreamTimeout(error)) {
            err.put("errCode", "ERR02");
            err.put("errMessage", "downstream response timeout");
            return err;
        }
        err.put("errCode", "ERR02");
        err.put("errMessage", error.getMessage() == null ? "downstream error" : error.getMessage());
        return err;
    }

    private Map<String, Object> extractError(Map<String, Object> downstreamResponse) {
        if (downstreamResponse == null) {
            return Collections.emptyMap();
        }
        Object nestedError = downstreamResponse.get("error");
        if (nestedError instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) nestedError);
        }
        Map<String, Object> err = new LinkedHashMap<>();
        if (downstreamResponse.containsKey("errCode")) {
            err.put("errCode", downstreamResponse.get("errCode"));
            err.put("errMessage", downstreamResponse.getOrDefault("errMessage", ""));
        }
        return err;
    }

    private HttpHeaders copyHeaders(HttpHeaders headers) {
        HttpHeaders responseHeaders = new HttpHeaders();
        headers.forEach((key, values) -> responseHeaders.put(key, new ArrayList<>(values)));
        return responseHeaders;
    }

    private ParameterizedTypeReference<Map<String, Object>> mapTypeReference() {
        return new ParameterizedTypeReference<Map<String, Object>>() {};
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
