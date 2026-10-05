package com.mindman.controller;

import com.mindman.common.exception.GlobalExceptionHandler;
import com.mindman.common.page.PageVO;
import com.mindman.dto.ArticleCategorySaveDTO;
import com.mindman.dto.ArticleCategoryVO;
import com.mindman.dto.ArticleVO;
import com.mindman.service.ArticleCategoryService;
import com.mindman.service.ArticleService;
import com.mindman.util.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class KnowledgeControllerApiTest {

    private final ArticleCategoryService categoryService = mock(ArticleCategoryService.class);
    private final ArticleService articleService = mock(ArticleService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new KnowledgeController(categoryService, articleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        LoginUser.set(42L, "test-user", "user");
    }

    @AfterEach
    void clearLoginContext() {
        LoginUser.clear();
    }

    @Test
    void categoryTreeIsAvailableToSignedInUser() throws Exception {
        ArticleCategoryVO category = new ArticleCategoryVO();
        category.setCategoryId(8L);
        category.setCategoryName("情绪管理");
        when(categoryService.listTree()).thenReturn(List.of(category));

        mockMvc.perform(get("/api/knowledge/category/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].categoryId").value(8))
                .andExpect(jsonPath("$.data[0].categoryName").value("情绪管理"));

        verify(categoryService).listTree();
    }

    @Test
    void regularUserCannotReadAdminCategoryPage() throws Exception {
        mockMvc.perform(get("/api/knowledge/category/page"))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(categoryService, articleService);
    }

    @Test
    void regularUserCannotCreateCategory() throws Exception {
        mockMvc.perform(post("/api/knowledge/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"测试分类\"}"))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(categoryService, articleService);
    }

    @Test
    void administratorCanCreateCategory() throws Exception {
        LoginUser.set(7L, "test-admin", "admin");
        when(categoryService.save(any(ArticleCategorySaveDTO.class))).thenReturn(88L);

        mockMvc.perform(post("/api/knowledge/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"测试分类\",\"description\":\"本地自动化测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(88));

        verify(categoryService).save(any(ArticleCategorySaveDTO.class));
        verifyNoInteractions(articleService);
    }

    @Test
    void articlePagePassesQueryToService() throws Exception {
        LoginUser.set(42L, "test-user", "user");
        ArticleVO article = new ArticleVO();
        article.setArticleId(31L);
        article.setTitle("正念练习");
        when(articleService.page(any())).thenReturn(PageVO.of(1, 1, 10, List.of(article)));

        mockMvc.perform(get("/api/knowledge/article/page")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("keyword", "正念"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].articleId").value(31))
                .andExpect(jsonPath("$.data.list[0].title").value("正念练习"));

        verify(articleService).page(any());
        verifyNoInteractions(categoryService);
    }
}
