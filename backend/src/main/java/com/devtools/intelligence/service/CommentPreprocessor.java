package com.devtools.intelligence.service;

import com.devtools.intelligence.model.JiraTicketJson.Comment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Preprocesses a ticket's comment thread before it goes to the LLM.
 *
 * Two responsibilities:
 *
 * 1. Noise filtering — drops bot/automation comments that add no knowledge
 *    value. These inflate the context window and confuse the enrichment LLM.
 *
 * 2. Signal consolidation — extracts plain text from each remaining comment
 *    and assembles the thread into a single consolidated string that the
 *    enrichment prompt can reason over cleanly.
 *
 * The output of this class is passed directly into EnrichmentService as the
 * "comments" field — replacing what used to be the raw CSV comments column.
 */
@Service
@Slf4j
public class CommentPreprocessor {

    /**
     * Known bot/automation email patterns. Any comment whose author email
     * matches one of these is treated as a bot comment and dropped.
     * Extend this set as you discover new automation accounts in your org.
     */
    private static final Set<String> BOT_EMAIL_PATTERNS = Set.of(
            "automation@", "jira-bot@", "jenkins@", "gitlab-bot@", "nexus@", "confluence@",
            "noreply@", "no-reply@", "notify@", "bot@"
    );

    /**
     * Bot display name fragments. Comment is dropped if the author's
     * display name contains any of these (case-insensitive).
     */
    private static final Set<String> BOT_NAME_FRAGMENTS = Set.of(
            "automation", "jenkins", "bot", "gitlab bot", "jira"
    );

    /**
     * One-word or trivial acknowledgement patterns. A comment consisting
     * only of these words after trimming is dropped regardless of author.
     */
    private static final Set<String> TRIVIAL_PATTERNS = Set.of(
            "done", "+1", "noted", "thanks", "thank you", "ok", "okay",
            "acknowledged", "ack", "noted.", "thanks.", "done."
    );

    /**
     * Processes a comment list and returns a consolidated plain-text
     * thread suitable for feeding into the LLM enrichment prompt.
     *
     * Format per comment: "[Author]: text\n"
     * Bot comments are silently dropped.
     * Trivial comments are silently dropped.
     */
    public String process(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return "";
        }

        List<Comment> meaningful = comments.stream()
                .filter(c -> !isBot(c))
                .filter(c -> !isTrivial(c))
                .collect(Collectors.toList());

        int dropped = comments.size() - meaningful.size();
        if (dropped > 0) {
            log.debug("Filtered {} bot/trivial comments from thread of {}", dropped, comments.size());
        }

        return meaningful.stream()
                .map(this::formatComment)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Returns true if the comment author looks like a bot or automation account.
     */
    private boolean isBot(Comment comment) {
        if (comment.getAuthor() == null) return false;

        String email = comment.getAuthor().getEmailAddress();
        if (email != null) {
            String emailLower = email.toLowerCase();
            for (String pattern : BOT_EMAIL_PATTERNS) {
                if (emailLower.contains(pattern)) return true;
            }
        }

        String name = comment.getAuthor().getDisplayName();
        if (name != null) {
            String nameLower = name.toLowerCase();
            for (String fragment : BOT_NAME_FRAGMENTS) {
                if (nameLower.contains(fragment)) return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the comment body contains only trivial acknowledgement text.
     */
    private boolean isTrivial(Comment comment) {
        String text = extractText(comment);
        if (text.isBlank()) return true;

        String normalized = text.trim().toLowerCase()
                .replaceAll("[.,!?]+$", "") // strip trailing punctuation
                .trim();

        // Under 4 words and matches a trivial pattern
        if (normalized.split("\\s+").length <= 4) {
            return TRIVIAL_PATTERNS.contains(normalized);
        }

        return false;
    }

    /**
     * Extracts plain text from a comment's ADF body and prefixes it
     * with the author's display name for context in the LLM prompt.
     */
    private String formatComment(Comment comment) {
        String author = comment.getAuthor() != null
                ? comment.getAuthor().getDisplayName()
                : "Unknown";

        String text = extractText(comment);

        return "[" + author + "]: " + text;
    }

    private String extractText(Comment comment) {
        if (comment.getBody() == null) return "";
        return AdfExtractor.extractText(comment.getBody());
    }
}
