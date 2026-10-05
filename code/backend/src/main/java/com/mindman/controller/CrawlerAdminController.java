package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.config.CrawlerProperties;
import com.mindman.dto.CrawlRunResult;
import com.mindman.dto.CrawlerStatusVO;
import com.mindman.service.RealtimeCrawlerService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端 - 文章爬虫管理接口。
 */
@RestController
@RequestMapping("/api/admin/crawler")
@Tag(name = "爬虫管理")
@RequiredArgsConstructor
public class CrawlerAdminController {

    private final RealtimeCrawlerService crawler;
    private final CrawlerProperties properties;

    @PostMapping("/run")
    public R<Map<String, Object>> runOnce() {
        LoginUser.requireAdmin();
        CrawlRunResult result = crawler.crawlDetailed(properties.getDailyLimit(), "manual");
        Map<String, Object> data = new HashMap<>();
        data.put("saved", result.importedCount());
        data.put("seeds", crawler.listSeeds());
        data.put("run", result);
        return R.ok(data);
    }

    @GetMapping("/status")
    public R<CrawlerStatusVO> status() {
        LoginUser.requireAdmin();
        return R.ok(crawler.status());
    }

    @PostMapping("/seeds")
    public R<List<String>> seeds() {
        LoginUser.requireAdmin();
        return R.ok(crawler.listSeeds());
    }
}
