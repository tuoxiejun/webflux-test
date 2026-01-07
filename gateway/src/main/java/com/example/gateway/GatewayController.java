package com.example.gateway;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.gateway.config.DownstreamProperties;
import com.example.gateway.model.GatewayRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.timeout.ReadTimeoutException;
import reactor.core.publisher.Mono;

@RestController
@Validated
public class GatewayController {
    private static final Set<String> PASS_THROUGH_HEADER_NAMES = new LinkedHashSet<>(Arrays.asList("traceid", "userid"));

    private final WebClient webClient;
    private final DownstreamProperties downstreamProperties;
    private final ObjectMapper objectMapper;

    public GatewayController(WebClient webClient, DownstreamProperties downstreamProperties, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.downstreamProperties = downstreamProperties;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/gateway/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> process(
            @RequestHeader("head1") @NotBlank @Size(max = 8) String head1,
            @RequestHeader("head2") @NotBlank @Size(max = 8) String head2,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody GatewayRequest body) {

        Map<String, Object> requestHead = convertSection(body.getHead());
        Map<String, Object> outgoingBody = buildOutgoingBody(body);
        String source = resolveSource(headers);
        MediaType downstreamMediaType = downstreamJsonMediaType();

        byte[] requestPayload;
        try {
            requestPayload = encodeRequestBody(outgoingBody);
        } catch (JsonProcessingException e) {
            return Mono.just(buildResponse(headers, requestHead, Collections.emptyMap(), e));
        }

        Mono<ResponseEntity<Map<String, Object>>> responseMono = webClient.post()
                .uri(downstreamProperties.getPath())
                .headers(clientHeaders -> {
                    clientHeaders.set(downstreamProperties.getSourceHeader(), source);
                    clientHeaders.setContentType(downstreamMediaType);
                    clientHeaders.setAccept(Collections.singletonList(downstreamMediaType));
                })
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(this::decodeDownstreamResponse)
                .map(downstreamResponse -> buildResponse(headers, requestHead, downstreamResponse, null))
                .onErrorResume(error -> Mono.just(buildResponse(headers, requestHead, Collections.emptyMap(), error)));
        return responseMono;
    }

    private Map<String, Object> convertSection(Object source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> mapped = objectMapper.convertValue(source, new TypeReference<Map<String, Object>>() {});
        return mapped == null ? new LinkedHashMap<>() : new LinkedHashMap<>(mapped);
    }

    private Map<String, Object> buildOutgoingBody(GatewayRequest incoming) {
        Map<String, Object> incomingHead = convertSection(incoming.getHead());
        Map<String, Object> incomingPayload = convertSection(incoming.getBody());

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
        HttpHeaders responseHeaders = HttpHeaders.writableHttpHeaders(new HttpHeaders());
        headers.forEach((key, values) -> values.forEach(value -> responseHeaders.add(key, value)));

        PASS_THROUGH_HEADER_NAMES.forEach(name -> {
            if (headers.containsKey(name)) {
                headers.get(name).forEach(value -> responseHeaders.add(name, value));
            }
        });
        return responseHeaders;
    }

    private MediaType downstreamJsonMediaType() {
        Charset charset = downstreamProperties.getCharset();
        return new MediaType(MediaType.APPLICATION_JSON, charset);
    }

    private byte[] encodeRequestBody(Map<String, Object> outgoingBody) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(outgoingBody);
        return json.getBytes(downstreamProperties.getCharset());
    }

    private Map<String, Object> decodeDownstreamResponse(byte[] downstreamBody) {
        if (downstreamBody == null || downstreamBody.length == 0) {
            return Collections.emptyMap();
        }
        try {
            String json = new String(downstreamBody, downstreamProperties.getCharset());
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode downstream response", e);
        }
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
