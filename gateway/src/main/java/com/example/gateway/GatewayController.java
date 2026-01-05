package com.example.gateway;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@RestController
public class GatewayController {

    private final WebClient webClient = WebClient.create("http://localhost:8081");

    @PostMapping(value = "/gateway/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> process(
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> newBody = body == null
                ? new HashMap<String, Object>()
                : new HashMap<String, Object>(body);

        newBody.remove("aaa");
        newBody.put("bbb", "BBB");

        return webClient.post()
                .uri("/echo")
                .header("from", "aaa")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(newBody)
                .retrieve()
                .bodyToMono(String.class);
    }
}
