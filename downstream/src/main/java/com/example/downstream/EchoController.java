package com.example.downstream;

import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class EchoController {

    @PostMapping("/echo")
    public Mono<Map<String, Object>> echo(
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("fromHeader", headers.get("from"));
        resp.put("body", body);
        System.out.println("11111");
        return Mono.just(resp);
    }
}
