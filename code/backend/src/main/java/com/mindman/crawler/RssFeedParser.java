package com.mindman.crawler;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses RSS 2.0 and Atom metadata only; it never downloads linked article pages. */
public final class RssFeedParser {

    private static final Pattern TAGS = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>|<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);");

    public List<FeedItem> parse(String xml) {
        return parse(xml, ZoneId.of("Asia/Shanghai"));
    }

    public List<FeedItem> parse(String xml, ZoneId zone) {
        if (xml == null || xml.isBlank()) return List.of();
        if (xml.length() > 2 * 1024 * 1024) throw new IllegalArgumentException("RSS feed exceeds 2 MB limit");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            NodeList rssItems = document.getElementsByTagName("item");
            List<FeedItem> items = new ArrayList<>();
            if (rssItems.getLength() > 0) {
                for (int i = 0; i < rssItems.getLength(); i++) {
                    Element element = (Element) rssItems.item(i);
                    items.add(new FeedItem(
                            clean(text(element, "guid")),
                            clean(text(element, "title")),
                            clean(text(element, "link")),
                            plainText(firstNonBlank(text(element, "description"), text(element, "encoded"))),
                            clean(firstNonBlank(text(element, "creator"), text(element, "author"))),
                            parseDate(firstNonBlank(text(element, "pubDate"), text(element, "date")), zone)
                    ));
                }
            } else {
                NodeList atomEntries = document.getElementsByTagNameNS("*", "entry");
                for (int i = 0; i < atomEntries.getLength(); i++) {
                    Element element = (Element) atomEntries.item(i);
                    items.add(new FeedItem(
                            clean(text(element, "id")),
                            clean(text(element, "title")),
                            atomLink(element),
                            plainText(firstNonBlank(text(element, "summary"), text(element, "content"))),
                            clean(text(element, "name")),
                            parseDate(firstNonBlank(text(element, "published"), text(element, "updated")), zone)
                    ));
                }
            }
            return items.stream()
                    .filter(item -> !blank(item.title()) && validHttpsUrl(item.link()))
                    .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("RSS/Atom feed XML is invalid", e);
        }
    }

    private static String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagNameNS("*", "link");
        String fallback = null;
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            if (!(node instanceof Element link)) continue;
            String href = link.getAttribute("href");
            if (blank(href)) continue;
            if (fallback == null) fallback = href;
            if ("alternate".equalsIgnoreCase(link.getAttribute("rel")) || blank(link.getAttribute("rel"))) return href;
        }
        return fallback;
    }

    private static String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) nodes = parent.getElementsByTagName(localName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    private static LocalDateTime parseDate(String value, ZoneId zone) {
        if (blank(value)) return null;
        String trimmed = value.trim();
        try {
            return ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(zone).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(trimmed).atZoneSameInstant(zone).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.ofInstant(Instant.parse(trimmed), zone);
                } catch (DateTimeParseException ignoredThird) {
                    return null;
                }
            }
        }
    }

    private static String plainText(String value) {
        if (blank(value)) return "";
        String withoutTags = TAGS.matcher(value).replaceAll(" ");
        Matcher matcher = ENTITY.matcher(withoutTags);
        StringBuffer decoded = new StringBuffer();
        while (matcher.find()) {
            String entity = matcher.group(1);
            String replacement = decodeEntity(entity);
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(decoded);
        return clean(decoded.toString());
    }

    private static String decodeEntity(String entity) {
        String lower = entity.toLowerCase(Locale.ROOT);
        if (lower.startsWith("#x")) {
            try { return new String(Character.toChars(Integer.parseInt(entity.substring(2), 16))); }
            catch (RuntimeException ignored) { return " "; }
        }
        if (lower.startsWith("#")) {
            try { return new String(Character.toChars(Integer.parseInt(entity.substring(1)))); }
            catch (RuntimeException ignored) { return " "; }
        }
        return switch (lower) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            case "ndash", "mdash" -> "-";
            case "rsquo", "lsquo" -> "'";
            case "rdquo", "ldquo" -> "\"";
            default -> " ";
        };
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String firstNonBlank(String first, String second) {
        return !blank(first) ? first : second;
    }

    private static boolean validHttpsUrl(String value) {
        if (blank(value)) return false;
        try { return "https".equalsIgnoreCase(URI.create(value.trim()).getScheme()) && URI.create(value.trim()).getHost() != null; }
        catch (RuntimeException ignored) { return false; }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
