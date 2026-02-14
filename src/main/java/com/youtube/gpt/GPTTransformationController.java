package com.youtube.gpt;

import com.youtube.arxiv.oai.section.ArxivSectionFilter;
import com.youtube.gpt.estimation.CostEstimate;
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

    @PostMapping("/estimate/arxiv")
    public CostEstimate estimateArxiv(
            @RequestParam Map<String, String> multilingualSystemPrompt,
            @RequestParam String resourceUniqueId,
            @RequestParam GPTTaskPriceMultiplier multiplier,
            @RequestBody(required = false) ArxivSectionFilter arxivSectionFilter
    ) {
        return gptService.findAndEstimateArxivTransformationCost(
                multilingualSystemPrompt, resourceUniqueId, multiplier, arxivSectionFilter
        );
    }
}