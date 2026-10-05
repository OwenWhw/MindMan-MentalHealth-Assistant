package com.mindman.ai;

import com.mindman.config.AiConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ChatClientRouterTest {

    @Test
    void autoProviderFallsBackFromUnavailableSelectedOllamaToCloudModel() {
        AiConfig config = new AiConfig();
        config.setProvider("auto");
        config.setModel("cloud-model");
        OllamaProperties ollamaProperties = new OllamaProperties();
        ollamaProperties.setEnabled(true);
        ollamaProperties.setModel("qwen2.5:7b");

        StreamingChatClient cloud = mock(StreamingChatClient.class);
        when(cloud.name()).thenReturn("cloud");
        when(cloud.isAvailable()).thenReturn(true);
        when(cloud.stream(anyList(), any())).thenReturn(Flux.just("real AI response"));

        StreamingChatClient ollama = mock(StreamingChatClient.class);
        when(ollama.name()).thenReturn("ollama");
        when(ollama.isAvailable()).thenReturn(true);
        when(ollama.stream(anyList(), any())).thenReturn(Flux.error(new IllegalStateException("Connection refused")));

        ChatClientRouter router = new ChatClientRouter(config, ollamaProperties, cloud, ollama);
        ChatOptions selectedLocalModel = ChatOptions.builder().model("qwen2.5:7b").build();

        List<String> chunks = router.stream(List.of(ChatMessage.user("测试")), selectedLocalModel).collectList().block();

        assertEquals(List.of("real AI response"), chunks);
        ArgumentCaptor<ChatOptions> cloudOptions = ArgumentCaptor.forClass(ChatOptions.class);
        verify(cloud).stream(anyList(), cloudOptions.capture());
        assertEquals("cloud-model", cloudOptions.getValue().getModel());
    }
}
