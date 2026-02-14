package com.youtube.gpt.estimation;

import com.youtube.gpt.GPTClient;
import com.youtube.gpt.GPTTaskPriceMultiplier;
import com.youtube.jpa.dao.Video;
import com.youtube.jpa.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class YoutubeGPTCostEstimator {
    private final GPTClient gptClient;
    private final VideoRepository videoRepository;

    public CostEstimate findAndEstimateResourceTransformationCost(
            Map<String, String> multilingualSystemPrompt,
            String resourceUniqueExternalId,
            GPTTaskPriceMultiplier multiplier
    ) {
        Stream<LangText> texts =  youtubeTexts(resourceUniqueExternalId);

        return texts
                .map(langWithText -> gptClient.estimateMaxTextCost(multilingualSystemPrompt.get(langWithText.lang()), langWithText.text(), multiplier.getValue()))
                .reduce((acc, supl) -> CostEstimate.builder()
                        .promptTokens(acc.promptTokens() + supl.promptTokens())
                        .averageCompletionTokens(acc.averageCompletionTokens() + supl.averageCompletionTokens())
                        .averagePrice(acc.averagePrice().add(supl.averagePrice()))
                        .build())
                .orElseThrow(() -> new RuntimeException("No Cost generation found"));
    }

    private Stream<LangText> youtubeTexts(String youtubeVideoId) {
        Video video = videoRepository.findByYoutubeVideoId(youtubeVideoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        return video.getTranscripts().stream()
                .map(t -> new LangText(t.getLanguage(), t.getTranscriptText()));
    }
}
