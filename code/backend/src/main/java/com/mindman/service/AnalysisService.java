package com.mindman.service;

import com.mindman.dto.AnalysisOverviewVO;

/**
 * 数据分析服务（管理端）。
 */
public interface AnalysisService {

    /**
     * 聚合平台综合数据（最近 days 天趋势 + 概览指标）。
     *
     * @param days 趋势统计天数（默认 14）
     * @return 综合分析视图
     */
    AnalysisOverviewVO overview(int days);
}
