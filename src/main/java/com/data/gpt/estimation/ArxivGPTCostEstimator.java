package com.data.gpt.estimation;

import com.data.oai.PapersRepository;
import com.data.oai.generic.common.record.RecordEntity;
import com.data.oai.generic.common.section.SectionEntity;
import com.data.oai.generic.common.section.SectionFilter;
import com.data.gpt.GPTClient;
import com.data.gpt.GPTTaskPriceMultiplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Component
@RequiredArgsConstructor
public class ArxivGPTCostEstimator {
    private final GPTClient gptClient;
    private final PapersRepository papersRepository;

    public CostEstimate findAndEstimateResourceTransformationCost(
            Map<String, String> multilingualSystemPrompt,
            String resourceUniqueExternalId,
            GPTTaskPriceMultiplier multiplier,
            SectionFilter arxivFilter
    ) {
        Stream<LangText> texts = arxivTexts(resourceUniqueExternalId, arxivFilter);

        return texts
                .map(langWithText -> gptClient.estimateMaxTextCost(multilingualSystemPrompt.get(langWithText.lang()), langWithText.text(), multiplier.getValue()))
                .reduce((acc, supl) -> CostEstimate.builder()
                        .promptTokens(acc.promptTokens() + supl.promptTokens())
                        .averageCompletionTokens(acc.averageCompletionTokens() + supl.averageCompletionTokens())
                        .averagePrice(acc.averagePrice().add(supl.averagePrice()))
                        .build())
                .orElseThrow(() -> new RuntimeException("No Cost generation found"));
    }

    private Stream<LangText> arxivTexts(String arxivId, SectionFilter filter) {
        RecordEntity record = papersRepository.findBySourceId(arxivId).orElseThrow(() -> new RuntimeException("Arxiv record not found"));

        if (isNull(record.getDocument()) || isNull(record.getDocument().getSections()) || record.getDocument().getSections().isEmpty()) {
            String fallback = buildMetadataText(record);

            return Stream.of(new LangText("en", fallback));
        }

        String text = buildFilteredPaperText(record, filter);

        return Stream.of(new LangText("en", text));
    }

    private String buildFilteredPaperText(RecordEntity record, SectionFilter filter) {
        List<SectionEntity> list = getArxivSectionEntities(record, filter);

        StringBuilder sb = new StringBuilder();

        if (nonNull(record.getDocument().getTitle())) {
            sb.append("TITLE: ").append(record.getDocument().getTitle()).append("\n\n");
        }

        if (nonNull(record.getDocument().getAbstractText())) {
            sb.append("ABSTRACT: ").append(record.getDocument().getAbstractText()).append("\n\n");
        }

        for (SectionEntity s : list) {
            sb.append("## ").append(isNull(s.getTitle() ) ? "UNTITLED" : s.getTitle()).append("\n");

            if (nonNull(s.getText())) {
                sb.append(s.getText()).append("\n\n");
            }
        }

        if (nonNull(filter) && filter.getMaxCharsTotal() != null && sb.length() > filter.getMaxCharsTotal()) {
            return sb.substring(0, filter.getMaxCharsTotal());
        }

        return sb.toString();
    }

    private List<SectionEntity> getArxivSectionEntities(RecordEntity record, SectionFilter filter) {
        var sections = record.getDocument().getSections().stream();

        if (nonNull(filter)) {
            if (nonNull(filter.getMaxLevel())) {
                sections = sections.filter(s -> isNull(s.getLevel()) || s.getLevel() <= filter.getMaxLevel());
            }
            if (nonNull(filter.getIncludeTitles()) && !filter.getIncludeTitles().isEmpty()) {
                sections = sections.filter(s -> titleMatches(filter.getIncludeTitles(), s.getTitle()));
            }
            if (nonNull(filter.getExcludeTitles()) && !filter.getExcludeTitles().isEmpty()) {
                sections = sections.filter(s -> !titleMatches(filter.getExcludeTitles(), s.getTitle()));
            }
        }

        var list = sections.toList();

        if (nonNull(filter) && nonNull(filter.getMaxSections()) && list.size() > filter.getMaxSections()) {
            list = list.subList(0, filter.getMaxSections());
        }

        return list;
    }

    private boolean titleMatches(java.util.Set<String> needles, String title) {
        if (isNull(title)) {
            return false;
        }

        String t = title.trim().toLowerCase(java.util.Locale.ROOT);

        for (String n : needles) {
            if (isNull(n)) {
                continue;
            }

            String nn = n.trim().toLowerCase(java.util.Locale.ROOT);

            if (!nn.isEmpty() && t.contains(nn)) {
                return true;
            }
        }

        return false;
    }

    private String buildMetadataText(RecordEntity r) {
        return """
            TITLE: %s

            ABSTRACT: %s
            """.formatted(nullToEmpty(r.getDocument().getTitle()), nullToEmpty(r.getDocument().getAbstractText()));
    }

    private String nullToEmpty(String s) { return isNull(s) ? "" : s; }
}
