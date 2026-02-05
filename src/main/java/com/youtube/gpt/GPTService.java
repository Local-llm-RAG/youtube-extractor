package com.youtube.gpt;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@Slf4j
public class GPTService {
    private static final ChatModel MODEL = ChatModel.GPT_5_2;

    @Value("${application.gpt.key}")
    private String apiKey;

    private OpenAIClient client;

    @PostConstruct
    public void init() {
        this.client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    private ChatCompletionCreateParams buildTextParams(
            String prompt,
            String systemMessage
    ) {
        return ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(systemMessage)
                .addUserMessage(prompt)
                .build();
    }

    private String getTextResponse(
            ChatCompletionCreateParams params,
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
                    log.warn("GPT text response missing or empty!");
                    return exceptionSupplier.get();
                });
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
