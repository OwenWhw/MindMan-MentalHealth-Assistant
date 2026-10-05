package com.mindman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GardenInsightRequest {
    @NotBlank(message = "请先选择心情")
    @Size(max = 20)
    private String emotion;

    @NotBlank(message = "请先写下今天发生了什么")
    @Size(max = 255)
    private String content;

    @Size(max = 64)
    private String trigger;
}
