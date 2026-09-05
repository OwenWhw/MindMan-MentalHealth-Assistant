package com.mindman.service;

import com.mindman.dto.QuoteVO;

/**
 * 每日一句 / 语录服务。
 */
public interface QuoteService {

    /**
     * 获取一条随机语录。
     *
     * <p>优先从外部一言 API（hitokoto.cn）拉取；当外部服务不可用或超时时，
     * 返回本地兜底语录，避免前端空窗。</p>
     *
     * @return 语录 VO
     */
    QuoteVO randomQuote();
}
