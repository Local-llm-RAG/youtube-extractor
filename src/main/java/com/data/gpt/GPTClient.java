package com.data.gpt;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import com.data.gpt.estimation.CostEstimate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

@Service
@Slf4j
public class GPTClient {
    private static final ChatModel MODEL = ChatModel.GPT_5_2;

    private final OpenAIClient client;

    private static final BigDecimal INPUT_USD_PER_1M = new BigDecimal("0.25");
    private static final BigDecimal OUTPUT_USD_PER_1M = new BigDecimal("2");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final Encoding encoding;

    public GPTClient(@Value("${gpt.key}") String apiKey) {
        this.client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
        this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE);
    }

    public CostEstimate estimateMaxTextCost(
            String systemPrompt,
            String userPrompt,
            BigDecimal taskMultiplier
    ) {
        long systemTokens = encoding.countTokens(systemPrompt);
        long userTokens = encoding.countTokens(userPrompt);
        long overheadTokens = 20; // Adding extra start and end tokens
        long promptTokens = systemTokens + userTokens + overheadTokens;
        BigDecimal promptUsd = usdForInputTokens(promptTokens);

        BigDecimal avgCompletionTokens =
                BigDecimal.valueOf(promptTokens)
                        .multiply(taskMultiplier);

        BigDecimal avgCompletionUsd =
                avgCompletionTokens
                        .multiply(OUTPUT_USD_PER_1M)
                        .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);

        BigDecimal avgUsd = promptUsd.add(avgCompletionUsd);

        return new CostEstimate(
                promptTokens,
                avgCompletionTokens.setScale(0, RoundingMode.CEILING).longValueExact(),
                avgUsd
        );
    }

    public GPTTextResult requestText(String systemPrompt, String userPrompt, Supplier<? extends RuntimeException> exceptionSupplier) {

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);

        String text = completion.choices()
                .get(0)
                .message()
                .content()
                .orElseThrow(() -> {
                    log.warn("GPT text response missing or empty!");
                    return exceptionSupplier.get();
                });

        CompletionUsage usage = completion.usage().get();
        long promptTokens = usage.promptTokens();
        long completionTokens = usage.completionTokens();

        BigDecimal actualUsd = usdForInputTokens(promptTokens).add(usdForOutputTokens(completionTokens));

        return new GPTTextResult(text, usage, actualUsd);
    }

    private BigDecimal usdForInputTokens(long tokens) {
        return INPUT_USD_PER_1M
                .multiply(BigDecimal.valueOf(tokens))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal usdForOutputTokens(long tokens) {
        return OUTPUT_USD_PER_1M
                .multiply(BigDecimal.valueOf(tokens))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }


    private <T> StructuredChatCompletionCreateParams<T> buildStructuredParams(
            String prompt,
            String systemMessage,
            Class<T> responseClass
    ) {
        return ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(systemMessage)
                .addUserMessage(prompt)
                .responseFormat(responseClass)
                .build();
    }

    private <T> T getStructuredResponse(
            StructuredChatCompletionCreateParams<T> params,
            Supplier<? extends RuntimeException> exceptionSupplier
    ) {
        return client.chat()
                .completions()
                .create(params)
                .choices()
                .get(0)
                .message()
                .content()
                .orElseThrow(() -> {
                    log.warn("GPT structured response missing or invalid!");

                    return exceptionSupplier.get();
                });
    }
}
