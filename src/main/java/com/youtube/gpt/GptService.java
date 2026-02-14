package com.youtube.gpt;

import com.youtube.arxiv.oai.section.ArxivSectionFilter;
import com.youtube.gpt.estimation.ArxivGPTCostEstimator;
import com.youtube.gpt.estimation.CostEstimate;
import com.youtube.gpt.estimation.YoutubeGPTCostEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GptService {
    private final YoutubeGPTCostEstimator youtubeCostEstimator;
    private final ArxivGPTCostEstimator arxivCostEstimator;

    public CostEstimate findAndEstimateYoutubeTransformationCost(
            Map<String, String> multilingualSystemPrompt,
            String resourceUniqueExternalId,
            GPTTaskPriceMultiplier multiplier
    ) {
        return youtubeCostEstimator.findAndEstimateResourceTransformationCost(
                multilingualSystemPrompt, resourceUniqueExternalId, multiplier
        );
    }

    public CostEstimate findAndEstimateArxivTransformationCost(
            Map<String, String> multilingualSystemPrompt,
            String resourceUniqueExternalId,
            GPTTaskPriceMultiplier multiplier,
            ArxivSectionFilter filter
    ) {
        return arxivCostEstimator.findAndEstimateResourceTransformationCost(
                multilingualSystemPrompt, resourceUniqueExternalId, multiplier, filter
        );
    }
}
