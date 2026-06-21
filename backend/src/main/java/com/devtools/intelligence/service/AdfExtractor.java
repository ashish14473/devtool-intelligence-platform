package com.devtools.intelligence.service;

import com.devtools.intelligence.model.JiraTicketJson.AdfNode;

/**
 * Extracts plain text from an Atlassian Document Format (ADF) node tree.
 *
 * ADF is a recursive JSON structure Jira uses for rich-text fields like
 * comment bodies. Our sample data uses the minimal shape:
 *
 *   doc -> paragraph -> text (with a "text" string leaf)
 *
 * Real Jira responses can also contain: codeBlock, bulletList, listItem,
 * heading, mention, emoji, hardBreak, etc. This extractor handles all of
 * them gracefully via recursive traversal — for any node type it doesn't
 * recognise it just recurses into its children rather than failing.
 *
 * Output: plain text with paragraph breaks preserved as newlines.
 */
public class AdfExtractor {

    private AdfExtractor() {}

    /**
     * Entry point. Returns empty string if node is null.
     */
    public static String extractText(AdfNode node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        extract(node, sb);
        return sb.toString().trim();
    }

    private static void extract(AdfNode node, StringBuilder sb) {
        if (node == null) return;

        String type = node.getType() == null ? "" : node.getType();

        switch (type) {
            case "text":
                // Leaf node — the actual text content
                if (node.getText() != null && !node.getText().isBlank()) {
                    sb.append(node.getText());
                }
                break;

            case "hardBreak":
                sb.append("\n");
                break;

            case "paragraph":
            case "bulletList":
            case "orderedList":
            case "listItem":
            case "blockquote":
            case "heading":
            case "doc":
                // Container nodes — recurse into children, add newline after block elements
                if (node.getContent() != null) {
                    for (AdfNode child : node.getContent()) {
                        extract(child, sb);
                    }
                }
                // Add newline after block-level containers to preserve readability
                if (!type.equals("listItem")) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append("\n");
                    }
                }
                break;

            case "codeBlock":
                // Code blocks — include content but mark as code context
                if (node.getContent() != null) {
                    for (AdfNode child : node.getContent()) {
                        extract(child, sb);
                    }
                }
                sb.append("\n");
                break;

            case "mention":
            case "emoji":
            case "inlineCard":
            case "media":
            case "mediaSingle":
                // Skip — mentions and media add noise, not signal
                break;

            default:
                // Unknown node type — recurse into children defensively
                // so future ADF additions don't cause data loss
                if (node.getContent() != null) {
                    for (AdfNode child : node.getContent()) {
                        extract(child, sb);
                    }
                }
                break;
        }
    }
}
