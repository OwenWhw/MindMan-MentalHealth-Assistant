package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.service.RealtimeCrawlerService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/run")
    public R<Map<String, Object>> runOnce() {
        LoginUser.requireAdmin();
        int saved = crawler.crawlOnce(4);
        Map<String, Object> data = new HashMap<>();
        data.put("saved", saved);
        data.put("seeds", crawler.listSeeds());
        return R.ok(data);
    }

    @PostMapping("/seeds")
    public R<List<String>> seeds() {
        LoginUser.requireAdmin();
        return R.ok(crawler.listSeeds());
    }
}
