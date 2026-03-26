package com.data.oai.zenodo;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public final class ZenodoRecordFilePicker {

    // Zenodo's file API returns 400 when the key contains characters that survive
    // URL-encoding but break their backend routing (quotes, angle brackets, etc.).
    // Allow only printable ASCII minus the known-bad set.
    private static final Pattern UNSAFE_KEY = Pattern.compile("[\"<>{}|\\\\^`\\[\\]]");

    private ZenodoRecordFilePicker() {}

    public static ZenodoRecord.FileEntry pickPdfUrl(ZenodoRecord rec) {
        if (rec.getFiles() == null) return null;
        return rec.getFiles().stream()
                .filter(f -> f != null && f.getKey() != null && f.getKey().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .filter(f -> !UNSAFE_KEY.matcher(f.getKey()).find())
                .filter(f -> f.getLinks() != null && f.getLinks().getSelf() != null && !f.getLinks().getSelf().isBlank())
                .min((a, b) -> Long.compare(
                        b.getSize() == null ? 0 : b.getSize(),
                        a.getSize() == null ? 0 : a.getSize()
                ))
                .orElse(null);
    }

    public static boolean acceptForGrobid(ZenodoRecord rec) {
        if (rec == null || rec.getMetadata() == null) return false;

        var rt = rec.getMetadata().getResourceType();
        if (rt != null && rt.getType() != null) {
            if (!"publication".equalsIgnoreCase(rt.getType())) return false;
        }

        ZenodoRecord.FileEntry pdf = pickPdfUrl(rec);
        if (pdf == null) return false;

        long size = pdf.getSize() == null ? 0 : pdf.getSize();
        if (size < 40_000) return false; // 40 KB

        if (rt != null && rt.getSubtype() != null) {
            String subtype = rt.getSubtype().toLowerCase(Locale.ROOT);
            if (subtype.contains("taxonomictreatment")) return false;
            if (subtype.contains("poster")) return false;
            if (subtype.contains("presentation")) return false;
            if (subtype.contains("slides")) return false;
        }

        return true;
    }
}
