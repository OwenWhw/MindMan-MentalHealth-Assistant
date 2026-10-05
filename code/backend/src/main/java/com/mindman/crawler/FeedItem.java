package com.mindman.crawler;

import java.time.LocalDateTime;

/** A single metadata record published by a trusted RSS/Atom feed. */
public record FeedItem(
        String guid,
        String title,
        String link,
        String description,
        String author,
        LocalDateTime publishedAt
) {}
