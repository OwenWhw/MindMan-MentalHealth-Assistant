package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.common.page.PageVO;
import com.mindman.dto.AdminSessionVO;
import com.mindman.dto.ChatMessageVO;
import com.mindman.service.ChatService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端咨询管理控制器。
 *
 * <h3>接口列表</h3>
 * <pre>
 * GET    /api/admin/consult/sessions               分页查询咨询会话（关联用户信息）
 * GET    /api/admin/consult/sessions/{id}/messages 查看会话消息
 * DELETE /api/admin/consult/sessions/{id}          删除会话（级联消息）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/consult")
@RequiredArgsConstructor
@Tag(name = "咨询管理（管理端）", description = "咨询会话分页 / 消息查看 / 删除")
public class ConsultAdminController {

    private final ChatService chatService;

    @GetMapping("/sessions")
    @Operation(summary = "分页查询咨询会话（管理端）")
    public R<PageVO<AdminSessionVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        LoginUser.requireAdmin();
        return R.page(chatService.adminPageSessions(page, pageSize, keyword, status));
    }

    @GetMapping("/sessions/{id}/messages")
    @Operation(summary = "查看会话消息（管理端）")
    public R<List<ChatMessageVO>> messages(@PathVariable Long id) {
        LoginUser.requireAdmin();
        return R.ok(chatService.adminListMessages(id));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "删除会话（管理端）")
    public R<Void> delete(@PathVariable Long id) {
        LoginUser.requireAdmin();
        chatService.adminDeleteSession(id);
        return R.ok();
    }
}
