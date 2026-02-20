package com.data.gpt;

import com.data.oai.generic.common.section.SectionFilter;
import com.data.gpt.estimation.ArxivGPTCostEstimator;
import com.data.gpt.estimation.CostEstimate;
import com.data.gpt.estimation.YoutubeGPTCostEstimator;
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
            SectionFilter filter
    ) {
        return arxivCostEstimator.findAndEstimateResourceTransformationCost(
                multilingualSystemPrompt, resourceUniqueExternalId, multiplier, filter
        );
    }
}
