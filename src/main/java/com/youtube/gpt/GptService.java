package com.youtube.gpt;

import com.youtube.jpa.dao.Video;
import com.youtube.jpa.repository.ArxivRepository;
import com.youtube.jpa.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GptService {
    private final GPTClient gptClient;
    private final VideoRepository videoRepository;
    private final ArxivRepository arxivRepository;

    public CostEstimate findAndEstimateResourceTransformationCost(
            Map<String, String> multilingualSystemPrompt,
            ResourceType resourceType,
            String resourceUniqueExternalId,
            GptTaskPriceMultiplier multiplier
    ) {
        Stream<AbstractMap.SimpleEntry<String, String>> resourceText = Stream.of();
        if (resourceType == ResourceType.YOUTUBE_TRANSCRIPT) {
            Video video = videoRepository.findByYoutubeVideoId(resourceUniqueExternalId)
                    .orElseThrow(() -> new RuntimeException("Video not found"));
            resourceText = video.getTranscripts().stream()
                    .map(videoTranscript ->
                            new AbstractMap.SimpleEntry<>(videoTranscript.getLanguage(), videoTranscript.getTranscriptText()));

        } else if (resourceType == ResourceType.ARCHIVE_PAPER) {
            resourceText = Stream.of(); // TODO: when database this needs to be implemented
        }

        return resourceText
                .map(langWithText -> gptClient.estimateMaxTextCost(multilingualSystemPrompt.get(langWithText.getKey()), langWithText.getValue(), multiplier.getValue()))
                .reduce((acc, supl) -> CostEstimate.builder()
                        .promptTokens(acc.promptTokens() + supl.promptTokens())
                        .averageCompletionTokens(acc.averageCompletionTokens() + supl.averageCompletionTokens())
                        .averagePrice(acc.averagePrice().add(supl.averagePrice()))
                        .build())
                .orElseThrow(() -> new RuntimeException("No Cost generation found"));


    }
}
