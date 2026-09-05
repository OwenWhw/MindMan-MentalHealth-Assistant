package com.mindman.service;

import com.mindman.common.page.PageVO;
import com.mindman.dto.AdminSessionVO;
import com.mindman.dto.ChatMessageVO;
import com.mindman.dto.ChatSendDTO;
import com.mindman.dto.ChatSessionCreateDTO;
import com.mindman.dto.ChatSessionVO;
import com.mindman.entity.ChatSession;

import java.util.List;

/**
 * 心理咨询 AI 对话服务。
 *
 * <h3>功能概览</h3>
 * <ul>
 *   <li><b>会话管理</b>：创建会话、查询用户的会话列表、获取会话详情、删除会话</li>
 *   <li><b>消息收发</b>：发送用户消息、获取会话历史消息、AI 回复生成（预留）</li>
 * </ul>
 *
 * <h3>AI 对话能力说明</h3>
 * <p>本模块为心理咨询 AI 对话的后端基础架构，提供：</p>
 * <ol>
 *   <li>完整的会话生命周期管理（CRUD）</li>
 *   <li>消息持久化与历史查询</li>
 *   <li>AI 回复接口预留（当前返回模拟回复，后续接入 LLM 服务后替换）</li>
 *   <li>情绪维度分析字段（每条 AI 消息可附带情绪标签）</li>
 * </ol>
 *
 * <h3>调用流程示例</h3>
 * <pre>
 * 1. POST /api/chat/sessions        → 创建新会话
 * 2. GET  /api/chat/sessions        → 查询我的会话列表
 * 3. POST /api/chat/messages        → 发送消息（用户说话）
 *    → 返回: 用户消息 + AI回复（含情绪分析）
 * 4. GET  /api/chat/sessions/{id}/messages → 获取历史消息
 * 5. DELETE /api/chat/sessions/{id} → 删除会话
 * </pre>
 */
public interface ChatService {

    /**
     * 创建新的咨询会话
     *
     * @param userId 当前登录用户ID
     * @param dto    创建请求（title 可选）
     * @return 会话信息
     */
    ChatSessionVO createSession(Long userId, ChatSessionCreateDTO dto);

    /**
     * 查询当前用户的会话列表（按更新时间倒序）
     *
     * @param userId 当前登录用户ID
     * @return 会话列表（含最近消息预览和消息数量）
     */
    List<ChatSessionVO> listSessions(Long userId);

    /**
     * 获取会话详情
     *
     * @param userId    当前登录用户ID（用于权限校验）
     * @param sessionId 会话ID
     * @return 会话详情
     */
    ChatSessionVO getSessionDetail(Long userId, Long sessionId);

    /**
     * 删除会话（及其所有消息）
     *
     * @param userId    当前登录用户ID（用于权限校验）
     * @param sessionId 会话ID
     */
    void deleteSession(Long userId, Long sessionId);

    /**
     * 发送消息并获取 AI 回复。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>保存用户消息到数据库</li>
     *   <li>调用 AI 服务生成回复（当前为模拟，后续接入真实 LLM）</li>
     *   <li>保存 AI 回复到数据库（附带情绪分析标签）</li>
     *   <li>返回用户消息 + AI 回复</li>
     * </ol>
     *
     * @param userId 当前登录用户ID
     * @param dto    发送请求（sessionId + content）
     * @return 包含用户消息和AI回复的列表
     */
    List<ChatMessageVO> sendMessage(Long userId, ChatSendDTO dto);

    /**
     * 获取会话历史消息（分页，按时间正序）
     *
     * @param userId    当前登录用户ID（用于权限校验）
     * @param sessionId 会话ID
     * @param page     页码（从1开始）
     * @param size     每页条数
     * @return 消息列表
     */
    List<ChatMessageVO> listMessages(Long userId, Long sessionId, int page, int size);

    /**
     * 获取会话实体（内部使用，用于校验归属权）。
     * 若会话不存在或不属于当前用户，抛出 NotFoundException。
     *
     * @param userId    当前登录用户ID
     * @param sessionId 会话ID
     * @return 会话实体
     */
    ChatSession getSessionEntity(Long userId, Long sessionId);

    /**
     * 消息收发后刷新会话：自动生成标题（首条消息）并更新 updatedAt。
     * 流式聊天也要调用，保证会话列表按最近使用排序。
     *
     * @param userId       当前登录用户ID
     * @param sessionId    会话ID
     * @param userContent  用户刚发送的内容（用于首条消息自动命名）
     */
    void touchSession(Long userId, Long sessionId, String userContent);

    /**
     * 归档（结束）会话：将状态置为 2（已结束）。仅本人可操作。
     *
     * @param userId    当前登录用户ID
     * @param sessionId 会话ID
     */
    void archiveSession(Long userId, Long sessionId);

    // ======================== 管理端 ========================

    /**
     * 分页查询全部咨询会话（管理端），关联用户信息、最后消息预览、情绪标签与消息数。
     */
    PageVO<AdminSessionVO> adminPageSessions(int page, int pageSize, String keyword, Integer status);

    /** 管理端：获取指定会话的全部消息（无归属校验） */
    List<ChatMessageVO> adminListMessages(Long sessionId);

    /** 管理端：删除会话及其全部消息（级联） */
    void adminDeleteSession(Long sessionId);
}
