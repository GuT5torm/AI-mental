package com.example.aispringboot.controller;

import com.example.aispringboot.DTO.response.AnalyticsOverviewDTO;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.AnalyticsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分析看板。
 *
 * <p>前端 {@code getAnalyticsOverview()} 不带参数，走 Service 里的全量区间分支；
 * days 留着是给运维按需缩短区间用，不传时不会走到。
 */
@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalyticsController {

    @Resource
    private AnalyticsService analyticsService;

    @GetMapping("/overview")
    public Result<AnalyticsOverviewDTO> overview(@RequestParam(required = false) Integer days) {
        return Result.ok(analyticsService.overview(days));
    }
}
