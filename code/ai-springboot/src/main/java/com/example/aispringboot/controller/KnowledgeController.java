package com.example.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aispringboot.DTO.command.ArticleCommandDTO;
import com.example.aispringboot.DTO.command.ArticlePageQuery;
import com.example.aispringboot.DTO.command.ArticleStatusCommandDTO;
import com.example.aispringboot.DTO.response.ArticleResponseDTO;
import com.example.aispringboot.DTO.response.CategoryResponseDTO;
import com.example.aispringboot.common.Anonymous;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.KnowledgeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接口。
 *
 * <p>列表与详情标了 {@code @Anonymous}，因为前台知识库页是免登录访问的；
 * 但拦截器在匿名接口上仍会解析 token，管理员带 token 访问时能看到全部状态，
 * 普通访客只看到已发布文章。写操作不标注解，必须带有效 token，
 * 且服务层还会校验 userType=2。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgeService knowledgeService;

    @Anonymous
    @GetMapping("/category/tree")
    public Result<List<CategoryResponseDTO>> categoryTree() {
        return Result.ok(knowledgeService.categoryTree());
    }

    @Anonymous
    @GetMapping("/article/page")
    public Result<Page<ArticleResponseDTO>> articlePage(@Valid ArticlePageQuery query) {
        return Result.ok(knowledgeService.articlePage(query));
    }

    @Anonymous
    @GetMapping("/article/{id}")
    public Result<ArticleResponseDTO> articleDetail(@PathVariable("id") String id) {
        return Result.ok(knowledgeService.articleDetail(id));
    }

    @PostMapping("/article")
    public Result<ArticleResponseDTO> createArticle(@Valid @RequestBody ArticleCommandDTO command) {
        return Result.ok(knowledgeService.createArticle(command));
    }

    @PutMapping("/article/{id}")
    public Result<ArticleResponseDTO> updateArticle(@PathVariable("id") String id,
                                                    @Valid @RequestBody ArticleCommandDTO command) {
        return Result.ok(knowledgeService.updateArticle(id, command));
    }

    @PutMapping("/article/{id}/status")
    public Result<ArticleResponseDTO> changeStatus(@PathVariable("id") String id,
                                                   @Valid @RequestBody ArticleStatusCommandDTO command) {
        return Result.ok(knowledgeService.changeStatus(id, command));
    }

    @DeleteMapping("/article/{id}")
    public Result<Void> deleteArticle(@PathVariable("id") String id) {
        knowledgeService.deleteArticle(id);
        return Result.ok(null);
    }
}
