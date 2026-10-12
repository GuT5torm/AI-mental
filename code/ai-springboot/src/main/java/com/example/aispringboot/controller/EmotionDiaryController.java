package com.example.aispringboot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.aispringboot.DTO.command.EmotionDiaryCommandDTO;
import com.example.aispringboot.DTO.command.EmotionDiaryPageQuery;
import com.example.aispringboot.DTO.response.EmotionDiaryResponseDTO;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.EmotionDiaryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪日记。
 *
 * <p>提交接口走前台（api/frontend.js 的 /emotion-diary），需要登录但不要求管理员；
 * 分页与删除走后台（api/gutts.js 的 /emotion-diary/admin/*），必须是管理员。
 *
 * <p>提交不返回分析结果——分析是异步任务，提交瞬间还没有结果。
 * 前端 emotionDiary.vue 只弹"提交成功"，后台列表稍后能看到回填的结构化数据。
 */
@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {

    @Resource
    private EmotionDiaryService emotionDiaryService;

    /**
     * 提交日记。返回新日记主键，同时入队一条 AUTO 类型的 AI 分析任务。
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody EmotionDiaryCommandDTO command) {
        return Result.ok(emotionDiaryService.createDiary(command));
    }

    @GetMapping("/admin/page")
    public Result<IPage<EmotionDiaryResponseDTO>> adminPage(@Valid EmotionDiaryPageQuery query) {
        return Result.ok(emotionDiaryService.adminPage(query));
    }

    /**
     * 删除日记，连带清理其 AI 分析任务。
     */
    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        emotionDiaryService.deleteDiary(id);
        return Result.ok();
    }
}
