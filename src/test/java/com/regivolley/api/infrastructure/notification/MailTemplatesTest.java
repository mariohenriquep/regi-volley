package com.regivolley.api.infrastructure.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Issue #40: the plain-text and HTML bodies, with every value escaped and nothing visitor-controlled in the subject. */
class MailTemplatesTest {

    private static final String HOSTILE = "<script>alert(1)</script> & \"quoted\" 'single'";

    private final MailTemplates templates = MailTemplates.load();

    @Test
    void everyNoticeHasASubjectATextAndAnHtmlBodyThatRenderWithTheirValues() {
        // Arrange
        Map<String, String> texts = Map.of("association", "Volley Club", "when", "12/10/2026 18:30", "reason", "Wet floor",
                "count", "3", "member", "Ana Silva", "expires", "19/10/2026 10:00");
        Map<String, String> links = Map.of("link", "https://app.example.org/activate#token=abc");

        for (NoticeKind kind : NoticeKind.values()) {
            // Act
            MailContent content = templates.render(kind, texts, links);

            // Assert
            assertThat(content.subject()).isNotBlank().doesNotContain("{{").doesNotContain("Volley Club");
            assertThat(content.text()).isNotBlank().doesNotContain("{{").doesNotContain("@@body@@");
            assertThat(content.html()).startsWith("<!DOCTYPE html>").doesNotContain("{{").doesNotContain("@@body@@");
        }
    }

    @Test
    void theHtmlBodyEscapesEveryValue() {
        // Arrange
        Map<String, String> texts = Map.of("association", HOSTILE, "when", "12/10/2026 18:30");

        // Act
        MailContent content = templates.render(NoticeKind.BOOKING_PROMOTED, texts, Map.of());

        // Assert
        assertThat(content.html()).doesNotContain("<script>").doesNotContain("alert(1)</script>");
        assertThat(content.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quoted&quot; &#39;single&#39;");
    }

    @Test
    void theTextBodyKeepsTheValueButDropsControlAndInvisibleCharacters() {
        // Arrange
        String name = "Volley\r\nBcc: evil‮ Club​";
        Map<String, String> texts = Map.of("association", name, "when", "12/10/2026 18:30");

        // Act
        MailContent content = templates.render(NoticeKind.BOOKING_PROMOTED, texts, Map.of());

        // Assert
        assertThat(content.text()).contains("Volley Bcc: evil Club");
        assertThat(content.text()).doesNotContain("‮").doesNotContain("​").doesNotContain("\r");
    }

    @Test
    void aVeryLongValueIsCut() {
        // Arrange
        Map<String, String> texts = Map.of("association", "x".repeat(5_000), "when", "12/10/2026 18:30");

        // Act
        MailContent content = templates.render(NoticeKind.BOOKING_PROMOTED, texts, Map.of());

        // Assert
        assertThat(content.text()).doesNotContain("x".repeat(301)).contains("x".repeat(100));
    }

    @Test
    void aValueThatLooksLikeAPlaceholderIsNotExpandedAgain() {
        // Arrange
        Map<String, String> texts = Map.of("association", "{{when}}", "when", "12/10/2026 18:30");

        // Act
        MailContent content = templates.render(NoticeKind.BOOKING_PROMOTED, texts, Map.of());

        // Assert
        assertThat(content.text()).contains("{{when}}");
    }

    @Test
    void aLinkIsEscapedInHtmlAndPrintedAsIsInText() {
        // Arrange
        String link = "https://app.example.org/activate?a=1&b=2#token=abc";
        Map<String, String> texts = Map.of("expires", "19/10/2026 10:00");

        // Act
        MailContent content = templates.render(NoticeKind.ACTIVATION, texts, Map.of("link", link));

        // Assert
        assertThat(content.text()).contains(link);
        assertThat(content.html()).contains("href=\"https://app.example.org/activate?a=1&amp;b=2#token=abc\"");
    }

    @Test
    void aLinkThatIsNotHttpIsRefusedForGood() {
        // Arrange
        Map<String, String> texts = Map.of("expires", "19/10/2026 10:00");
        Executable act = () -> templates.render(NoticeKind.ACTIVATION, texts, Map.of("link", "javascript:alert(1)"));

        // Act
        PermanentMailFailure refused = assertThrows(PermanentMailFailure.class, act);

        // Assert
        assertThat(refused.getMessage()).doesNotContain("alert");
    }

    @Test
    void aMissingValueIsAProgrammingErrorThatRetryingCannotCure() {
        // Arrange
        Executable act = () -> templates.render(NoticeKind.BOOKING_PROMOTED, Map.of("association", "Volley Club"), Map.of());

        // Act
        PermanentMailFailure failure = assertThrows(PermanentMailFailure.class, act);

        // Assert
        assertThat(failure.getMessage()).contains("when");
    }

    @Test
    void textThatLooksLikeALinkIsDefusedSoNoMailClientMakesItClickable() {
        // Arrange
        Map<String, String> texts = Map.of("association", "Win now at https://evil.example/claim or www.evil.example or mail me@evil.example",
                "when", "12/10/2026 18:30");

        // Act
        MailContent content = templates.render(NoticeKind.BOOKING_PROMOTED, texts, Map.of());

        // Assert
        assertThat(content.text()).doesNotContain("://").doesNotContain("www.").doesNotContain("me@");
        assertThat(content.html()).doesNotContain("://").doesNotContain("www.").doesNotContain("me@");
        assertThat(content.text()).contains("evil").contains("claim");
    }

    @Test
    void theLinkOfALinkMailKeepsItsSchemeAndHost() {
        // Arrange
        Map<String, String> texts = Map.of("expires", "19/10/2026 10:00");

        // Act
        MailContent content = templates.render(NoticeKind.ACTIVATION, texts, Map.of("link", "https://app.example.org/activate#token=abc"));

        // Assert
        assertThat(content.text()).contains("https://app.example.org/activate#token=abc");
    }

    @Test
    void theJoinNoticesTellAStrangerWhoDidNotAskToIgnoreThem() {
        // Arrange
        Map<String, String> texts = Map.of("association", "Volley Club");

        // Act
        MailContent approved = templates.render(NoticeKind.JOIN_APPROVED, texts, Map.of());
        MailContent rejected = templates.render(NoticeKind.JOIN_REJECTED, texts, Map.of());

        // Assert
        assertThat(approved.text()).contains("If you did not ask to join, ignore this message");
        assertThat(approved.html()).contains("If you did not ask to join, ignore this message");
        assertThat(rejected.text()).contains("If you did not ask to join, ignore this message");
        assertThat(rejected.html()).contains("If you did not ask to join, ignore this message");
    }
}
