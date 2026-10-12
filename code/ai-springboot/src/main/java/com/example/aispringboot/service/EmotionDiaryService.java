package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aispringboot.DTO.command.EmotionDiaryCommandDTO;
import com.example.aispringboot.DTO.command.EmotionDiaryPageQuery;
import com.example.aispringboot.DTO.response.EmotionDiaryResponseDTO;
import com.example.aispringboot.common.AuthUtil;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.entity.AiAnalysisTask;
import com.example.aispringboot.entity.EmotionDiary;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.mapper.AiAnalysisTaskMapper;
import com.example.aispringboot.mapper.EmotionDiaryMapper;
import com.example.aispringboot.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 情绪日记。
 *
 * <p>提交日记不直接调模型，而是写一条 {@code ai_analysis_task}（{@code task_type=AUTO}、
 * {@code status=PENDING}）交给消费者异步处理——ai_analysis_task 表的 14 条既有数据
 * 全部是 COMPLETED，说明这套结构原本就是按任务队列设计的。
 *
 * <p>列表页需要 username / nickname。这里不写联表 SQL，而是分页查主表后按 user_id
 * 批量回查：分页插件的 COUNT 改写遇到 JOIN + ORDER BY 容易生成非法 SQL，
 * 而数据量下多一次按主键批量查询的代价可以忽略。
 */
@Slf4j
@Service
public class EmotionDiaryService {

    /** 自动任务的优先级，与既有数据一致：AUTO=2，ADMIN=3 */
    public static final int PRIORITY_AUTO = 2;

    public static final String TASK_TYPE_AUTO = "AUTO";
    public static final String TASK_STATUS_PENDING = "PENDING";
    public static final int DEFAULT_MAX_RETRY = 3;

    @Resource
    private EmotionDiaryMapper diaryMapper;

    @Resource
    private AiAnalysisTaskMapper taskMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 提交日记。
     *
     * @return 新日记主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createDiary(EmotionDiaryCommandDTO command) {
        Long userId = AuthUtil.requireUserId();

        EmotionDiary diary = new EmotionDiary();
        diary.setUserId(userId);
        diary.setDiaryDate(command.getDiaryDate() == null ? LocalDate.now() : command.getDiaryDate());
        diary.setMoodScore(command.getMoodScore());
        diary.setDominantEmotion(blankToNull(command.getDominantEmotion()));
        diary.setEmotionTriggers(blankToNull(command.getEmotionTriggers()));
        diary.setDiaryContent(blankToNull(command.getDiaryContent()));
        diary.setSleepQuality(command.getSleepQuality());
        diary.setStressLevel(command.getStressLevel());
        diaryMapper.insert(diary);

        AiAnalysisTask task = new AiAnalysisTask();
        task.setDiaryId(diary.getId());
        task.setUserId(userId);
        task.setTaskType(TASK_TYPE_AUTO);
        task.setStatus(TASK_STATUS_PENDING);
        task.setPriority(PRIORITY_AUTO);
        task.setRetryCount(0);
        task.setMaxRetryCount(DEFAULT_MAX_RETRY);
        taskMapper.insert(task);

        return diary.getId();
    }

    public IPage<EmotionDiaryResponseDTO> adminPage(EmotionDiaryPageQuery query) {
        AuthUtil.requireAdmin();

        LambdaQueryWrapper<EmotionDiary> wrapper = Wrappers.lambdaQuery(EmotionDiary.class)
                .eq(query.getUserId() != null, EmotionDiary::getUserId, query.getUserId())
                .ge(query.getMoodMin() != null, EmotionDiary::getMoodScore, query.getMoodMin())
                .le(query.getMoodMax() != null, EmotionDiary::getMoodScore, query.getMoodMax())
                .ge(query.getDateFrom() != null, EmotionDiary::getDiaryDate, query.getDateFrom())
                .le(query.getDateTo() != null, EmotionDiary::getDiaryDate, query.getDateTo())
                .orderByDesc(EmotionDiary::getDiaryDate)
                .orderByDesc(EmotionDiary::getId);

        Page<EmotionDiary> page = new Page<>(query.getCurrent(), query.getSizeValue());
        IPage<EmotionDiary> raw = diaryMapper.selectPage(page, wrapper);

        List<Long> userIds = raw.getRecords().stream()
                .map(EmotionDiary::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = loadUsers(userIds);

        List<EmotionDiaryResponseDTO> rows = raw.getRecords().stream()
                .map(d -> toResponse(d, users.get(d.getUserId())))
                .toList();

        Page<EmotionDiaryResponseDTO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(rows);
        return result;
    }

    /**
     * 后台删除日记。
     *
     * <p>同时清掉它的 ai_analysis_task。库上已有 {@code fk_ai_task_diary ... ON DELETE CASCADE}，
     * 单靠约束其实就够了，这里再显式删一次是不想把一致性寄托在数据库约束上——
     * 一旦将来拆库或调整外键，行为仍然一致，代码本身也能说明这条关联关系。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDiary(Long id) {
        AuthUtil.requireAdmin();
        EmotionDiary diary = diaryMapper.selectById(id);
        if (diary == null) {
            throw new BusinessException(ResultCode.DIARY_NOT_EXIST.getCode(), "日记不存在");
        }
        taskMapper.delete(Wrappers.lambdaQuery(AiAnalysisTask.class)
                .eq(AiAnalysisTask::getDiaryId, diary.getId()));
        diaryMapper.deleteById(id);
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private static EmotionDiaryResponseDTO toResponse(EmotionDiary d, User user) {
        EmotionDiaryResponseDTO dto = new EmotionDiaryResponseDTO();
        dto.setId(d.getId());
        dto.setUserId(d.getUserId());
        dto.setUsername(user == null ? null : user.getUsername());
        dto.setNickname(user == null ? null : user.getNickname());
        dto.setDiaryDate(d.getDiaryDate());
        dto.setMoodScore(d.getMoodScore());
        dto.setDominantEmotion(d.getDominantEmotion());
        dto.setEmotionTriggers(d.getEmotionTriggers());
        dto.setDiaryContent(d.getDiaryContent());
        dto.setSleepQuality(d.getSleepQuality());
        dto.setStressLevel(d.getStressLevel());
        dto.setAiEmotionAnalysis(d.getAiEmotionAnalysis());
        dto.setAiAnalysisUpdatedAt(d.getAiAnalysisUpdatedAt());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setUpdatedAt(d.getUpdatedAt());
        return dto;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
