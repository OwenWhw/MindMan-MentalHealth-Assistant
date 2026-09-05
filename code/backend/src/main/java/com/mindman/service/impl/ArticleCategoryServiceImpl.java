package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindman.common.enums.ArticleStatusEnum;
import com.mindman.common.exception.BadRequestException;
import com.mindman.common.exception.NotFoundException;
import com.mindman.common.page.PageVO;
import com.mindman.dto.ArticleCategorySaveDTO;
import com.mindman.dto.ArticleCategoryVO;
import com.mindman.entity.Article;
import com.mindman.entity.ArticleCategory;
import com.mindman.mapper.ArticleCategoryMapper;
import com.mindman.mapper.ArticleMapper;
import com.mindman.service.ArticleCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分类服务实现
 */
@Service
@RequiredArgsConstructor
public class ArticleCategoryServiceImpl implements ArticleCategoryService {

    private final ArticleCategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<ArticleCategoryVO> listTree() {
        // 1. 取所有启用分类
        List<ArticleCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ArticleCategory>()
                        .eq(ArticleCategory::getStatus, 1)
                        .orderByAsc(ArticleCategory::getSortOrder)
                        .orderByAsc(ArticleCategory::getId)
        );
        if (categories.isEmpty()) return Collections.emptyList();

        // 2. 统计每个分类下的已发布文章数
        Map<Long, Long> countMap = countPublishedByCategory();

        // 3. 构树
        Map<Long, ArticleCategoryVO> map = new LinkedHashMap<>();
        for (ArticleCategory c : categories) {
            ArticleCategoryVO vo = new ArticleCategoryVO();
            vo.setCategoryId(c.getId());
            vo.setCategoryName(c.getName());
            vo.setDescription(c.getDescription());
            vo.setSortOrder(c.getSortOrder());
            vo.setStatus(c.getStatus());
            vo.setCreatedAt(c.getCreatedAt());
            vo.setUpdatedAt(c.getUpdatedAt());
            vo.setArticleCount(countMap.getOrDefault(c.getId(), 0L));
            vo.setChildren(new ArrayList<>());
            map.put(c.getId(), vo);
        }

        // 4. parent_id 拼装父子
        List<ArticleCategoryVO> roots = new ArrayList<>();
        for (ArticleCategory c : categories) {
            ArticleCategoryVO vo = map.get(c.getId());
            Long pid = c.getParentId();
            if (pid == null || pid == 0 || !map.containsKey(pid)) {
                roots.add(vo);
            } else {
                map.get(pid).getChildren().add(vo);
            }
        }
        return roots;
    }

    @Override
    public PageVO<ArticleCategoryVO> page(int page, int pageSize, String keyword, Integer status) {
        Page<ArticleCategory> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ArticleCategory> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ArticleCategory::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(ArticleCategory::getStatus, status);
        }
        wrapper.orderByAsc(ArticleCategory::getSortOrder)
               .orderByAsc(ArticleCategory::getId);
        Page<ArticleCategory> result = categoryMapper.selectPage(p, wrapper);

        Map<Long, Long> countMap = countPublishedByCategory();
        List<ArticleCategoryVO> records = result.getRecords().stream().map(c -> {
            ArticleCategoryVO vo = new ArticleCategoryVO();
            vo.setCategoryId(c.getId());
            vo.setCategoryName(c.getName());
            vo.setDescription(c.getDescription());
            vo.setSortOrder(c.getSortOrder());
            vo.setStatus(c.getStatus());
            vo.setCreatedAt(c.getCreatedAt());
            vo.setUpdatedAt(c.getUpdatedAt());
            vo.setArticleCount(countMap.getOrDefault(c.getId(), 0L));
            return vo;
        }).collect(Collectors.toList());
        return PageVO.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public ArticleCategoryVO detail(Long id) {
        ArticleCategory c = getOrThrow(id);
        ArticleCategoryVO vo = new ArticleCategoryVO();
        vo.setCategoryId(c.getId());
        vo.setCategoryName(c.getName());
        vo.setDescription(c.getDescription());
        vo.setSortOrder(c.getSortOrder());
        vo.setStatus(c.getStatus());
        vo.setCreatedAt(c.getCreatedAt());
        vo.setUpdatedAt(c.getUpdatedAt());
        // 当前分类的文章数
        Long cnt = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getCategoryId, id)
                        .eq(Article::getStatus, ArticleStatusEnum.PUBLISHED.getCode())
        );
        vo.setArticleCount(cnt);
        return vo;
    }

    @Override
    @Transactional
    public Long save(ArticleCategorySaveDTO dto) {
        // 同名校验
        Long exist = categoryMapper.selectCount(
                new LambdaQueryWrapper<ArticleCategory>()
                        .eq(ArticleCategory::getName, dto.getCategoryName())
        );
        if (exist != null && exist > 0) {
            throw new BadRequestException("分类名称已存在");
        }
        ArticleCategory c = new ArticleCategory();
        c.setName(dto.getCategoryName());
        c.setDescription(dto.getDescription());
        c.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        c.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        c.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        categoryMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional
    public void update(ArticleCategorySaveDTO dto) {
        ArticleCategory exist = getOrThrow(dto.getCategoryId());
        // 同名校验
        Long same = categoryMapper.selectCount(
                new LambdaQueryWrapper<ArticleCategory>()
                        .eq(ArticleCategory::getName, dto.getCategoryName())
                        .ne(ArticleCategory::getId, dto.getCategoryId())
        );
        if (same != null && same > 0) {
            throw new BadRequestException("分类名称已被使用");
        }
        exist.setName(dto.getCategoryName());
        exist.setDescription(dto.getDescription());
        if (dto.getSortOrder() != null) exist.setSortOrder(dto.getSortOrder());
        if (dto.getStatus() != null) exist.setStatus(dto.getStatus());
        categoryMapper.updateById(exist);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getOrThrow(id);
        // 有关联文章则禁止删除
        Long cnt = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getCategoryId, id)
        );
        if (cnt != null && cnt > 0) {
            throw new BadRequestException("该分类下还有 " + cnt + " 篇文章，请先移动或删除文章");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public ArticleCategory getOrThrow(Long id) {
        ArticleCategory c = categoryMapper.selectById(id);
        if (c == null) throw new NotFoundException("分类不存在");
        return c;
    }

    private Map<Long, Long> countPublishedByCategory() {
        // 查询已发布文章的 categoryId 并分组统计
        List<Map<String, Object>> rows = articleMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Article>()
                        .select("category_id, COUNT(*) AS cnt")
                        .eq("status", ArticleStatusEnum.PUBLISHED.getCode())
                        .isNotNull("category_id")
                        .groupBy("category_id")
        );
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Object cid = r.get("category_id");
            Object cnt = r.get("cnt");
            if (cid != null && cnt != null) {
                map.put(((Number) cid).longValue(), ((Number) cnt).longValue());
            }
        }
        return map;
    }
}