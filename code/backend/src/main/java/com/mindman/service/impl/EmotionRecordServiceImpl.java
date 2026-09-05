package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindman.common.exception.NotFoundException;
import com.mindman.common.page.PageVO;
import com.mindman.dto.EmotionDiaryVO;
import com.mindman.dto.EmotionGardenVO;
import com.mindman.dto.EmotionRecordSaveDTO;
import com.mindman.entity.EmotionRecord;
import com.mindman.entity.User;
import com.mindman.mapper.EmotionRecordMapper;
import com.mindman.mapper.UserMapper;
import com.mindman.service.EmotionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 情绪记录服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionRecordServiceImpl implements EmotionRecordService {

    private final EmotionRecordMapper emotionRecordMapper;
    private final UserMapper userMapper;

    // ======================== 用户端：情绪花园 ========================

    @Override
    public List<EmotionGardenVO> listGarden(Long userId) {
        List<EmotionRecord> list = emotionRecordMapper.selectList(
                new LambdaQueryWrapper<EmotionRecord>()
                        .eq(EmotionRecord::getUserId, userId)
                        .orderByDesc(EmotionRecord::getRecordDate)
                        .orderByDesc(EmotionRecord::getCreatedAt)
        );
        return list.stream().map(EmotionGardenVO::from).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EmotionGardenVO plant(Long userId, EmotionRecordSaveDTO dto) {
        EmotionRecord r = new EmotionRecord();
        r.setUserId(userId);
        r.setEmotion(dto.getEmotion());
        r.setNote(dto.getContent());
        r.setEmotionScore(dto.getEmotionScore());
        r.setSleepScore(dto.getSleepScore());
        r.setStressScore(dto.getStressScore());
        r.setTrigger(dto.getTrigger());
        r.setRecordDate(LocalDate.now());
        r.setCreatedAt(LocalDateTime.now());
        emotionRecordMapper.insert(r);
        log.info("用户 {} 种下心情之花 id={}, emotion={}", userId, r.getId(), r.getEmotion());
        return EmotionGardenVO.from(r);
    }

    @Override
    @Transactional
    public EmotionGardenVO update(Long userId, Long id, EmotionRecordSaveDTO dto) {
        EmotionRecord r = getOwned(userId, id);
        r.setEmotion(dto.getEmotion());
        r.setNote(dto.getContent());
        r.setEmotionScore(dto.getEmotionScore());
        r.setSleepScore(dto.getSleepScore());
        r.setStressScore(dto.getStressScore());
        r.setTrigger(dto.getTrigger());
        emotionRecordMapper.updateById(r);
        log.info("用户 {} 编辑心情之花 id={}", userId, id);
        return EmotionGardenVO.from(r);
    }

    @Override
    @Transactional
    public void deleteOwn(Long userId, Long id) {
        getOwned(userId, id);
        emotionRecordMapper.deleteById(id);
        log.info("用户 {} 删除心情之花 id={}", userId, id);
    }

    // ======================== 管理端：情绪日志 ========================

    @Override
    public PageVO<EmotionDiaryVO> pageDiary(int page, int pageSize, String userId, String scoreRange) {
        LambdaQueryWrapper<EmotionRecord> wrapper = new LambdaQueryWrapper<>();

        // 用户ID筛选（空串/非法值则忽略）
        Long uid = parseUserId(userId);
        if (uid != null) {
            wrapper.eq(EmotionRecord::getUserId, uid);
        }

        // 情绪评分区间筛选
        applyScoreRange(wrapper, scoreRange);

        wrapper.orderByDesc(EmotionRecord::getRecordDate)
               .orderByDesc(EmotionRecord::getCreatedAt);

        IPage<EmotionRecord> paged = emotionRecordMapper.selectPage(new Page<>(page, pageSize), wrapper);

        // 批量补齐用户信息（昵称/头像）
        List<Long> userIds = paged.getRecords().stream()
                .map(EmotionRecord::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<EmotionDiaryVO> list = paged.getRecords().stream()
                .map(r -> EmotionDiaryVO.from(r, userMap.get(r.getUserId())))
                .collect(Collectors.toList());

        return PageVO.of(paged.getTotal(), page, pageSize, list);
    }

    @Override
    @Transactional
    public void deleteDiary(Long id) {
        emotionRecordMapper.deleteById(id);
        log.info("管理端删除情绪日志 id={}", id);
    }

    // ======================== 内部方法 ========================

    private EmotionRecord getOwned(Long userId, Long id) {
        EmotionRecord r = emotionRecordMapper.selectById(id);
        if (r == null) {
            throw new NotFoundException("心情记录不存在");
        }
        if (!r.getUserId().equals(userId)) {
            throw new NotFoundException("心情记录不存在");
        }
        return r;
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyScoreRange(LambdaQueryWrapper<EmotionRecord> wrapper, String scoreRange) {
        if (scoreRange == null || scoreRange.isBlank()) return;
        switch (scoreRange) {
            case "1-2":
                wrapper.between(EmotionRecord::getEmotionScore, 1, 2);
                break;
            case "3":
                wrapper.eq(EmotionRecord::getEmotionScore, 3);
                break;
            case "4-5":
                wrapper.ge(EmotionRecord::getEmotionScore, 4);
                break;
            default:
                break;
        }
    }
}
