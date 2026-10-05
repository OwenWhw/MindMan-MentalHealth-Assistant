package com.mindman.service.impl;

import com.mindman.common.enums.ArticleStatusEnum;
import com.mindman.common.exception.NotFoundException;
import com.mindman.dto.ArticleQueryDTO;
import com.mindman.dto.ArticleVO;
import com.mindman.entity.Article;
import com.mindman.mapper.ArticleCategoryMapper;
import com.mindman.mapper.ArticleMapper;
import com.mindman.util.LoginUser;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleServiceImplTest {

    @BeforeAll
    static void initializeArticleLambdaMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "article-test");
        assistant.setCurrentNamespace("article-test");
        TableInfoHelper.initTableInfo(assistant, Article.class);
    }

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final ArticleCategoryMapper categoryMapper = mock(ArticleCategoryMapper.class);
    private final ArticleServiceImpl articleService = new ArticleServiceImpl(articleMapper, categoryMapper);

    @AfterEach
    void clearLoginUser() {
        LoginUser.clear();
    }

    @Test
    void publicArticleQueriesCannotRequestDrafts() {
        LoginUser.set(9L, "reader", "user");
        assertEquals("user", LoginUser.role());
        when(articleMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArticleQueryDTO query = new ArticleQueryDTO();
        query.setStatus(0);

        articleService.page(query);

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<Article>> wrapper = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(articleMapper).selectPage(any(Page.class), wrapper.capture());
        wrapper.getValue().getSqlSegment();
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(ArticleStatusEnum.PUBLISHED.getCode()));
        org.junit.jupiter.api.Assertions.assertFalse(wrapper.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void publicUserCannotOpenDraftArticle() {
        LoginUser.set(9L, "reader", "user");
        Article draft = new Article();
        draft.setId(99L);
        draft.setStatus(0);
        when(articleMapper.selectById(99L)).thenReturn(draft);

        assertThrows(NotFoundException.class, () -> articleService.detail(99L));
        verify(articleMapper, never()).update(any(), any());
    }

    @Test
    void administratorsCanStillFilterDraftArticles() {
        LoginUser.set(1L, "admin", "admin");
        assertEquals("admin", LoginUser.role());
        when(articleMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArticleQueryDTO query = new ArticleQueryDTO();
        query.setStatus(0);

        articleService.page(query);

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<Article>> wrapper = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(articleMapper).selectPage(any(Page.class), wrapper.capture());
        wrapper.getValue().getSqlSegment();
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void selectedArticleMustBePublished() {
        Article draft = new Article();
        draft.setId(77L);
        draft.setStatus(0);
        when(articleMapper.selectById(77L)).thenReturn(draft);

        assertNull(articleService.publishedReference(77L));
        verify(categoryMapper, never()).selectBatchIds(any());
    }

    @Test
    void publishedReferenceReturnsContentWithoutIncreasingReadCount() {
        Article published = new Article();
        published.setId(77L);
        published.setStatus(ArticleStatusEnum.PUBLISHED.getCode());
        published.setTitle("认识压力反应");
        published.setSummary("压力会带来身心变化");
        published.setContent("觉察身体信号可以帮助我们照顾自己。");
        published.setReads(12L);
        when(articleMapper.selectById(77L)).thenReturn(published);

        ArticleVO reference = articleService.publishedReference(77L);

        assertEquals("认识压力反应", reference.getTitle());
        assertEquals("觉察身体信号可以帮助我们照顾自己。", reference.getContent());
        assertEquals(12L, reference.getReads());
        verify(articleMapper, never()).update(any(), any());
    }

    @Test
    void articleRecommendationsSearchPublishedTextFieldsAndApplySafeLimit() {
        Article article = new Article();
        article.setId(21L);
        article.setStatus(ArticleStatusEnum.PUBLISHED.getCode());
        article.setTitle("认识焦虑与压力反应");
        article.setSummary("从身体信号开始识别压力");
        article.setReads(4L);
        when(articleMapper.selectList(any())).thenReturn(java.util.List.of(article));

        var result = articleService.recommendPublished(java.util.List.of("焦虑"), 99);

        assertEquals(1, result.size());
        assertEquals("认识焦虑与压力反应", result.get(0).getTitle());
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<Article>> wrapper = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(articleMapper).selectList(wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("LIMIT 5"));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue("%焦虑%"));
    }
}
