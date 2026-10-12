package com.example.aispringboot.controller;

import com.example.aispringboot.common.Anonymous;
import com.example.aispringboot.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Anonymous
public class TestController {
    @GetMapping("/test")
    public Result<String> test() {
        return Result.ok("hello world");
    }
}
