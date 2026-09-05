package com.mindman.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 每日一句 / 随机语录视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteVO {

    /** 中文/主文本 */
    private String content;

    /** 英文翻译（外部 API 不提供时为空） */
    private String translation;

    /** 作者 / 来源 */
    private String author;

    /** 来源作品 */
    private String source;
}
