package com.data.openai.estimation;

import com.data.shared.exception.CostEstimationException;
import com.data.shared.exception.ResourceNotFoundException;
import com.data.openai.client.GPTClient;
import com.data.openai.client.GPTTaskPriceMultiplier;
import com.data.youtube.persistence.entity.Video;
import com.data.youtube.persistence.repository.VideoRepository;
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
                .reduce(CostEstimate::sum)
                .orElseThrow(() -> new CostEstimationException("No Cost generation found"));
    }

    public CostEstimate findAndEstimateChannelTransformationCost(
            Map<String, String> multilingualSystemPrompt,
            String youtubeChannelId,
            GPTTaskPriceMultiplier multiplier
    ) {
        Stream<LangText> texts = youtubeChannelTexts(youtubeChannelId);

        return texts
                .map(langWithText ->
                        gptClient.estimateMaxTextCost(
                                multilingualSystemPrompt.get(langWithText.lang()),
                                langWithText.text(),
                                multiplier.getValue()
                        )
                )
                .reduce(CostEstimate::sum)
                .orElseThrow(() -> new CostEstimationException("No Cost generation found"));
    }

    private Stream<LangText> youtubeTexts(String youtubeVideoId) {
        Video video = videoRepository.findByYoutubeVideoId(youtubeVideoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

        return video.getTranscripts().stream()
                .map(t -> new LangText(t.getLanguage(), t.getTranscriptText()));
    }

    private Stream<LangText> youtubeChannelTexts(String youtubeChannelId) {
        return videoRepository.findAllByYoutubeChannelWithTranscripts(youtubeChannelId).stream()
                .flatMap(v -> v.getTranscripts().stream())
                .map(t -> new LangText(t.getLanguage(), t.getTranscriptText()));
    }
}
