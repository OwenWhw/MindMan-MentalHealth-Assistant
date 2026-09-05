package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.dto.AnalysisOverviewVO;
import com.mindman.service.AnalysisService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 数据分析控制器（管理端）。
 *
 * <h3>接口</h3>
 * <pre>
 * GET /api/analysis/overview?days=14   获取平台综合分析数据
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "数据分析", description = "平台综合分析（管理端）")
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/overview")
    @Operation(summary = "获取综合分析数据（管理端）")
    public R<AnalysisOverviewVO> overview(@RequestParam(defaultValue = "14") int days) {
        LoginUser.requireAdmin();
        return R.ok(analysisService.overview(days));
    }
}
