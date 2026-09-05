<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHead from '@/components/PageHead.vue'
import BaseChart from '@/components/BaseChart.vue'
import { getAnalysisOverview } from '@/api/analysis'

const loading = ref(false)
const overview = ref(null)

const statCards = computed(() => {
  const d = overview.value || {}
  return [
    {
      title: '总用户数',
      icon: 'User',
      bg: 'rgba(47, 111, 219, 0.1)',
      color: '#2f6fdb',
      value: d.userTotal ?? '-',
      subs: [
        { label: '活跃用户', value: d.activeUsers ?? '-' },
        { label: '今日新增', value: d.newUsersToday ?? '-' }
      ]
    },
    {
      title: '情绪日志',
      icon: 'Notebook',
      bg: 'rgba(47, 111, 219, 0.1)',
      color: '#2f6fdb',
      value: d.diaryTotal ?? '-',
      subs: [{ label: '今日新增', value: d.diaryToday ?? '-' }]
    },
    {
      title: '咨询会话',
      icon: 'ChatDotRound',
      bg: 'rgba(47, 111, 219, 0.1)',
      color: '#2f6fdb',
      value: d.sessionTotal ?? '-',
      subs: [{ label: '今日新增', value: d.sessionToday ?? '-' }]
    },
    {
      title: '情绪健康指数',
      icon: 'DataLine',
      bg: 'rgba(47, 111, 219, 0.1)',
      color: '#2f6fdb',
      value: d.emotionHealth != null ? `${d.emotionHealth}/10` : '-',
      subs: [{ label: '平均时长', value: d.avgDuration != null ? `${d.avgDuration} 分钟` : '-' }]
    }
  ]
})

const C = {
  blue: '#5a84d6',
  sage: '#8da898',
  mauve: '#a59ac2',
  sand: '#c5aa82',
  grid: 'rgba(17, 24, 39, 0.05)',
  axis: '#cdd3dc',
  label: '#9ca3af'
}

function axisBase(xData) {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(17, 24, 39, 0.12)' } },
      backgroundColor: '#ffffff',
      borderColor: '#eceff3',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: '#374151', fontSize: 12 }
    },
    grid: { left: 46, right: 46, top: 34, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xData,
      axisLine: { lineStyle: { color: C.axis } },
      axisTick: { show: false },
      axisLabel: { color: C.label, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: C.label, fontSize: 11 },
      splitLine: { lineStyle: { color: C.grid } }
    }
  }
}

function legendOption(names) {
  return {
    legend: {
      data: names,
      top: 0,
      right: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 18,
      textStyle: { color: '#6b7280', fontSize: 12 }
    }
  }
}

const emotionTrendOption = computed(() => {
  const list = overview.value?.emotionTrend || []
  const base = axisBase(list.map((i) => i.date))
  return {
    ...base,
    ...legendOption(['平均情绪评分', '记录数量']),
    yAxis: [
      {
        ...base.yAxis,
        min: 0,
        max: 5
      },
      {
        ...base.yAxis,
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '平均情绪评分',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2, color: C.blue },
        itemStyle: { color: C.blue },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(90, 132, 214, 0.2)' },
              { offset: 1, color: 'rgba(90, 132, 214, 0)' }
            ]
          }
        },
        data: list.map((i) => i.avgScore),
        yAxisIndex: 0
      },
      {
        name: '记录数量',
        type: 'bar',
        barWidth: 10,
        itemStyle: { color: 'rgba(90, 132, 214, 0.2)', borderRadius: [3, 3, 0, 0] },
        data: list.map((i) => i.count),
        yAxisIndex: 1
      }
    ]
  }
})

const consultActivityOption = computed(() => {
  const list = overview.value?.consultActivity || []
  const base = axisBase(list.map((i) => i.date))
  return {
    ...base,
    ...legendOption(['会话数量', '参与用户数']),
    series: [
      {
        name: '会话数量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2, color: C.blue },
        itemStyle: { color: C.blue },
        data: list.map((i) => i.sessions)
      },
      {
        name: '参与用户数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2, color: C.sage },
        itemStyle: { color: C.sage },
        data: list.map((i) => i.users)
      }
    ]
  }
})

const sessionStatsOption = computed(() => {
  const list = overview.value?.sessionStats || []
  const base = axisBase(list.map((i) => i.date))
  return {
    ...base,
    xAxis: { ...base.xAxis, boundaryGap: true },
    series: [
      {
        name: '总会话数',
        type: 'bar',
        barWidth: 18,
        itemStyle: { color: 'rgba(165, 154, 194, 0.75)', borderRadius: [4, 4, 0, 0] },
        data: list.map((i) => i.sessions)
      }
    ]
  }
})

const activityTrendOption = computed(() => {
  const list = overview.value?.activityTrend || []
  const base = axisBase(list.map((i) => i.date))
  const names = ['活跃用户', '新用户', '日记用户', '咨询用户']
  const keys = ['activeUsers', 'newUsers', 'diaryUsers', 'consultUsers']
  const colors = [C.blue, C.sage, C.sand, C.mauve]
  return {
    ...base,
    ...legendOption(names),
    series: names.map((name, idx) => ({
      name,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 1.6, color: colors[idx] },
      itemStyle: { color: colors[idx] },
      data: list.map((i) => i[keys[idx]])
    }))
  }
})

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await getAnalysisOverview()
  } catch (e) {
    overview.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<template>
  <div class="page">
    <PageHead icon="DataAnalysis" title="数据分析" />

    <div v-loading="loading" class="stat-cards">
      <el-card v-for="card in statCards" :key="card.title" shadow="never" class="stat-card">
        <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
          <el-icon :size="22"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-title">{{ card.title }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-subs">
            <span v-for="sub in card.subs" :key="sub.label" class="stat-sub">
              {{ sub.label }} {{ sub.value }}
            </span>
          </div>
        </div>
      </el-card>
    </div>

    <div class="chart-grid">
      <el-card shadow="never" class="chart-card">
        <template #header><span class="chart-title">情绪趋势分析</span></template>
        <BaseChart :option="emotionTrendOption" height="300px" />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">咨询会话统计</span>
            <span class="chart-meta">
              平均时长 {{ overview?.avgDuration != null ? `${overview.avgDuration} 分钟` : '-' }}
            </span>
          </div>
        </template>
        <BaseChart :option="sessionStatsOption" height="300px" />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header><span class="chart-title">咨询活动统计</span></template>
        <BaseChart :option="consultActivityOption" height="300px" />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header><span class="chart-title">用户活跃度趋势</span></template>
        <BaseChart :option="activityTrendOption" height="300px" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  min-height: 110px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  border-radius: 12px;
  border: 1px solid #edf0f4;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-info {
  min-width: 0;
}

.stat-title {
  font-size: 13px;
  color: #8b93a1;
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #23272f;
  line-height: 1.1;
  letter-spacing: 0.5px;
  font-variant-numeric: tabular-nums;
}

.stat-subs {
  display: flex;
  gap: 14px;
  margin-top: 8px;
}

.stat-sub {
  font-size: 12px;
  color: #a4abb7;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.chart-card {
  border-radius: 12px;
  border: 1px solid #edf0f4;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #3a4150;
  letter-spacing: 0.3px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-meta {
  font-size: 12px;
  color: #a4abb7;
}

@media (max-width: 1100px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
