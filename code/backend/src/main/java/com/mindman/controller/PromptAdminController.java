package com.mindman.controller;

import com.mindman.ai.ChatClientRouter;
import com.mindman.common.R;
import com.mindman.entity.PromptTemplate;
import com.mindman.service.PromptTemplateService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端 - 提示词模板管理 + AI 通道诊断。
 *
 * <p>接口一览：
 * <ul>
 *   <li>GET  /api/admin/prompt/list           模板列表</li>
 *   <li>POST /api/admin/prompt/save           新增/更新模板</li>
 *   <li>POST /api/admin/prompt/enable/{id}    启用（同场景互斥）</li>
 *   <li>DELETE /api/admin/prompt/{id}         删除模板</li>
 *   <li>POST /api/admin/prompt/render         渲染测试：scene + vars → 渲染结果</li>
 *   <li>GET  /api/admin/prompt/channels       AI 通道诊断（cloud/ollama 当前状态）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/prompt")
@Tag(name = "提示词模板管理")
@RequiredArgsConstructor
public class PromptAdminController {

    private final PromptTemplateService promptService;
    private final ChatClientRouter chatClientRouter;

    @GetMapping("/list")
    @Operation(summary = "模板列表")
    public R<List<PromptTemplate>> list() {
        LoginUser.requireAdmin();
        return R.ok(promptService.listAll());
    }

    @PostMapping("/save")
    @Operation(summary = "新增/更新模板")
    public R<PromptTemplate> save(@RequestBody PromptTemplate template) {
        LoginUser.requireAdmin();
        if (template.getScene() == null || template.getScene().isBlank()) {
            return R.error("scene 不能为空");
        }
        if (template.getTemplate() == null || template.getTemplate().isBlank()) {
            return R.error("template 不能为空");
        }
        return R.ok(promptService.save(template));
    }

    @PostMapping("/enable/{id}")
    @Operation(summary = "启用模板（同场景其它模板自动停用）")
    public R<Void> enable(@PathVariable Long id) {
        LoginUser.requireAdmin();
        promptService.enable(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板")
    public R<Void> delete(@PathVariable Long id) {
        LoginUser.requireAdmin();
        promptService.delete(id);
        return R.ok();
    }

    @PostMapping("/render")
    @Operation(summary = "渲染测试：按场景取生效模板并替换 {变量}")
    public R<Map<String, Object>> render(@RequestBody Map<String, Object> body) {
        LoginUser.requireAdmin();
        String scene = String.valueOf(body.getOrDefault("scene", ""));
        @SuppressWarnings("unchecked")
        Map<String, String> vars = (Map<String, String>) body.getOrDefault("vars", Map.of());
        String rendered = promptService.render(scene, vars, () -> "（该场景无生效模板，将回退到内置提示词）");
        PromptTemplate active = promptService.getActiveByScene(scene);
        return R.ok(Map.of(
                "scene", scene,
                "source", active != null ? "db:" + active.getName() : "fallback（内置）",
                "rendered", rendered));
    }

    @GetMapping("/channels")
    @Operation(summary = "AI 通道诊断（cloud / ollama 可用性）")
    public R<Map<String, Object>> channels() {
        LoginUser.requireAdmin();
        return R.ok(chatClientRouter.diagnostics());
    }
}
