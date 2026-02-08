package com.youtube.controller;

import com.youtube.gpt.CostEstimate;
import com.youtube.gpt.GptService;
import com.youtube.gpt.GptTaskPriceMultiplier;
import com.youtube.gpt.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AITransformationController {

    private final GptService gptService;

    @GetMapping("/estimate")
    public CostEstimate estimate(
            @RequestParam Map<String, String> multilingualSystemPrompt,
            @RequestParam String resourceUniqueId,
            @RequestParam ResourceType resourceType,
            @RequestParam GptTaskPriceMultiplier multiplier
    ) {
        return gptService.findAndEstimateResourceTransformationCost(multilingualSystemPrompt, resourceType, resourceUniqueId, multiplier);
    }
}