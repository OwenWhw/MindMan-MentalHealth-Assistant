package com.mindman.ai;

import lombok.Builder;
import lombok.Data;

/**
 * 一次对话调用的参数（模型 / 采样参数）。
 * 各字段为 null 时由客户端回退到其自身默认配置。
 */
@Data
@Builder
public class ChatOptions {

    /** 模型 ID（如 qwen3.8-max / qwen2.5:7b），null 则用客户端默认模型 */
    private String model;

    /** 最大回复 token 数 */
    private Integer maxTokens;

    /** 采样温度 */
    private Double temperature;
}
