package pl.zagranietyper.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import pl.zagranietyper.model.AuthorIdentity;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthorIdentityParser {

    private static final Pattern AUTHOR_SLUG_PATTERN =
            Pattern.compile(
                    "(?i)/author/([^/?#]+)/?"
            );

    private static final List<String> AUTHOR_LINK_SELECTORS =
            List.of(
                    "a[rel=author][href*=/author/]",
                    "[class*=author] a[href*=/author/]",
                    "a[href*=/author/]"
            );

    public AuthorIdentity parse(
            String html,
            String articleUrl
    ) {
        if (
                html == null
                        || html.isBlank()
        ) {
            return new AuthorIdentity(
                    null,
                    null
            );
        }

        Document document =
                Jsoup.parse(
                        html,
                        articleUrl
                );

        for (
                String selector :
                AUTHOR_LINK_SELECTORS
        ) {
            for (
                    Element link :
                    document.select(
                            selector
                    )
            ) {
                String href =
                        link.attr(
                                "href"
                        );

                String slug =
                        extractSlug(
                                href
                        );

                if (slug == null) {
                    continue;
                }

                String displayName =
                        normalizeText(
                                link.text()
                        );

                if (
                        displayName == null
                ) {
                    displayName =
                            extractMetaAuthor(
                                    document
                            );
                }

                return new AuthorIdentity(
                        displayName,
                        slug
                );
            }
        }

        return new AuthorIdentity(
                extractMetaAuthor(
                        document
                ),
                null
        );
    }

    private static String extractMetaAuthor(
            Document document
    ) {
        Element meta =
                document.selectFirst(
                        "meta[name=author]"
                );

        if (meta == null) {
            return null;
        }

        return normalizeText(
                meta.attr(
                        "content"
                )
        );
    }

    private static String extractSlug(
            String href
    ) {
        if (
                href == null
                        || href.isBlank()
        ) {
            return null;
        }

        Matcher matcher =
                AUTHOR_SLUG_PATTERN
                        .matcher(
                                href
                        );

        if (!matcher.find()) {
            return null;
        }

        return URLDecoder.decode(
                matcher.group(1),
                StandardCharsets.UTF_8
        );
    }

    private static String normalizeText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value
                        .replace(
                                '\u00A0',
                                ' '
                        )
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        return normalized.isBlank()
                ? null
                : normalized;
    }
}