package com.data.openai;

import com.data.oai.persistence.SectionFilter;
import com.data.openai.client.GPTTaskPriceMultiplier;
import com.data.openai.estimation.ArxivGPTCostEstimator;
import com.data.openai.estimation.CostEstimate;
import com.data.openai.estimation.YoutubeGPTCostEstimator;
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

    public CostEstimate findAndEstimateYoutubeChannelTransformationCost(
            Map<String, String> multilingualSystemPrompt,
            String youtubeChannelId,
            GPTTaskPriceMultiplier multiplier
    ) {
        return youtubeCostEstimator.findAndEstimateChannelTransformationCost(
                multilingualSystemPrompt, youtubeChannelId, multiplier
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
