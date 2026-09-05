package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.dto.ArticleVO;
import com.mindman.service.RecommendService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章推荐 Controller。
 *
 * <p>面向首页/详情页等场景，返回经过个性化算法排序的文章列表。</p>
 */
@RestController
@RequestMapping("/api/articles")
@Tag(name = "文章推荐")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @GetMapping("/recommend")
    @Operation(summary = "AI 推荐文章（登录用户走个性化，访客走热门）")
    public R<List<ArticleVO>> recommend(
            @RequestParam(defaultValue = "3") Integer limit) {
        Long uid = LoginUser.get();
        return R.ok(recommendService.recommend(uid, limit));
    }
}
