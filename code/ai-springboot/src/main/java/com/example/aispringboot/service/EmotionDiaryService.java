package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * <p>情绪记录的唯一录入途径是 AI 咨询对话：消息命中关键词时写一条记录
 * （source=CHAT），同时写 {@code ai_analysis_task}（{@code task_type=AUTO}、
 * {@code status=PENDING}）交给消费者异步处理——ai_analysis_task 表的既有数据
 * 说明这套结构原本就是按任务队列设计的。存量手动填写的记录 source 为空，
 * 查看时按 MANUAL 语义处理。
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

    /** 记录来源：AI咨询对话自动记录（唯一录入途径；存量手动数据 source 为空） */
    public static final String SOURCE_CHAT = "CHAT";

    @Resource
    private EmotionDiaryMapper diaryMapper;

    @Resource
    private AiAnalysisTaskMapper taskMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * AI 咨询对话触发的自动记录：消息命中关键词时调用。
     *
     * <p>这是情绪记录的唯一录入途径。SSE 推流跑在线程池里，没有请求上下文
     * （AuthUtil 取不到用户），所以 userId、会话ID 由调用方显式传入。
     * 正文即用户消息原文，触发因素记命中词；评分取配置默认分，其余字段留空，
     * 交给 ai_analysis_task 异步评估回填 ai_emotion_analysis。
     *
     * @return 新日记主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createDiaryFromChat(Long userId, Long sessionId, String message,
                                    List<String> matchedKeywords, Integer defaultMoodScore) {
        EmotionDiary diary = new EmotionDiary();
        diary.setUserId(userId);
        diary.setDiaryDate(LocalDate.now());
        diary.setMoodScore(defaultMoodScore);
        diary.setEmotionTriggers("AI咨询对话触发：关键词「" + String.join("、", matchedKeywords) + "」");
        diary.setDiaryContent(message);
        diary.setSource(SOURCE_CHAT);
        diary.setSourceSessionId(sessionId);
        return insertDiaryWithTask(diary, userId);
    }

    /**
     * 同一会话是否已产生过 CHAT 来源记录（同会话仅记录一次）。
     */
    public boolean hasChatCapture(Long sessionId) {
        return diaryMapper.selectCount(Wrappers.lambdaQuery(EmotionDiary.class)
                .eq(EmotionDiary::getSource, SOURCE_CHAT)
                .eq(EmotionDiary::getSourceSessionId, sessionId)) > 0;
    }

    /**
     * 插入日记并投递 AUTO 分析任务，评估由消费者异步完成。
     *
     * @return 新日记主键
     */
    private Long insertDiaryWithTask(EmotionDiary diary, Long userId) {
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

    /**
     * 当前用户自己的全部情绪记录，按日期倒序——情绪日记页"观看自己心情"的数据源。
     */
    public List<EmotionDiaryResponseDTO> myDiaries() {
        Long userId = AuthUtil.requireUserId();

        List<EmotionDiary> records = diaryMapper.selectList(
                Wrappers.lambdaQuery(EmotionDiary.class)
                        .eq(EmotionDiary::getUserId, userId)
                        .orderByDesc(EmotionDiary::getDiaryDate)
                        .orderByDesc(EmotionDiary::getId));

        Map<Long, User> users = loadUsers(List.of(userId));
        return records.stream()
                .map(d -> toResponse(d, users.get(d.getUserId())))
                .toList();
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
        dto.setSource(d.getSource());
        dto.setAiAnalysisUpdatedAt(d.getAiAnalysisUpdatedAt());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setUpdatedAt(d.getUpdatedAt());
        return dto;
    }
}
