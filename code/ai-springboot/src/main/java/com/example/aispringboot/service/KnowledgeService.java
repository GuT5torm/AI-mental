package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aispringboot.DTO.command.ArticleCommandDTO;
import com.example.aispringboot.DTO.command.ArticlePageQuery;
import com.example.aispringboot.DTO.command.ArticleStatusCommandDTO;
import com.example.aispringboot.DTO.response.ArticleResponseDTO;
import com.example.aispringboot.DTO.response.CategoryResponseDTO;
import com.example.aispringboot.common.AuthUtil;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.common.UserContext;
import com.example.aispringboot.entity.KnowledgeArticle;
import com.example.aispringboot.entity.KnowledgeCategory;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.mapper.KnowledgeArticleMapper;
import com.example.aispringboot.mapper.KnowledgeCategoryMapper;
import com.example.aispringboot.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库：分类与文章。
 *
 * <p>后台与前台共用 {@code GET /api/knowledge/article/page} 这一个接口，用调用者身份区分：
 * 管理员（userType=2）看到全部状态，未登录或普通用户只能看到已发布（status=1）。
 */
@Service
public class KnowledgeService {

    /** 草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 已发布 */
    private static final int STATUS_PUBLISHED = 1;
    /** 已下线 */
    private static final int STATUS_OFFLINE = 2;

    @Resource
    private KnowledgeCategoryMapper categoryMapper;
    @Resource
    private KnowledgeArticleMapper articleMapper;
    @Resource
    private UserMapper userMapper;

    /**
     * 分类列表。接口名沿用前端的 tree，但前端是按扁平数组消费的，故返回扁平列表。
     */
    public List<CategoryResponseDTO> categoryTree() {
        List<KnowledgeCategory> list = categoryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeCategory>()
                        .eq(KnowledgeCategory::getStatus, 1)
                        .orderByAsc(KnowledgeCategory::getSortOrder)
                        .orderByAsc(KnowledgeCategory::getId));
        return list.stream().map(c -> {
            CategoryResponseDTO dto = new CategoryResponseDTO();
            dto.setId(c.getId());
            dto.setParentId(c.getParentId());
            dto.setCategoryName(c.getCategoryName());
            dto.setCategoryCode(c.getCategoryCode());
            dto.setDescription(c.getDescription());
            dto.setSortOrder(c.getSortOrder());
            dto.setStatus(c.getStatus());
            return dto;
        }).collect(Collectors.toList());
    }

    public Page<ArticleResponseDTO> articlePage(ArticlePageQuery query) {
        boolean admin = isAdmin();

        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        if (admin) {
            if (query.getStatus() != null) {
                wrapper.eq(KnowledgeArticle::getStatus, query.getStatus());
            }
        } else {
            // 前台只看已发布
            wrapper.eq(KnowledgeArticle::getStatus, STATUS_PUBLISHED);
        }
        if (query.getTitle() != null && !query.getTitle().isBlank()) {
            wrapper.like(KnowledgeArticle::getTitle, query.getTitle().trim());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(KnowledgeArticle::getCategoryId, query.getCategoryId());
        }

        Page<KnowledgeArticle> page = new Page<>(query.getCurrent(), query.getSizeValue());
        String sortColumn = query.resolveSortColumn();
        if (sortColumn != null) {
            page.addOrder(query.isAsc() ? OrderItem.asc(sortColumn) : OrderItem.desc(sortColumn));
        } else {
            page.addOrder(OrderItem.desc("created_at"));
        }

        Page<KnowledgeArticle> result = articleMapper.selectPage(page, wrapper);
        Map<Long, String> categoryNames = loadCategoryNames();
        Map<Long, String> authorNames = loadAuthorNames(result.getRecords());

        Page<ArticleResponseDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        dtoPage.setRecords(result.getRecords().stream()
                .map(a -> toDto(a, categoryNames, authorNames, false))
                .collect(Collectors.toList()));
        return dtoPage;
    }

    /**
     * 文章详情。前台访问时同时自增阅读量；后台编辑读取时不自增。
     */
    public ArticleResponseDTO articleDetail(String id) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.ARTICLE_NOT_EXIST.getCode(), "文章不存在");
        }
        if (!isAdmin() && !Integer.valueOf(STATUS_PUBLISHED).equals(article.getStatus())) {
            throw new BusinessException(ResultCode.ARTICLE_NOT_EXIST.getCode(), "文章不存在或未发布");
        }

        if (!isAdmin()) {
            // 用 SQL 自增而不是先读后写，避免并发下丢更新
            articleMapper.update(null, new LambdaUpdateWrapper<KnowledgeArticle>()
                    .eq(KnowledgeArticle::getId, id)
                    .setSql("read_count = coalesce(read_count, 0) + 1"));
            KnowledgeArticle refreshed = articleMapper.selectById(id);
            if (refreshed != null) {
                article = refreshed;
            }
        }

        Map<Long, String> categoryNames = loadCategoryNames();
        Map<Long, String> authorNames = loadAuthorNames(Collections.singletonList(article));
        return toDto(article, categoryNames, authorNames, true);
    }

    /**
     * 新增文章。id 可来自前端（上传封面时生成的 UUID），为空时后端生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleResponseDTO createArticle(ArticleCommandDTO command) {
        AuthUtil.requireAdmin();
        KnowledgeArticle article = new KnowledgeArticle();
        String id = command.getId() == null || command.getId().isBlank()
                ? UUID.randomUUID().toString() : command.getId().trim();
        if (articleMapper.selectById(id) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "文章ID已存在");
        }
        article.setId(id);
        article.setTitle(command.getTitle().trim());
        article.setContent(command.getContent());
        article.setCategoryId(command.getCategoryId());
        article.setCoverImage(command.getCoverImage());
        article.setSummary(command.getSummary());
        article.setTags(command.getTags());
        article.setAuthorId(UserContext.getUserId());
        article.setReadCount(0);
        int status = command.getStatus() == null ? STATUS_DRAFT : command.getStatus();
        article.setStatus(status);
        if (status == STATUS_PUBLISHED) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.insert(article);
        return articleDetail(article.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ArticleResponseDTO updateArticle(String id, ArticleCommandDTO command) {
        AuthUtil.requireAdmin();
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.ARTICLE_NOT_EXIST.getCode(), "文章不存在");
        }
        article.setTitle(command.getTitle() == null ? article.getTitle() : command.getTitle().trim());
        article.setContent(command.getContent() == null ? article.getContent() : command.getContent());
        article.setCategoryId(command.getCategoryId() == null ? article.getCategoryId() : command.getCategoryId());
        article.setCoverImage(command.getCoverImage());
        article.setSummary(command.getSummary());
        article.setTags(command.getTags());
        if (command.getStatus() != null) {
            article.setStatus(command.getStatus());
        }
        if (Integer.valueOf(STATUS_PUBLISHED).equals(article.getStatus()) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
        return articleDetail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArticleResponseDTO changeStatus(String id, ArticleStatusCommandDTO command) {
        AuthUtil.requireAdmin();
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.ARTICLE_NOT_EXIST.getCode(), "文章不存在");
        }
        int status = command.getStatus();
        if (status != STATUS_DRAFT && status != STATUS_PUBLISHED && status != STATUS_OFFLINE) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "状态取值非法");
        }
        article.setStatus(status);
        if (status == STATUS_PUBLISHED && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
        return articleDetail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(String id) {
        AuthUtil.requireAdmin();
        if (articleMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.ARTICLE_NOT_EXIST.getCode(), "文章不存在");
        }
        articleMapper.deleteById(id);
    }

    private static boolean isAdmin() {
        return AuthUtil.isAdmin();
    }

    private Map<Long, String> loadCategoryNames() {
        List<KnowledgeCategory> categories = categoryMapper.selectList(null);
        return categories.stream()
                .collect(Collectors.toMap(KnowledgeCategory::getId, KnowledgeCategory::getCategoryName,
                        (a, b) -> a));
    }

    private Map<Long, String> loadAuthorNames(List<KnowledgeArticle> articles) {
        Set<Long> ids = articles.stream()
                .map(KnowledgeArticle::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        List<User> users = userMapper.selectBatchIds(ids);
        for (User u : users) {
            String display = u.getNickname() != null && !u.getNickname().isBlank()
                    ? u.getNickname() : u.getUsername();
            names.put(u.getId(), display);
        }
        return names;
    }

    private ArticleResponseDTO toDto(KnowledgeArticle a, Map<Long, String> categoryNames,
                                     Map<Long, String> authorNames, boolean withContent) {
        ArticleResponseDTO dto = new ArticleResponseDTO();
        dto.setId(a.getId());
        dto.setTitle(a.getTitle());
        dto.setSummary(a.getSummary());
        if (withContent) {
            dto.setContent(a.getContent());
        }
        dto.setCoverImage(a.getCoverImage());
        dto.setTags(a.getTags());
        dto.setTagArray(splitTags(a.getTags()));
        dto.setCategoryId(a.getCategoryId());
        dto.setCategoryName(categoryNames.get(a.getCategoryId()));
        dto.setAuthorId(a.getAuthorId());
        dto.setAuthorName(a.getAuthorId() == null ? null : authorNames.get(a.getAuthorId()));
        dto.setReadCount(a.getReadCount());
        dto.setStatus(a.getStatus());
        dto.setStatusDisplayName(describeStatus(a.getStatus()));
        dto.setTimeFields(a.getPublishedAt(), a.getCreatedAt(), a.getUpdatedAt());
        return dto;
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String describeStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_DRAFT -> "草稿";
            case STATUS_PUBLISHED -> "已发布";
            case STATUS_OFFLINE -> "已下线";
            default -> "未知";
        };
    }
}
