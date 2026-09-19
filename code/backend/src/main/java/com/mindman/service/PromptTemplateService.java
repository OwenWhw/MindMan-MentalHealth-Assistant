package com.mindman.service;

import com.mindman.entity.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板服务：模板 CRUD + 变量渲染 + 按场景取生效模板。
 */
public interface PromptTemplateService {

    /** 场景键常量：AI 咨询系统提示词 */
    String SCENE_CHAT_SYSTEM = "chat_system";
    /** 场景键常量：治愈语录生成 */
    String SCENE_QUOTE_GEN = "quote_gen";
    /** 场景键常量：文章推荐 */
    String SCENE_ARTICLE_RECOMMEND = "article_recommend";

    /** 全部模板（管理端列表） */
    List<PromptTemplate> listAll();

    /** 按场景取当前生效（enabled=1，updated_at 最新）的模板；无则返回 null */
    PromptTemplate getActiveByScene(String scene);

    /**
     * 渲染模板：将内容中的 {@code {变量名}} 占位符替换为 vars 中的值。
     *
     * @param scene       场景键
     * @param vars        变量键值对（可null）
     * @param fallbackRef 缺省模板供应器：场景无生效模板时返回内置默认提示词
     * @return 渲染后的提示词文本
     */
    String render(String scene, Map<String, String> vars, java.util.function.Supplier<String> fallbackRef);

    /** 新增/更新（id为null则新增，否则按id更新） */
    PromptTemplate save(PromptTemplate template);

    /** 启用某模板并停用同场景其它模板 */
    void enable(Long id);

    /** 删除模板 */
    void delete(Long id);
}
