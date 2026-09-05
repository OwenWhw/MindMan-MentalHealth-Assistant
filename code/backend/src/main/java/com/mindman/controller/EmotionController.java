package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.common.page.PageVO;
import com.mindman.dto.EmotionDiaryVO;
import com.mindman.dto.EmotionGardenVO;
import com.mindman.dto.EmotionRecordSaveDTO;
import com.mindman.service.EmotionRecordService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 情绪记录控制器。
 *
 * <h3>接口列表</h3>
 * <pre>
 * 用户端（情绪花园）：
 *   GET    /api/emotion/garden            获取我的情绪花园
 *   POST   /api/emotion/garden            种下今日心情
 *   PUT    /api/emotion/garden/{id}       编辑心情之花
 *   DELETE /api/emotion/garden/{id}       删除心情之花
 *
 * 管理端（情绪日志）：
 *   GET    /api/emotion/diary/page        分页查询情绪日志（管理员）
 *   DELETE /api/emotion/diary/{id}        删除情绪日志（管理员）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
@Tag(name = "情绪记录", description = "情绪花园（用户端）+ 情绪日志（管理端）")
public class EmotionController {

    private final EmotionRecordService emotionRecordService;

    // ======================== 用户端：情绪花园 ========================

    @GetMapping("/garden")
    @Operation(summary = "获取我的情绪花园")
    public R<List<EmotionGardenVO>> getGarden() {
        return R.ok(emotionRecordService.listGarden(LoginUser.get()));
    }

    @PostMapping("/garden")
    @Operation(summary = "种下今日心情")
    public R<EmotionGardenVO> plant(@Valid @RequestBody EmotionRecordSaveDTO dto) {
        return R.ok(emotionRecordService.plant(LoginUser.get(), dto));
    }

    @PutMapping("/garden/{id}")
    @Operation(summary = "编辑心情之花")
    public R<EmotionGardenVO> update(@PathVariable Long id, @Valid @RequestBody EmotionRecordSaveDTO dto) {
        return R.ok(emotionRecordService.update(LoginUser.get(), id, dto));
    }

    @DeleteMapping("/garden/{id}")
    @Operation(summary = "删除心情之花")
    public R<Void> delete(@PathVariable Long id) {
        emotionRecordService.deleteOwn(LoginUser.get(), id);
        return R.ok();
    }

    // ======================== 管理端：情绪日志 ========================

    @GetMapping("/diary/page")
    @Operation(summary = "分页查询情绪日志（管理端）")
    public R<PageVO<EmotionDiaryVO>> diaryPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String scoreRange) {
        LoginUser.requireAdmin();
        return R.page(emotionRecordService.pageDiary(page, pageSize, userId, scoreRange));
    }

    @DeleteMapping("/diary/{id}")
    @Operation(summary = "删除情绪日志（管理端）")
    public R<Void> deleteDiary(@PathVariable Long id) {
        LoginUser.requireAdmin();
        emotionRecordService.deleteDiary(id);
        return R.ok();
    }
}
