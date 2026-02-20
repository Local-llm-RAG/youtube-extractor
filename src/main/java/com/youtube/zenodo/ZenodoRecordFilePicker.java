package com.youtube.zenodo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public final class ZenodoRecordFilePicker {
    private static final ObjectMapper OM = new ObjectMapper();

    private ZenodoRecordFilePicker() {}

    public static ZenodoRecord.FileEntry pickPdfUrl(ZenodoRecord rec) {
        if (rec.getFiles() == null) return null;
        return rec.getFiles().stream()
                .filter(f -> f != null && f.getKey() != null && f.getKey().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .filter(f -> f.getLinks() != null && f.getLinks().getSelf() != null && !f.getLinks().getSelf().isBlank()).min((a, b) -> Long.compare(
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
