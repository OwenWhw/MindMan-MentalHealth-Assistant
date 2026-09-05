package com.mindman.service;

import java.util.List;

/**
 * 实时心理文章爬虫服务。
 *
 * <p>调用方式：</p>
 * <ul>
 *   <li>{@link #crawlOnce(int)} 单次执行（管理员手动触发或测试用）</li>
 *   <li>定时任务由 {@code CrawlTask} 调用 {@link #crawlOnce(int)}</li>
 * </ul>
 */
public interface RealtimeCrawlerService {

    /**
     * 执行一次爬取，按关键词过滤后入库到「实时心理」分类。
     *
     * @param limit 期望入库条数
     * @return 实际入库条数
     */
    int crawlOnce(int limit);

    /**
     * 当前配置的爬取 URL 种子列表（公开展示用，便于后台维护）。
     */
    List<String> listSeeds();
}
