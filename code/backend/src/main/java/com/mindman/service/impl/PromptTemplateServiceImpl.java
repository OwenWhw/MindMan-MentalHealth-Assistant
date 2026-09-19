package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindman.entity.PromptTemplate;
import com.mindman.mapper.PromptTemplateMapper;
import com.mindman.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 提示词模板服务实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li>同一场景仅一条生效模板：启用时先把同场景其它模板置为停用（事务性由单条 UPDATE 保证近似）</li>
 *   <li>渲染用简单 {@code {key}} 占位符替换，不引入模板引擎依赖</li>
 *   <li>查询走缓存友好路径：每次按 scene + enabled 查询，数据量极小（个位数行），无需额外缓存</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper mapper;

    @Override
    public List<PromptTemplate> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                .orderByAsc(PromptTemplate::getScene)
                .orderByDesc(PromptTemplate::getUpdatedAt));
    }

    @Override
    public PromptTemplate getActiveByScene(String scene) {
        return mapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getScene, scene)
                .eq(PromptTemplate::getEnabled, 1)
                .orderByDesc(PromptTemplate::getUpdatedAt)
                .last("LIMIT 1"));
    }

    @Override
    public String render(String scene, Map<String, String> vars, Supplier<String> fallbackRef) {
        PromptTemplate t = getActiveByScene(scene);
        String template = t != null && t.getTemplate() != null && !t.getTemplate().isBlank()
                ? t.getTemplate() : (fallbackRef != null ? fallbackRef.get() : "");
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                template = template.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return template;
    }

    @Override
    public PromptTemplate save(PromptTemplate t) {
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getId() == null) {
            mapper.insert(t);
        } else {
            mapper.updateById(t);
        }
        // 新保存且标记启用的模板：停用同场景其它模板，保证"单场景单生效"
        if (t.getEnabled() == 1) {
            enable(t.getId());
        }
        return t;
    }

    @Override
    public void enable(Long id) {
        PromptTemplate t = mapper.selectById(id);
        if (t == null) {
            throw new IllegalArgumentException("模板不存在: id=" + id);
        }
        // 停用同场景其它模板
        PromptTemplate off = new PromptTemplate();
        off.setEnabled(0);
        mapper.update(off, new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getScene, t.getScene())
                .ne(PromptTemplate::getId, id));
        t.setEnabled(1);
        mapper.updateById(t);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
