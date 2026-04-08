package com.macs.qa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "QA Tool", description = "QA testing endpoints")
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "Hello", description = "Simple hello response for QA testing")
    public Mono<String> hello() {
        return Mono.just("hello");
    }
}
