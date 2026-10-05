package com.mindman.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatStreamingChatClientTest {

    private final OpenAiCompatStreamingChatClient client = new OpenAiCompatStreamingChatClient(
            "test", "http://127.0.0.1:1/v1", "", "test-model", 16, 0.2,
            Duration.ofSeconds(1), Duration.ofSeconds(1), new ObjectMapper());

    @Test
    void preservesWhitespaceDeltaBetweenEnglishWords() {
        List<String> deltas = List.of(
                "{\"choices\":[{\"delta\":{\"content\":\"Fans\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\" \"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"across\"}}]}"
        );

        assertEquals("Fans across", deltas.stream()
                .map(data -> client.parseDeltaFromSseData(data).block())
                .collect(Collectors.joining()));
    }

    @Test
    void removesThinkTextWithoutTrimmingVisibleChunkBoundaries() {
        String delta = """
                {"choices":[{"delta":{"content":" visible <think>private reasoning</think> "}}]}
                """;

        assertEquals(" visible  ", client.parseDeltaFromSseData(delta).block());
    }
}
