package com.data.oai.zenodo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ZenodoJson {
    private static final Pattern PDF_LINK = Pattern.compile(
            "\"key\"\\s*:\\s*\"[^\"]+\\.pdf\".*?\"links\"\\s*:\\s*\\{[^}]*\"self\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    static String pickFirstPdfUrl(String json) {
        if (json == null) return null;
        Matcher m = PDF_LINK.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
