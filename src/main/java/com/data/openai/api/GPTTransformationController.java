package com.data.openai.api;

import com.data.oai.persistence.SectionFilter;
import com.data.openai.GptService;
import com.data.openai.client.GPTTaskPriceMultiplier;
import com.data.openai.estimation.CostEstimate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GPTTransformationController {
    private final GptService gptService;

    @PostMapping("/estimate/youtube")
    public CostEstimate estimateYoutube(
            @RequestParam Map<String, String> multilingualSystemPrompt,
            @RequestParam String resourceUniqueId,
            @RequestParam GPTTaskPriceMultiplier multiplier
    ) {

        return gptService.findAndEstimateYoutubeTransformationCost(
                multilingualSystemPrompt, resourceUniqueId, multiplier
        );
    }

    @PostMapping("/estimate/youtube/channel")
    public CostEstimate estimateYoutubeChannel(
            @RequestParam Map<String, String> multilingualSystemPrompt,
            @RequestParam String youtubeChannelId,
            @RequestParam GPTTaskPriceMultiplier multiplier
    ) {
        return gptService.findAndEstimateYoutubeChannelTransformationCost(
                multilingualSystemPrompt, youtubeChannelId, multiplier
        );
    }

    @PostMapping("/estimate/arxiv")
    public CostEstimate estimateArxiv(
            @RequestParam Map<String, String> multilingualSystemPrompt,
            @RequestParam String resourceUniqueId,
            @RequestParam GPTTaskPriceMultiplier multiplier,
            @RequestBody(required = false) SectionFilter sectionFilter
    ) {
        return gptService.findAndEstimateArxivTransformationCost(
                multilingualSystemPrompt, resourceUniqueId, multiplier, sectionFilter
        );
    }
}