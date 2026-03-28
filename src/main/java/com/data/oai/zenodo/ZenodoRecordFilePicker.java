package com.data.oai.zenodo;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ZenodoRecordFilePicker {

    // Zenodo's file API returns 400 when the key contains characters that survive
    // URL-encoding but break their backend routing (quotes, angle brackets, etc.).
    // Allow only printable ASCII minus the known-bad set.
    private static final Pattern UNSAFE_KEY = Pattern.compile("[\"<>{}|\\\\^`\\[\\]]");
    private static final long MIN_PDF_SIZE_BYTES = 10_000;
    private static final Set<String> REJECTED_SUBTYPES = Set.of(
            "taxonomictreatment", "poster", "presentation", "slides"
    );

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
        if (size < MIN_PDF_SIZE_BYTES) return false;

        if (rt != null && rt.getSubtype() != null) {
            String subtype = rt.getSubtype().toLowerCase(Locale.ROOT);
            if (REJECTED_SUBTYPES.stream().anyMatch(subtype::contains)) return false;
        }

        return true;
    }
}
