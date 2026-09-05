package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindman.common.enums.ArticleStatusEnum;
import com.mindman.common.exception.NotFoundException;
import com.mindman.common.page.PageVO;
import com.mindman.dto.ArticleQueryDTO;
import com.mindman.dto.ArticleSaveDTO;
import com.mindman.dto.ArticleVO;
import com.mindman.entity.Article;
import com.mindman.entity.ArticleCategory;
import com.mindman.mapper.ArticleCategoryMapper;
import com.mindman.mapper.ArticleMapper;
import com.mindman.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章 Service - MyBatis-Plus 查询条件构建演示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleCategoryMapper categoryMapper;

    @Override
    public PageVO<ArticleVO> page(ArticleQueryDTO q) {
        Page<Article> page = new Page<>(q.getPage(), q.getPageSize());

        // title 与 keyword 等价兼容
        String keyword = StringUtils.hasText(q.getTitle()) ? q.getTitle() : q.getKeyword();

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper
                .eq(q.getCategoryId() != null, Article::getCategoryId, q.getCategoryId())
                .like(StringUtils.hasText(keyword), Article::getTitle, keyword)
                .eq(q.getStatus() != null, Article::getStatus, q.getStatus())
                .orderByDesc(Article::getPublishTime)
                .orderByDesc(Article::getId);

        Page<Article> result = articleMapper.selectPage(page, wrapper);

        List<ArticleVO> vos = toVO(result.getRecords());
        return PageVO.of(result.getTotal(), result.getCurrent(), result.getSize(), vos);
    }

    @Override
    @Transactional
    public ArticleVO detail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new NotFoundException("文章不存在");
        }
        // 阅读量原子 +1
        articleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Article>()
                .eq("id", id)
                .setSql("`reads` = `reads` + 1"));
        article.setReads(article.getReads() + 1);

        List<ArticleVO> list = toVO(Collections.singletonList(article));
        return list.get(0);
    }

    @Override
    @Transactional
    public Long save(ArticleSaveDTO dto) {
        // 校验分类存在
        ArticleCategory category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null) {
            throw new NotFoundException("所选分类不存在");
        }

        if (dto.getArticleId() == null) {
            // 新增
            Article a = new Article();
            applyDTO(a, dto);
            if (a.getStatus() == null) a.setStatus(ArticleStatusEnum.PUBLISHED.getCode());
            if (StringUtils.hasText(a.getStatus().toString()) && a.getStatus() == ArticleStatusEnum.PUBLISHED.getCode()
                    && a.getPublishTime() == null) {
                a.setPublishTime(LocalDateTime.now());
            }
            articleMapper.insert(a);
            return a.getId();
        } else {
            // 更新
            Article exist = articleMapper.selectById(dto.getArticleId());
            if (exist == null) throw new NotFoundException("文章不存在");
            applyDTO(exist, dto);
            // 状态从草稿变为已发布时补时间
            if (exist.getStatus() == ArticleStatusEnum.PUBLISHED.getCode() && exist.getPublishTime() == null) {
                exist.setPublishTime(LocalDateTime.now());
            }
            articleMapper.updateById(exist);
            return exist.getId();
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long articleId, Integer status) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) throw new NotFoundException("文章不存在");
        if (status == null || (status != 0 && status != 1)) {
            throw new com.mindman.common.exception.BadRequestException("状态值不合法");
        }
        a.setStatus(status);
        if (status == ArticleStatusEnum.PUBLISHED.getCode() && a.getPublishTime() == null) {
            a.setPublishTime(LocalDateTime.now());
        }
        articleMapper.updateById(a);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Article a = articleMapper.selectById(id);
        if (a == null) throw new NotFoundException("文章不存在");
        articleMapper.deleteById(id);
    }

    // ============== 私有方法 ==============

    private void applyDTO(Article a, ArticleSaveDTO dto) {
        a.setTitle(dto.getTitle());
        a.setCategoryId(dto.getCategoryId());
        a.setCover(dto.getCover());
        a.setSummary(dto.getSummary());
        a.setContent(dto.getContent());
        a.setTags(dto.getTags());
        a.setAuthor(StringUtils.hasText(dto.getAuthor()) ? dto.getAuthor() : "MindMan");
        if (dto.getStatus() != null) a.setStatus(dto.getStatus());
    }

    /** 将实体列表转为带分类名称的 VO */
    private List<ArticleVO> toVO(List<Article> articles) {
        if (articles.isEmpty()) return Collections.emptyList();

        // 一次性查出涉及的分类
        Set<Long> catIds = articles.stream()
                .map(Article::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = new HashMap<>();
        if (!catIds.isEmpty()) {
            List<ArticleCategory> cats = categoryMapper.selectBatchIds(catIds);
            for (ArticleCategory c : cats) nameMap.put(c.getId(), c.getName());
        }

        return articles.stream().map(a -> {
            ArticleVO vo = new ArticleVO();
            vo.setArticleId(a.getId());
            vo.setTitle(a.getTitle());
            vo.setCategoryId(a.getCategoryId());
            vo.setCategoryName(nameMap.getOrDefault(a.getCategoryId(), "未分类"));
            vo.setCover(a.getCover());
            vo.setSummary(a.getSummary());
            vo.setContent(a.getContent());
            vo.setTags(a.getTags());
            vo.setAuthor(a.getAuthor());
            vo.setReads(a.getReads());
            vo.setStatus(a.getStatus());
            vo.setPublishTime(a.getPublishTime());
            vo.setCreatedAt(a.getCreatedAt());
            vo.setUpdatedAt(a.getUpdatedAt());
            vo.setSourceType(a.getSourceType());
            vo.setSourceUrl(a.getSourceUrl());
            vo.setSourceName(a.getSourceName());
            return vo;
        }).collect(Collectors.toList());
    }
}