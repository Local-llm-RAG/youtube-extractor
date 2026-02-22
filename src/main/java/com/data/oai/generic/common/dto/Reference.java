package com.data.oai.generic.common.dto;

import java.util.List;
import java.util.Map;

public record Reference(
        int index,
        String analyticTitle,
        String monogrTitle,
        String doi,
        List<String> urls,
        List<String> authors,
        String year,
        String venue,
        Map<String, String> idnos
) {}