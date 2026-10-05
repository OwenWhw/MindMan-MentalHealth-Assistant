package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindman.config.CrawlerProperties;
import com.mindman.crawler.FeedItem;
import com.mindman.crawler.RssFeedParser;
import com.mindman.dto.CrawlRunResult;
import com.mindman.dto.CrawlerFeedInfo;
import com.mindman.dto.CrawlerStatusVO;
import com.mindman.entity.Article;
import com.mindman.entity.CrawlRunLog;
import com.mindman.entity.CrawlFeedState;
import com.mindman.mapper.ArticleMapper;
import com.mindman.mapper.CrawlFeedStateMapper;
import com.mindman.mapper.CrawlRunLogMapper;
import com.mindman.service.RealtimeCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Syncs source-provided metadata from APA RSS feeds; it never fabricates or republishes full articles. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeCrawlerImpl implements RealtimeCrawlerService {

    private static final long CATEGORY_LIVE = 7L;
    private static final String SOURCE_TYPE = "crawled";
    private static final int DESCRIPTION_LIMIT = 1800;
    private static final List<Feed> FEEDS = List.of(
            new Feed("APA 心理学新闻", "https://www.apa.org/news/press/releases/press-release-rss.xml", false),
            new Feed("APA PsycPORT 心理资讯", "https://www.apa.org/news/psycport/psycport-rss.xml", true)
    );
    private static final List<String> RELEVANCE_TERMS = List.of(
            "psycholog", "mental health", "anxiety", "stress", "emotion", "mood", "relationship",
            "loneliness", "sleep", "well-being", "wellbeing", "depression", "grief", "trauma",
            "resilience", "burnout", "coping", "therapy", "brain", "memory", "addiction", "autism",
            "suicide", "distress", "fear", "behavior", "behaviour", "social support", "workplace",
            "children", "adolescent", "心理", "情绪", "焦虑", "压力", "睡眠", "关系", "抑郁"
    );

    private final ArticleMapper articleMapper;
    private final CrawlRunLogMapper runLogMapper;
    private final CrawlFeedStateMapper feedStateMapper;
    private final WebClient.Builder webClientBuilder;
    private final CrawlerProperties properties;
    private final RssFeedParser parser = new RssFeedParser();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public int crawlOnce(int limit) {
        return crawlDetailed(limit, "manual").importedCount();
    }

    @Override
    public CrawlRunResult crawlDetailed(int limit, String triggerType) {
        if (!properties.isEnabled()) return result("DISABLED", triggerType, null, null, 0, 0, 0, 0, 0, 0, 0, "自动同步已关闭", List.of());
        if (!running.compareAndSet(false, true)) return result("SKIPPED", triggerType, null, null, 0, 0, 0, 0, 0, 0, 0, "已有同步任务正在运行", List.of());

        LocalDateTime startedAt = now();
        CrawlRunLog run = new CrawlRunLog();
        run.setTriggerType(normalizeTrigger(triggerType));
        run.setStatus("RUNNING");
        run.setStartedAt(startedAt);
        run.setSourceCount(FEEDS.size());
        run.setFetchedCount(0);
        run.setImportedCount(0);
        run.setUpdatedCount(0);
        run.setDuplicateCount(0);
        run.setNotModifiedCount(0);
        run.setSkippedCount(0);
        run.setFilteredCount(0);
        run.setFailedCount(0);
        Counters counters = new Counters();
        List<String> errors = new ArrayList<>();
        int itemLimit = Math.max(1, Math.min(limit, Math.max(1, properties.getDailyLimit())));
        try {
            runLogMapper.insert(run);
            for (Feed feed : FEEDS) {
                if (counters.imported >= itemLimit) break;
                try {
                    syncFeed(feed, itemLimit - counters.imported, counters);
                } catch (Exception e) {
                    counters.failed++;
                    String message = feed.name + "：" + safeMessage(e);
                    errors.add(message);
                    log.warn("RSS feed sync failed: {}", message);
                }
            }
        } catch (Exception e) {
            counters.failed++;
            errors.add("同步任务：" + safeMessage(e));
            log.error("RSS sync run failed before completion: {}", safeMessage(e));
        } finally {
            LocalDateTime finishedAt = now();
            String status = counters.failed == 0 ? "SUCCESS"
                    : counters.imported + counters.updated + counters.duplicate + counters.notModified > 0 ? "PARTIAL" : "FAILED";
            run.setStatus(status);
            run.setFinishedAt(finishedAt);
            run.setFetchedCount(counters.fetched);
            run.setImportedCount(counters.imported);
            run.setUpdatedCount(counters.updated);
            run.setDuplicateCount(counters.duplicate);
            run.setNotModifiedCount(counters.notModified);
            run.setSkippedCount(counters.skipped);
            run.setFilteredCount(counters.filtered);
            run.setFailedCount(counters.failed);
            run.setErrorSummary(errors.isEmpty() ? null : String.join("；", errors).substring(0, Math.min(900, String.join("；", errors).length())));
            try {
                if (run.getId() != null) runLogMapper.updateById(run);
            } catch (Exception e) {
                log.error("Could not persist RSS sync run result: {}", safeMessage(e));
            } finally {
                running.set(false);
            }
        }

        List<String> errorCopy = List.copyOf(errors);
        return new CrawlRunResult(run.getId(), run.getTriggerType(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
                run.getSourceCount(), run.getFetchedCount(), run.getImportedCount(), run.getUpdatedCount(), run.getDuplicateCount(),
                run.getNotModifiedCount(), run.getSkippedCount(), run.getFilteredCount(), run.getFailedCount(), run.getErrorSummary(), errorCopy);
    }

    private void syncFeed(Feed feed, int remainingLimit, Counters counters) {
        CrawlFeedState state = feedStateMapper.selectById(feed.url);
        LocalDateTime checkedAt = state == null ? null : state.getLastCheckedAt();
        long minHours = Math.max(0, properties.getMinCheckIntervalHours());
        if (checkedAt != null && checkedAt.isAfter(now().minusHours(minHours))) {
            counters.skipped++;
            return;
        }

        FeedResponse response = fetch(feed.url, state);
        if (response == null) throw new IllegalStateException("RSS 来源没有返回响应");
        LocalDateTime checkTime = now();
        if (response.statusCode == 304) {
            counters.notModified++;
            saveFeedState(feed, state, response, checkTime, state == null ? 0 : state.getLastItemCount());
            return;
        }

        List<FeedItem> items = parser.parse(response.body, ZoneId.of(properties.getZone()));
        int accepted = 0;
        for (FeedItem item : items) {
            int feedLimit = Math.max(1, properties.getPerFeedLimit());
            if (counters.imported >= properties.getDailyLimit() || accepted >= Math.min(remainingLimit, feedLimit)) break;
            counters.fetched++;
            if (!feed.includeAll && !isRelevant(item)) {
                counters.filtered++;
                continue;
            }
            LocalDateTime publishedAt = item.publishedAt() == null ? checkTime : item.publishedAt();
            if (publishedAt.isBefore(checkTime.minusDays(Math.max(1, properties.getMaxAgeDays())))) {
                counters.filtered++;
                continue;
            }
            String url = canonicalize(item.link());
            Article existing = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                    .eq(Article::getSourceType, SOURCE_TYPE)
                    .eq(Article::getSourceUrl, url)
                    .last("LIMIT 1"));
            if (existing != null) {
                if (updateIfChanged(existing, item, feed, item.publishedAt())) counters.updated++;
                else counters.duplicate++;
                accepted++;
                continue;
            }
            Article article = toArticle(item, feed, url, publishedAt);
            articleMapper.insert(article);
            counters.imported++;
            accepted++;
        }
        saveFeedState(feed, state, response, checkTime, items.size());
    }

    private FeedResponse fetch(String url, CrawlFeedState state) {
        WebClient client = webClientBuilder.clone()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        return client.get().uri(url)
                .header(HttpHeaders.USER_AGENT, "MindManArticleIndex/1.0 (+https://www.apa.org/rss)")
                .headers(headers -> {
                    if (state != null && StringUtils.hasText(state.getEtag())) headers.setIfNoneMatch(state.getEtag());
                    if (state != null && StringUtils.hasText(state.getLastModified())) headers.set(HttpHeaders.IF_MODIFIED_SINCE, state.getLastModified());
                })
                .exchangeToMono(response -> toResponse(response))
                .timeout(Duration.ofSeconds(Math.max(3, properties.getRequestTimeoutSeconds())))
                .block();
    }

    private Mono<FeedResponse> toResponse(ClientResponse response) {
        int status = response.statusCode().value();
        String etag = response.headers().asHttpHeaders().getETag();
        String modified = response.headers().asHttpHeaders().getFirst(HttpHeaders.LAST_MODIFIED);
        if (status == 304) return Mono.just(new FeedResponse(status, "", etag, modified));
        if (!response.statusCode().is2xxSuccessful()) return response.createException().flatMap(Mono::error);
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> new FeedResponse(status, body, etag, modified));
    }

    private void saveFeedState(Feed feed, CrawlFeedState state, FeedResponse response, LocalDateTime checkedAt, int itemCount) {
        CrawlFeedState saved = state == null ? new CrawlFeedState() : state;
        saved.setFeedUrl(feed.url);
        saved.setFeedName(feed.name);
        saved.setEtag(StringUtils.hasText(response.etag) ? response.etag : saved.getEtag());
        saved.setLastModified(StringUtils.hasText(response.lastModified) ? response.lastModified : saved.getLastModified());
        saved.setLastCheckedAt(checkedAt);
        saved.setLastSuccessfulAt(checkedAt);
        saved.setLastItemCount(itemCount);
        if (state == null) feedStateMapper.insert(saved);
        else feedStateMapper.updateById(saved);
    }

    private boolean updateIfChanged(Article article, FeedItem item, Feed feed, LocalDateTime publishedAt) {
        String summary = clip(item.description(), 500);
        String content = clip(item.description(), DESCRIPTION_LIMIT);
        String sourceName = sourceDisplayName(feed, item);
        boolean changed = !equals(article.getTitle(), clip(item.title(), 255))
                || !equals(article.getSummary(), summary)
                || !equals(article.getContent(), content)
                || !equals(article.getSourceName(), sourceName)
                || !equals(article.getTags(), tagsJson(item.title() + " " + item.description()))
                || (publishedAt != null && !publishedAt.equals(article.getPublishTime()));
        if (!changed) return false;
        article.setTitle(clip(item.title(), 255));
        article.setSummary(summary);
        article.setContent(content);
        article.setTags(tagsJson(item.title() + " " + item.description()));
        article.setEmotionTags(String.join(",", tags(item.title() + " " + item.description())));
        article.setSourceName(sourceName);
        if (publishedAt != null) article.setPublishTime(publishedAt);
        // Keep moderation status and author edits intact while refreshing source metadata.
        articleMapper.updateById(article);
        return true;
    }

    private Article toArticle(FeedItem item, Feed feed, String url, LocalDateTime publishedAt) {
        String text = item.title() + " " + item.description();
        Article article = new Article();
        article.setCategoryId(CATEGORY_LIVE);
        article.setTitle(clip(item.title(), 255));
        article.setSummary(clip(item.description(), 500));
        article.setContent(clip(item.description(), DESCRIPTION_LIMIT));
        article.setTags(tagsJson(text));
        article.setEmotionTags(String.join(",", tags(text)));
        article.setAuthor(StringUtils.hasText(item.author()) ? clip(item.author(), 64) : "American Psychological Association");
        article.setReads(0L);
        article.setStatus(1);
        article.setPublishTime(publishedAt);
        article.setSourceType(SOURCE_TYPE);
        article.setSourceUrl(url);
        article.setSourceName(sourceDisplayName(feed, item));
        return article;
    }

    private static String sourceDisplayName(Feed feed, FeedItem item) {
        if (!feed.includeAll) return "American Psychological Association (APA)";
        try {
            String host = URI.create(item.link()).getHost();
            if (host != null && host.toLowerCase(Locale.ROOT).startsWith("www.")) host = host.substring(4);
            if (StringUtils.hasText(host)) return host + " · APA PsycPORT";
        } catch (RuntimeException ignored) {
            // Keep the trustworthy feed identity if an item URL cannot be parsed here.
        }
        return feed.name;
    }

    private static boolean isRelevant(FeedItem item) {
        String text = (item.title() + " " + item.description()).toLowerCase(Locale.ROOT);
        return RELEVANCE_TERMS.stream().anyMatch(text::contains);
    }

    private static List<String> tags(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        if (contains(value, "anxiety", "焦虑", "worry", "fear")) tags.add("焦虑");
        if (contains(value, "stress", "burnout", "workplace", "压力")) tags.add("压力");
        if (contains(value, "sleep", "失眠")) tags.add("睡眠");
        if (contains(value, "relationship", "loneliness", "social support", "关系", "孤独")) tags.add("人际关系");
        if (contains(value, "emotion", "mood", "grief", "depression", "情绪", "抑郁")) tags.add("情绪");
        if (contains(value, "mental health", "psycholog", "心理")) tags.add("心理健康");
        return tags.stream().limit(4).toList();
    }

    private static String tagsJson(String text) {
        List<String> values = tags(text);
        return values.isEmpty() ? "[]" : "[\"" + String.join("\",\"", values) + "\"]";
    }

    private static boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static String canonicalize(String raw) {
        URI uri = URI.create(raw.trim()).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("来源链接不是有效的 HTTPS 地址");
        }
        String query = uri.getRawQuery();
        if (query != null) {
            query = java.util.Arrays.stream(query.split("&"))
                    .filter(part -> {
                        String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                        return !key.startsWith("utm_") && !List.of("fbclid", "gclid", "mc_cid", "mc_eid").contains(key);
                    }).collect(java.util.stream.Collectors.joining("&"));
            if (query.isBlank()) query = null;
        }
        try {
            return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), uri.getPath(), query, null).toASCIIString();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法规范化来源链接", e);
        }
    }

    @Override
    public List<String> listSeeds() {
        return FEEDS.stream().map(feed -> "[APA RSS] " + feed.name + ": " + feed.url).toList();
    }

    @Override
    public CrawlerStatusVO status() {
        List<CrawlerFeedInfo> feeds = FEEDS.stream().map(feed -> {
            CrawlFeedState state = feedStateMapper.selectById(feed.url);
            return new CrawlerFeedInfo(feed.name, feed.url, properties.isEnabled(), state == null ? null : state.getLastSuccessfulAt());
        }).toList();
        List<CrawlRunLog> logs = runLogMapper.selectList(new LambdaQueryWrapper<CrawlRunLog>()
                .orderByDesc(CrawlRunLog::getStartedAt).last("LIMIT 10"));
        List<CrawlRunResult> recent = logs.stream().map(this::toResult).toList();
        return new CrawlerStatusVO(properties.isEnabled(), running.get(), properties.getCron(), properties.getZone(), feeds,
                recent.isEmpty() ? null : recent.get(0), recent);
    }

    private CrawlRunResult toResult(CrawlRunLog run) {
        return new CrawlRunResult(run.getId(), run.getTriggerType(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
                value(run.getSourceCount()), value(run.getFetchedCount()), value(run.getImportedCount()), value(run.getUpdatedCount()),
                value(run.getDuplicateCount()), value(run.getNotModifiedCount()), value(run.getSkippedCount()), value(run.getFilteredCount()),
                value(run.getFailedCount()), run.getErrorSummary(), run.getErrorSummary() == null ? List.of() : List.of(run.getErrorSummary()));
    }

    private static CrawlRunResult result(String status, String trigger, LocalDateTime start, LocalDateTime end,
                                         int sources, int fetched, int imported, int updated, int duplicate, int notModified,
                                         int skipped, String message, List<String> errors) {
        return new CrawlRunResult(null, normalizeTrigger(trigger), status, start, end, sources, fetched, imported, updated,
                duplicate, notModified, skipped, 0, errors.size(), message, errors);
    }

    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of(properties.getZone())); }
    private static String normalizeTrigger(String value) { return "scheduled".equalsIgnoreCase(value) ? "scheduled" : "manual"; }
    private static int value(Integer value) { return value == null ? 0 : value; }
    private static String clip(String text, int max) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }
    private static boolean equals(String a, String b) { return java.util.Objects.equals(a, b); }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) return e.getClass().getSimpleName();
        String safe = message.replaceAll("[\\r\\n]+", " ");
        return safe.substring(0, Math.min(220, safe.length()));
    }

    private record Feed(String name, String url, boolean includeAll) {}
    private record FeedResponse(int statusCode, String body, String etag, String lastModified) {}
    private static final class Counters {
        int fetched, imported, updated, duplicate, notModified, skipped, filtered, failed;
    }
}
