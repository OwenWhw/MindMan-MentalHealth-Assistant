package com.mindman.service;

import com.mindman.common.page.PageVO;
import com.mindman.dto.ArticleCategorySaveDTO;
import com.mindman.dto.ArticleCategoryVO;
import com.mindman.entity.ArticleCategory;

import java.util.List;

public interface ArticleCategoryService {

    /** 获取分类树（含每分类文章数统计） */
    List<ArticleCategoryVO> listTree();

    /** 分类分页（管理端表格用） */
    PageVO<ArticleCategoryVO> page(int page, int pageSize, String keyword, Integer status);

    ArticleCategoryVO detail(Long id);

    Long save(ArticleCategorySaveDTO dto);

    void update(ArticleCategorySaveDTO dto);

    void delete(Long id);

    ArticleCategory getOrThrow(Long id);
}