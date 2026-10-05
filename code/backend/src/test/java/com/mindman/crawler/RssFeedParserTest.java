package com.mindman.crawler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssFeedParserTest {

    private final RssFeedParser parser = new RssFeedParser();

    @Test
    void parsesRssTitleSummaryAndDateWithoutKeepingHtml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <guid>apa-1</guid>
                  <title>Stress and wellbeing</title>
                  <link>https://www.apa.org/news/example</link>
                  <description><![CDATA[<p>Research &amp; practice for wellbeing.</p>]]></description>
                  <pubDate>Wed, 30 Sep 2026 01:00:00 GMT</pubDate>
                </item></channel></rss>
                """;

        FeedItem item = parser.parse(xml, ZoneId.of("Asia/Shanghai")).get(0);

        assertEquals("Stress and wellbeing", item.title());
        assertEquals("Research & practice for wellbeing.", item.description());
        assertEquals("https://www.apa.org/news/example", item.link());
        assertEquals(LocalDateTime.of(2026, 9, 30, 9, 0), item.publishedAt());
    }

    @Test
    void parsesAtomAlternateLinkAndRejectsNonHttpsLinks() {
        String xml = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry><id>atom-1</id><title>Sleep and mood</title>
                    <link rel="self" href="https://example.org/feed/1"/>
                    <link rel="alternate" href="https://example.org/article/1"/>
                    <summary>Sleep quality affects mood.</summary>
                    <updated>2026-09-30T01:00:00Z</updated>
                  </entry>
                  <entry><id>http</id><title>Unsafe link</title><link href="http://example.org/article/2"/></entry>
                </feed>
                """;

        var items = parser.parse(xml, ZoneId.of("Asia/Shanghai"));

        assertEquals(1, items.size());
        assertEquals("https://example.org/article/1", items.get(0).link());
        assertEquals("2026-09-30T09:00", items.get(0).publishedAt().toString());
    }

    @Test
    void refusesXmlExternalEntityAndOversizedFeed() {
        String malicious = """
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&xxe;</title><link>https://example.org/</link></item></channel></rss>
                """;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(malicious));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("x".repeat(2 * 1024 * 1024 + 1)));
        assertTrue(parser.parse("").isEmpty());
    }
}
