package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.common.page.PageVO;
import com.mindman.dto.*;
import com.mindman.service.ArticleCategoryService;
import com.mindman.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口
 *
 * <p>前端约定路径：{@code /api/knowledge/category/*} 和 {@code /api/knowledge/article/*}
 * <p>所有接口需登录；写操作要求管理员权限。
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final ArticleCategoryService categoryService;
    private final ArticleService articleService;

    // ==================== 分类管理 ====================

    @Operation(summary = "分类树（含每分类文章数）")
    @GetMapping("/category/tree")
    public R<List<ArticleCategoryVO>> categoryTree() {
        return R.ok(categoryService.listTree());
    }

    @Operation(summary = "分类分页（管理端表格）")
    @GetMapping("/category/page")
    public R<PageVO<ArticleCategoryVO>> categoryPage(
            @RequestParam(required = false, defaultValue = "1") Long page,
            @RequestParam(required = false, defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.page(categoryService.page(page.intValue(), pageSize.intValue(), keyword, status));
    }

    @Operation(summary = "分类详情")
    @GetMapping("/category/{id}")
    public R<ArticleCategoryVO> categoryDetail(@PathVariable Long id) {
        return R.ok(categoryService.detail(id));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/category")
    public R<Long> createCategory(@Valid @RequestBody ArticleCategorySaveDTO dto) {
        return R.created(categoryService.save(dto));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/category")
    public R<Void> updateCategory(@Valid @RequestBody ArticleCategorySaveDTO dto) {
        if (dto.getCategoryId() == null) {
            return R.badRequest("分类ID不能为空");
        }
        categoryService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/category/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }

    // ==================== 文章管理 ====================

    @Operation(summary = "文章分页")
    @GetMapping("/article/page")
    public R<PageVO<ArticleVO>> articlePage(@Valid ArticleQueryDTO query) {
        return R.page(articleService.page(query));
    }

    @Operation(summary = "文章详情（阅读量+1）")
    @GetMapping("/article/{id}")
    public R<ArticleVO> articleDetail(@PathVariable Long id) {
        return R.ok(articleService.detail(id));
    }

    @Operation(summary = "新增文章")
    @PostMapping("/article")
    public R<Long> createArticle(@Valid @RequestBody ArticleSaveDTO dto) {
        if (dto.getArticleId() != null) {
            return R.badRequest("新增请求不应携带 articleId");
        }
        return R.created(articleService.save(dto));
    }

    @Operation(summary = "更新文章")
    @PutMapping("/article")
    public R<Void> updateArticle(@Valid @RequestBody ArticleSaveDTO dto) {
        if (dto.getArticleId() == null) {
            return R.badRequest("更新请求必须携带 articleId");
        }
        articleService.save(dto);
        return R.ok();
    }

    @Operation(summary = "上下线/草稿切换")
    @PutMapping("/article/status")
    public R<Void> updateArticleStatus(@RequestBody Map<String, Object> body) {
        Object articleId = body.get("articleId");
        Object status = body.get("status");
        if (articleId == null || status == null) {
            return R.badRequest("articleId 与 status 必填");
        }
        try {
            articleService.updateStatus(Long.valueOf(articleId.toString()), Integer.valueOf(status.toString()));
        } catch (NumberFormatException e) {
            return R.badRequest("参数格式不合法");
        }
        return R.ok();
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/article/{id}")
    public R<Void> deleteArticle(@PathVariable Long id) {
        articleService.delete(id);
        return R.ok();
    }
}