package com.restaurant.management.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
class TestErrorController {

    @PostMapping("/validation")
    void validate(@Valid @RequestBody TestRequest request) {
    }

    @GetMapping("/unexpected")
    void fail() {
        throw new IllegalStateException("sensitive-internal-detail");
    }

    record TestRequest(@NotBlank String value) {
    }
}
