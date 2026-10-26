<template>
    <div class="emotionDiary-container">
        <div class="header-section">
            <div class="blob blob-1"></div>
            <div class="blob blob-2"></div>
            <div class="header-inner">
                <div class="header-badge">
                    <span class="dot"></span>
                    情绪自动记录 · 仅自己可见
                </div>
                <div class="header-content">
                    <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
                    <h1>我的心情</h1>
                </div>
                <p class="header-desc">这里汇集了您在 AI 咨询中被留意到的情绪波动记录，心情会随对话自动记录在这里</p>
                <div class="header-meta">
                    <span>对话中自动记录</span>
                    <i>|</i>
                    <span>AI 情绪评估</span>
                    <i>|</i>
                    <span>隐私仅自己可见</span>
                </div>
            </div>
        </div>
        <div class="content">
            <template v-if="records.length">
                <!-- 左侧悬浮概览栏 -->
                <aside class="overview-aside">
                    <div class="overview-card">
                        <div class="overview-title">心情概览</div>
                        <div class="stat-line">
                            <span class="stat-icon">📒</span>
                            <div class="stat-info">
                                <div class="stat-value">{{ records.length }}</div>
                                <div class="stat-label">累计记录</div>
                            </div>
                        </div>
                        <div class="stat-line">
                            <span class="stat-icon">🌤️</span>
                            <div class="stat-info">
                                <div class="stat-value">{{ avgScore }}</div>
                                <div class="stat-label">平均心情分</div>
                            </div>
                        </div>
                        <div class="stat-line">
                            <span class="stat-icon">🕒</span>
                            <div class="stat-info">
                                <div class="stat-value stat-value-sm">{{ latestDate }}</div>
                                <div class="stat-label">最近记录</div>
                            </div>
                        </div>
                    </div>
                    <div class="trend-card">
                        <div class="trend-title">心情走势</div>
                        <div ref="trendChartRef" class="trend-chart"></div>
                    </div>
                </aside>
                <!-- 右侧时间线记录流 -->
                <main class="record-main">
                    <div class="main-title">记录时间线</div>
                    <div class="timeline">
                        <div v-for="record in records" :key="record.id" class="timeline-item">
                            <div class="node"></div>
                            <div class="record-card">
                                <div class="record-head">
                                    <div class="record-date">{{ record.diaryDate }}</div>
                                    <el-tag type="primary" effect="plain" size="small">来自 AI 咨询</el-tag>
                                </div>
                                <div class="record-score">
                                    <el-rate :model-value="record.moodScore" disabled :max="10" />
                                    <span class="score-num">{{ record.moodScore }}/10</span>
                                </div>
                                <div v-if="record.dominantEmotion" class="record-emotion">主要情绪：{{ record.dominantEmotion }}</div>
                                <p v-if="record.emotionTriggers" class="record-line"><span class="line-label">触发因素</span>{{ record.emotionTriggers }}</p>
                                <p v-if="record.diaryContent" class="record-line"><span class="line-label">记录内容</span>{{ record.diaryContent }}</p>
                                <div class="record-indicators" v-if="record.sleepQuality || record.stressLevel">
                                    <span v-if="record.sleepQuality">睡眠：{{ sleepText(record.sleepQuality) }}</span>
                                    <span v-if="record.stressLevel">压力：{{ stressText(record.stressLevel) }}</span>
                                </div>
                                <!-- AI 评估 -->
                                <div v-if="parseAnalysis(record)" class="ai-analysis">
                                    <div class="analysis-head">
                                        <span class="analysis-title">AI 情绪评估</span>
                                        <span v-if="parseAnalysis(record).label" class="analysis-label">{{ parseAnalysis(record).icon }} {{ parseAnalysis(record).label }}</span>
                                    </div>
                                    <p v-if="parseAnalysis(record).suggestion" class="analysis-suggestion">{{ parseAnalysis(record).suggestion }}</p>
                                    <div v-if="parseAnalysis(record).improvementSuggestions?.length" class="analysis-tips">
                                        <span v-for="(tip, i) in parseAnalysis(record).improvementSuggestions" :key="i" class="tip-item">{{ tip }}</span>
                                    </div>
                                </div>
                                <div v-else class="ai-analysis pending">
                                    <span class="analysis-title">AI 情绪评估生成中…</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </main>
            </template>
            <!-- 空状态 -->
            <div v-else-if="!loading" class="empty-state">
                <p>还没有心情记录</p>
                <p class="empty-tip">去 AI 咨询聊聊吧，当对话中流露出情绪波动时，会自动记录在这里</p>
                <router-link to="/consultations" class="empty-link">开始 AI 咨询</router-link>
            </div>
        </div>
    </div>
</template>
<script setup>
    import * as echarts from 'echarts'
    import { ElMessage } from 'element-plus'
    import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
    import { getMyEmotionDiary } from '@/api/frontend'

    const records = ref([])
    const loading = ref(false)
    const trendChartRef = ref(null)
    let trendChart = null

    const loadRecords = () => {
        loading.value = true
        // 响应拦截器已剥离 Result 外壳，res 即记录数组
        getMyEmotionDiary().then(res => {
            records.value = res || []
            nextTick(renderTrend)
        }).catch(() => {
            ElMessage.error('记录加载失败')
        }).finally(() => {
            loading.value = false
        })
    }

    const sleepTextMap = { 1: '很差', 2: '较差', 3: '一般', 4: '良好', 5: '优秀' }
    const stressTextMap = { 1: '很低', 2: '较低', 3: '中等', 4: '较高', 5: '很高' }
    const sleepText = (v) => sleepTextMap[v] || v
    const stressText = (v) => stressTextMap[v] || v

    const avgScore = computed(() => {
        if (!records.value.length) return '—'
        const sum = records.value.reduce((s, r) => s + (r.moodScore || 0), 0)
        return (sum / records.value.length).toFixed(1)
    })

    const latestDate = computed(() => records.value[0]?.diaryDate || '—')

    // AI 评估 JSON 解析：脏数据时静默降级为"生成中"，不让整页挂掉
    const parseAnalysis = (record) => {
        if (!record.aiEmotionAnalysis) return null
        try {
            return JSON.parse(record.aiEmotionAnalysis)
        } catch (e) {
            return null
        }
    }

    // 心情走势：按日期升序画 moodScore 曲线，同一天多条取平均
    const renderTrend = () => {
        if (!trendChartRef.value || !records.value.length) return
        const byDate = new Map()
        records.value.forEach(r => {
            const list = byDate.get(r.diaryDate) || []
            list.push(r.moodScore || 0)
            byDate.set(r.diaryDate, list)
        })
        const dates = [...byDate.keys()].sort()
        const scores = dates.map(d => {
            const list = byDate.get(d)
            return +(list.reduce((s, v) => s + v, 0) / list.length).toFixed(1)
        })

        if (!trendChart) trendChart = echarts.init(trendChartRef.value)
        trendChart.setOption({
            grid: { left: 40, right: 24, top: 24, bottom: 30 },
            tooltip: {
                trigger: 'axis',
                formatter: (params) => `${params[0].name}<br/>心情分：${params[0].value}`
            },
            xAxis: {
                type: 'category',
                data: dates,
                boundaryGap: false,
                axisLine: { lineStyle: { color: '#E5E7EB' } },
                axisLabel: { color: '#6B7280' }
            },
            yAxis: {
                type: 'value',
                min: 0,
                max: 10,
                splitLine: { lineStyle: { color: '#F3F4F6' } },
                axisLabel: { color: '#6B7280' }
            },
            series: [{
                type: 'line',
                data: scores,
                smooth: true,
                symbolSize: 7,
                lineStyle: { width: 3, color: '#7ED321' },
                itemStyle: { color: '#7ED321' },
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(126, 211, 33, 0.25)' },
                        { offset: 1, color: 'rgba(126, 211, 33, 0.02)' }
                    ])
                }
            }]
        })
    }

    const handleResize = () => trendChart && trendChart.resize()

    const iconUrl = new URL('@/assets/images/like.png', import.meta.url).href

    onMounted(() => {
        loadRecords()
        window.addEventListener('resize', handleResize)
    })

    onBeforeUnmount(() => {
        window.removeEventListener('resize', handleResize)
        if (trendChart) {
            trendChart.dispose()
            trendChart = null
        }
    })
</script>
<style lang="scss" scoped>
    .emotionDiary-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    min-height: 100vh;
    .header-section {
        position: relative;
        overflow: hidden;
        background: linear-gradient(135deg, #7ED321 0%, #F5A623 100%);
        color: white;
        padding: 44px 48px;
        .header-inner {
            position: relative;
            z-index: 1;
        }
        .header-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 6px 16px;
            border-radius: 999px;
            background: rgba(255, 255, 255, 0.16);
            border: 1px solid rgba(255, 255, 255, 0.28);
            font-size: 14px;
            letter-spacing: 1px;
            .dot {
                width: 8px;
                height: 8px;
                border-radius: 50%;
                background: #ffd98e;
                box-shadow: 0 0 8px rgba(255, 217, 142, 0.9);
            }
        }
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-top: 18px;
            h1 {
                margin: 0;
                font-size: 34px;
                font-weight: 700;
            }
        }
        .header-desc {
            margin: 14px 0 0 72px;
            max-width: 560px;
            font-size: 15px;
            line-height: 1.8;
            opacity: 0.92;
        }
        .header-meta {
            display: flex;
            align-items: center;
            gap: 14px;
            margin: 18px 0 0 72px;
            font-size: 13px;
            opacity: 0.85;
            i {
                font-style: normal;
                opacity: 0.4;
            }
        }
        .blob {
            position: absolute;
            border-radius: 50%;
            filter: blur(60px);
            opacity: 0.4;
            pointer-events: none;
        }
        .blob-1 {
            width: 360px;
            height: 360px;
            background: rgba(255, 255, 255, 0.35);
            top: -160px;
            right: 40px;
        }
        .blob-2 {
            width: 260px;
            height: 260px;
            background: rgba(255, 224, 130, 0.45);
            bottom: -140px;
            left: 30%;
        }
    }
    .content {
        display: flex;
        align-items: flex-start;
        gap: 20px;
        margin: 0 auto;
        width: 1200px;
        padding: 20px;
        // 左侧悬浮概览栏
        .overview-aside {
            width: 300px;
            flex-shrink: 0;
            position: sticky;
            top: 20px;
            .overview-card {
                background: white;
                border-radius: 12px;
                padding: 18px;
                box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
                .overview-title {
                    font-size: 15px;
                    font-weight: 600;
                    color: #374151;
                    margin-bottom: 14px;
                }
                .stat-line {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    padding: 10px 12px;
                    border-radius: 10px;
                    background: #FAFDF7;
                    border: 1px solid #EFF7E9;
                    &:hover {
                        background: #F3FAEC;
                    }
                    .stat-icon {
                        font-size: 22px;
                    }
                    .stat-info {
                        .stat-value {
                            font-size: 22px;
                            font-weight: 700;
                            color: #374151;
                            line-height: 1.2;
                            &.stat-value-sm {
                                font-size: 16px;
                                padding-top: 4px;
                            }
                        }
                        .stat-label {
                            margin-top: 2px;
                            font-size: 12px;
                            color: #9CA3AF;
                        }
                    }
                    & + .stat-line {
                        margin-top: 10px;
                    }
                }
            }
            .trend-card {
                margin-top: 16px;
                background: white;
                border-radius: 12px;
                padding: 16px 14px 8px;
                box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
                .trend-title {
                    font-size: 15px;
                    font-weight: 600;
                    color: #374151;
                }
                .trend-chart {
                    width: 100%;
                    height: 200px;
                }
            }
        }
        // 右侧时间线记录流
        .record-main {
            flex: 1;
            min-width: 0;
            .main-title {
                font-size: 15px;
                font-weight: 600;
                color: #374151;
                margin-bottom: 14px;
            }
            .timeline {
                position: relative;
                padding-left: 28px;
                &::before {
                    content: '';
                    position: absolute;
                    left: 8px;
                    top: 10px;
                    bottom: 10px;
                    width: 2px;
                    border-radius: 1px;
                    background: linear-gradient(180deg, #7ED321 0%, #F5A623 100%);
                    opacity: 0.35;
                }
                .timeline-item {
                    position: relative;
                    padding-bottom: 16px;
                    .node {
                        position: absolute;
                        left: -26px;
                        top: 22px;
                        width: 12px;
                        height: 12px;
                        border-radius: 50%;
                        background: linear-gradient(135deg, #7ED321 0%, #F5A623 100%);
                        border: 2px solid white;
                        box-shadow: 0 2px 6px rgba(126, 211, 33, 0.4);
                    }
                    .record-card {
                        background: white;
                        border-radius: 12px;
                        padding: 20px;
                        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
                        transition: transform 0.2s, box-shadow 0.2s;
                        &:hover {
                            transform: translateX(4px);
                            box-shadow: 0 8px 18px rgba(126, 211, 33, 0.12);
                        }
                        .record-head {
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            .record-date {
                                font-size: 17px;
                                font-weight: 600;
                                color: #374151;
                            }
                        }
                        .record-score {
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            margin: 10px 0;
                            .score-num {
                                color: #6B7280;
                                font-size: 14px;
                            }
                        }
                        .record-emotion {
                            margin-bottom: 8px;
                            color: #374151;
                        }
                        .record-line {
                            margin: 6px 0;
                            font-size: 14px;
                            color: #4B5563;
                            line-height: 1.6;
                            .line-label {
                                display: inline-block;
                                margin-right: 8px;
                                padding: 0 8px;
                                border-radius: 6px;
                                background: #F3F4F6;
                                color: #6B7280;
                                font-size: 12px;
                            }
                        }
                        .record-indicators {
                            display: flex;
                            gap: 20px;
                            margin-top: 8px;
                            font-size: 13px;
                            color: #6B7280;
                        }
                        .ai-analysis {
                            margin-top: 14px;
                            padding: 12px 14px;
                            border-radius: 8px;
                            background: #F6FBF0;
                            border: 1px solid #DFF0C8;
                            .analysis-head {
                                display: flex;
                                align-items: center;
                                gap: 10px;
                                .analysis-title {
                                    font-weight: 600;
                                    color: #5B9E1F;
                                    font-size: 13px;
                                }
                                .analysis-label {
                                    color: #374151;
                                    font-size: 13px;
                                }
                            }
                            .analysis-suggestion {
                                margin: 8px 0 0;
                                font-size: 13px;
                                color: #4B5563;
                                line-height: 1.6;
                            }
                            .analysis-tips {
                                display: flex;
                                flex-wrap: wrap;
                                gap: 8px;
                                margin-top: 8px;
                                .tip-item {
                                    padding: 2px 10px;
                                    border-radius: 10px;
                                    background: rgba(126, 211, 33, 0.12);
                                    color: #5B9E1F;
                                    font-size: 12px;
                                }
                            }
                            &.pending {
                                background: #F9FAFB;
                                border-color: #E5E7EB;
                                .analysis-title {
                                    color: #9CA3AF;
                                }
                            }
                        }
                    }
                }
            }
        }
        .empty-state {
            flex: 1;
            background: white;
            border-radius: 12px;
            padding: 60px 20px;
            text-align: center;
            color: #374151;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
            .empty-tip {
                margin: 10px 0 16px;
                font-size: 13px;
                color: #9CA3AF;
            }
            .empty-link {
                color: #2E6FC0;
                font-weight: 600;
                text-decoration: none;
                &:hover {
                    text-decoration: underline;
                }
            }
        }
    }
}
</style>
