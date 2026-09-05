package com.mindman.service;

import com.mindman.common.page.PageVO;
import com.mindman.dto.EmotionDiaryVO;
import com.mindman.dto.EmotionGardenVO;
import com.mindman.dto.EmotionRecordSaveDTO;

import java.util.List;

/**
 * 情绪记录服务（情绪花园 + 情绪日志）。
 *
 * <h3>功能概览</h3>
 * <ul>
 *   <li><b>用户端（情绪花园）</b>：查询我的花园、种花、编辑、删除（仅本人）</li>
 *   <li><b>管理端（情绪日志）</b>：分页查询全量日志（关联用户信息）、删除</li>
 * </ul>
 */
public interface EmotionRecordService {

    /**
     * 查询指定用户的情绪花园（按日期倒序）
     *
     * @param userId 当前登录用户ID
     * @return 花园花朵列表
     */
    List<EmotionGardenVO> listGarden(Long userId);

    /**
     * 种下今日心情（新增一条情绪记录）
     *
     * @param userId 当前登录用户ID
     * @param dto    心情数据
     * @return 新建的花朵视图
     */
    EmotionGardenVO plant(Long userId, EmotionRecordSaveDTO dto);

    /**
     * 编辑心情之花
     *
     * @param userId 当前登录用户ID（用于归属校验）
     * @param id     记录ID
     * @param dto    心情数据
     * @return 更新后的花朵视图
     */
    EmotionGardenVO update(Long userId, Long id, EmotionRecordSaveDTO dto);

    /**
     * 删除心情之花（仅本人）
     *
     * @param userId 当前登录用户ID
     * @param id     记录ID
     */
    void deleteOwn(Long userId, Long id);

    /**
     * 管理端分页查询情绪日志。
     *
     * @param page       页码（从1开始）
     * @param pageSize   每页条数
     * @param userId     按用户ID筛选（空串/null 表示不筛选）
     * @param scoreRange 情绪评分区间：1-2 / 3 / 4-5（其它值忽略）
     * @return 分页视图
     */
    PageVO<EmotionDiaryVO> pageDiary(int page, int pageSize, String userId, String scoreRange);

    /**
     * 管理端删除情绪日志（管理员权限由 Controller 校验）
     *
     * @param id 记录ID
     */
    void deleteDiary(Long id);
}
