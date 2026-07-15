package com.techpulse.techradar.features.company.application;

/**
 * The crawler sometimes appends a badge line to the company name (e.g.
 * "Công ty Cổ phần MISA\nPro Company"), which occasionally creates a second Company node for what
 * is really the same company. Not worth de-duplicating the graph for; stripping it for display
 * is enough since the first line is always the real name.
 */
final class CompanyNames {

    private CompanyNames() {
    }

    static String clean(String rawName) {
        if (rawName == null) return null;
        return rawName.split("\n", 2)[0].trim();
    }
}
