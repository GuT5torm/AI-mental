package com.example.aispringboot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.aispringboot.DTO.command.EmotionDiaryPageQuery;
import com.example.aispringboot.DTO.response.EmotionDiaryResponseDTO;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.EmotionDiaryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 情绪日记。
 *
 * <p>情绪记录的唯一录入途径是 AI 咨询对话（命中关键词自动记录，来源 CHAT），
 * 这里只提供查询与后台管理接口：/my 供前台"我的心情"页查看自己的记录；
 * 分页与删除走后台（api/gutts.js 的 /emotion-diary/admin/*），必须是管理员。
 */
@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {

    @Resource
    private EmotionDiaryService emotionDiaryService;

    /**
     * 当前用户自己的情绪记录列表——情绪日记页作为"观看自己心情"页面的数据接口。
     */
    @GetMapping("/my")
    public Result<List<EmotionDiaryResponseDTO>> my() {
        return Result.ok(emotionDiaryService.myDiaries());
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
