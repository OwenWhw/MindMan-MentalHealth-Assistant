package com.mindman.service;

import com.mindman.dto.ArticleVO;

import java.util.List;

/**
 * 文章推荐服务。
 *
 * <p>个性化算法：</p>
 * <ol>
 *   <li>用户最近 7 天的 {@code emotion_record.emotion} + {@code chat_message.emotion}</li>
 *   <li>通过情绪词表映射得到「用户画像词袋」</li>
 *   <li>每篇文章按标签重叠 / 新鲜度 / 阅读量综合打分</li>
 *   <li>至少保留一篇实时分类 + 多样性（去重分类）</li>
 * </ol>
 *
 * <p>无登录态（{@code userId=null}）时退化为热门+新鲜推荐。</p>
 */
public interface RecommendService {

    /**
     * 推荐文章给当前用户。
     *
     * @param userId  当前登录用户 ID，可为空
     * @param limit   推荐数量（>=1，<=10）
     * @return 推荐文章列表（带分数排序后的结果）
     */
    List<ArticleVO> recommend(Long userId, int limit);
}
