package pl.zagranietyper.wp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WpPost(
        long id,
        long author,
        String link,
        String slug,
        String date,
        @JsonProperty("date_gmt") String dateGmt,
        String modified,
        @JsonProperty("modified_gmt") String modifiedGmt,
        Rendered title,
        Rendered content
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rendered(String rendered) {
    }

    public String renderedTitle() {
        return title == null || title.rendered() == null ? "" : title.rendered();
    }

    public String renderedContent() {
        return content == null || content.rendered() == null ? "" : content.rendered();
    }
}
