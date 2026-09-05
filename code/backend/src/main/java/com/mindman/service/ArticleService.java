package com.mindman.service;

import com.mindman.common.page.PageVO;
import com.mindman.dto.ArticleQueryDTO;
import com.mindman.dto.ArticleSaveDTO;
import com.mindman.dto.ArticleVO;

public interface ArticleService {

    /** 分页查询（返回带 categoryName 的 VO） */
    PageVO<ArticleVO> page(ArticleQueryDTO query);

    /** 文章详情（阅读量 +1，返回 VO） */
    ArticleVO detail(Long id);

    /** 保存（新增/更新），有 articleId 走更新 */
    Long save(ArticleSaveDTO dto);

    /** 上下线切换 */
    void updateStatus(Long articleId, Integer status);

    /** 逻辑删除 */
    void delete(Long id);
}