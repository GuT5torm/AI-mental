<template>
    <div class="knowledge-container">
        <div class="header-section">
            <div class="blob blob-1"></div>
            <div class="blob blob-2"></div>
            <div class="header-inner">
                <div class="header-badge">
                    <span class="dot"></span>
                    心理科普 · 免费阅读
                </div>
                <div class="header-content">
                    <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
                    <h1>心灵课堂</h1>
                </div>
                <p class="header-desc">精选心理健康科普文章，帮您更好地理解和管理自己的情绪</p>
                <div class="header-meta">
                    <span>精选文章</span>
                    <i>|</i>
                    <span>专业解读</span>
                    <i>|</i>
                    <span>持续更新</span>
                </div>
            </div>
        </div>
        <div class="content">
            <!-- 左侧菜单 -->
            <div class="recommend-section">
                <div class="section-title">
                    <el-icon><Histogram /></el-icon>
                    推荐阅读
                </div>
                <div class="recommend-list">
                    <div v-for="(item, index) in recommendList" :key="item.id" class="recommend-item"
                        @click="goToArticle(item.id)">
                        <span class="rank" :class="'rank-' + (index + 1)">{{ index + 1 }}</span>
                        <div class="recommend-body">
                            <h4>{{ item.title }}</h4>
                            <p class="read-count">
                                <el-icon>
                                    <Platform />
                                </el-icon>
                                {{ item.readCount }} 人读过
                            </p>
                        </div>
                    </div>
                </div>
            </div>
            <!-- 右侧内容 -->
            <div class="article-panel" v-loading="loading">
                <template v-if="articleList.length">
                    <!-- 置顶精选大卡 -->
                    <div v-if="featured" class="featured-card" @click="goToArticle(featured.id)">
                        <div class="featured-badge">
                            <el-icon><Star /></el-icon>
                            置顶精选
                        </div>
                        <div class="featured-body">
                            <div class="featured-info">
                                <h2>{{ featured.title }}</h2>
                                <div class="featured-meta">
                                    <span class="meta-item"><el-icon><Avatar /></el-icon>{{ featured.authorName }}</span>
                                    <span class="meta-item"><el-icon><List /></el-icon>{{ dayjs(featured.updatedAt).format('YYYY-MM-DD') }}</span>
                                    <span class="meta-item"><el-icon><Platform /></el-icon>{{ featured.readCount }} 人阅读</span>
                                </div>
                                <span class="go-read">阅读全文 →</span>
                            </div>
                            <el-image class="featured-cover" :src="getImage(featured.coverImage)">
                                <template #error>
                                    <div class="cover-fallback">
                                        <el-icon :size="34"><Reading /></el-icon>
                                    </div>
                                </template>
                            </el-image>
                        </div>
                    </div>
                    <!-- 两列封面网格 -->
                    <div class="article-grid">
                        <div v-for="item in restArticles" :key="item.id" class="grid-card" @click="goToArticle(item.id)">
                            <el-image class="grid-cover" :src="getImage(item.coverImage)">
                                <template #error>
                                    <div class="cover-fallback">
                                        <el-icon :size="30"><Reading /></el-icon>
                                    </div>
                                </template>
                            </el-image>
                            <div class="grid-body">
                                <div class="grid-title">
                                    <h3>{{ item.title }}</h3>
                                </div>
                                <div class="grid-meta">
                                    <el-tag class="category-tag" :style="categoryStyle(item.categoryName)" effect="light" size="small">{{ item.categoryName }}</el-tag>
                                    <span class="meta-item"><el-icon><Avatar /></el-icon>{{ item.authorName }}</span>
                                    <span class="meta-item"><el-icon><Platform /></el-icon>{{ item.readCount }}</span>
                                </div>
                                <div class="grid-foot">
                                    <span>{{ dayjs(item.updatedAt).format('YYYY-MM-DD') }}</span>
                                    <span class="go-read">阅读全文 →</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </template>
                <div v-else-if="!loading" class="empty-state">
                    <el-icon :size="42" color="#9CA3AF"><Reading /></el-icon>
                    <p>暂无文章</p>
                </div>
            </div>
        </div>
        <!-- 分页 -->
        <div class="pagination-wrapper">
            <el-pagination :page-size="pagination.size" layout="prev, pager, next"
                :total="pagination.total" @change="handleChange" />
        </div>
    </div>
</template>

<script setup>

import { dayjs, ElMessage } from 'element-plus'
import { ref, reactive, onMounted } from 'vue'
import { getKnowledgeList } from '@/api/frontend'
import { fileBaseUrl } from '@/config/index.js'
import { useRouter } from 'vue-router'



import iconUrl from '@/assets/images/book.png'
import { Avatar, Histogram, List, Platform, Reading, Star } from '@element-plus/icons-vue'
import { computed } from 'vue'

const router = useRouter()

// 推荐阅读列表
const recommendList = ref([])

// 列表加载状态
const loading = ref(false)

// 右侧列表数据
const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const articleList = ref([])

// 置顶文章（后台设置 is_top）作为精选大卡展示，其余进网格；无置顶时全部走网格
const featured = computed(() => {
    const first = articleList.value[0]
    return first && first.isTop === 1 ? first : null
})
const restArticles = computed(() => featured.value ? articleList.value.slice(1) : articleList.value)
// 获取列表数据
const getPageList = () => {
    const params = {
        sortField: 'publishedAt',
        sortDirection: 'desc',
        ...pagination
    }
    loading.value = true
    getKnowledgeList(params).then(res => {
        articleList.value = res.records
        pagination.total = res.total
    }).catch(err => {
        ElMessage.error(err?.response?.data?.msg || '文章列表加载失败')
    }).finally(() => {
        loading.value = false
    })
}

const defaultCover = new URL('@/assets/images/article-cover.jpg', import.meta.url).href

const getImage = (url) => {
    return url ? fileBaseUrl + url : defaultCover

}

// 分类标签多彩配色：同名分类固定同色，各分类颜色错开
const categoryPalette = [
    { bg: '#EEF5FF', border: '#C8E0F5', text: '#2E6FC0' },
    { bg: '#EFFAF0', border: '#C4E8C9', text: '#3E8E4C' },
    { bg: '#FFF4EC', border: '#F5D4BC', text: '#C06B2C' },
    { bg: '#F5F0FF', border: '#DCCDF5', text: '#7A4FD0' },
    { bg: '#FDF0F5', border: '#F2CADA', text: '#C2447F' },
    { bg: '#EDFAFA', border: '#BFE8E8', text: '#2B8C8C' }
]
const categoryStyle = (name) => {
    let hash = 0
    for (const ch of String(name || '')) hash = (hash * 31 + ch.codePointAt(0)) % 997
    return {
        backgroundColor: categoryPalette[hash % categoryPalette.length].bg,
        borderColor: categoryPalette[hash % categoryPalette.length].border,
        color: categoryPalette[hash % categoryPalette.length].text
    }
}

const handleChange = (page) => {
    pagination.currentPage = page
    getPageList()
}

//跳转到详情
const goToArticle = (id) => {
    router.push(`/knowledge/article/${id}`)
}


onMounted(() => {
    // 获取推荐阅读列表
    const params = {
        sortField: 'readCount',
        sortDirection: 'desc',
        currentPage: 1,
        size: 5
    }
    getPageList()
    getKnowledgeList(params).then(res => {
        recommendList.value = res.records
    }).catch(err => {
        ElMessage.error(err?.response?.data?.msg || '推荐阅读加载失败')
    })
})
</script>

<style lang="scss" scoped>
.knowledge-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    min-height: 100vh;

    .flex-box {
        display: flex;
        align-items: center;

        span {
            margin-left: 8px;
        }
    }

    .header-section {
        position: relative;
        overflow: hidden;
        background: linear-gradient(135deg, #4c3a80 0%, #7a63b8 55%, #9b85d6 100%);
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
            background: rgba(255, 255, 255, 0.14);
            border: 1px solid rgba(255, 255, 255, 0.25);
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
            background: rgba(255, 182, 200, 0.3);
            top: -160px;
            right: 40px;
        }

        .blob-2 {
            width: 260px;
            height: 260px;
            background: rgba(255, 255, 255, 0.18);
            bottom: -140px;
            left: 30%;
        }
    }

    .content {
        display: flex;
        gap: 20px;
        align-items: flex-start;
        margin: 0 auto;
        width: 1200px;
        padding: 20px;

        .recommend-section {
            width: 280px;
            background: white;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
            padding: 18px 15px;

            .section-title {
                font-size: 15px;
                font-weight: 600;
                color: #374151;
                margin-bottom: 14px;
                display: flex;
                align-items: center;
                gap: 6px;
                color: #4c3a80;
            }

            .recommend-list {
                display: flex;
                flex-direction: column;
                gap: 6px;

                .recommend-item {
                    display: flex;
                    align-items: flex-start;
                    gap: 10px;
                    padding: 10px;
                    border-radius: 8px;
                    cursor: pointer;
                    transition: background 0.2s;

                    &:hover {
                        background: #F5F3FB;

                        h4 {
                            color: #4c3a80;
                        }
                    }

                    .rank {
                        flex-shrink: 0;
                        width: 20px;
                        height: 20px;
                        border-radius: 6px;
                        background: #EDE9F7;
                        color: #8a6fd8;
                        font-size: 12px;
                        font-weight: 600;
                        display: flex;
                        align-items: center;
                        justify-content: center;

                        &.rank-1 {
                            background: #8a6fd8;
                            color: white;
                        }

                        &.rank-2 {
                            background: #a58fe3;
                            color: white;
                        }

                        &.rank-3 {
                            background: #c3b2ee;
                            color: white;
                        }
                    }

                    .recommend-body {
                        min-width: 0;

                        h4 {
                            font-size: 14px;
                            font-weight: 500;
                            color: #374151;
                            line-height: 1.5;
                            display: -webkit-box;
                            -webkit-line-clamp: 2;
                            -webkit-box-orient: vertical;
                            overflow: hidden;
                            transition: color 0.2s;
                        }

                        .read-count {
                            margin-top: 6px;
                            font-size: 12px;
                            color: #9CA3AF;
                            display: flex;
                            align-items: center;
                            gap: 4px;
                        }
                    }
                }
            }
        }

        .article-panel {
            flex: 1;
            min-width: 0;
            min-height: 300px;

            .featured-card {
                position: relative;
                background: white;
                border-radius: 14px;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
                border: 1px solid #EFE9FB;
                padding: 22px;
                margin-bottom: 20px;
                cursor: pointer;
                transition: transform 0.2s, box-shadow 0.2s;

                &:hover {
                    transform: translateY(-3px);
                    box-shadow: 0 10px 26px rgba(76, 58, 128, 0.16);

                    h2 {
                        color: #4c3a80;
                    }

                    .go-read {
                        color: #8a6fd8;
                    }
                }

                .featured-badge {
                    position: absolute;
                    top: -12px;
                    left: 20px;
                    display: inline-flex;
                    align-items: center;
                    gap: 5px;
                    padding: 4px 14px;
                    border-radius: 999px;
                    background: linear-gradient(135deg, #4c3a80 0%, #8a6fd8 100%);
                    color: #fff;
                    font-size: 12px;
                    font-weight: 600;
                    letter-spacing: 1px;
                    box-shadow: 0 4px 10px rgba(76, 58, 128, 0.3);
                }

                .featured-body {
                    display: flex;
                    gap: 20px;
                    align-items: center;
                }

                .featured-info {
                    flex: 1;
                    min-width: 0;

                    h2 {
                        font-size: 20px;
                        color: #111827;
                        line-height: 1.45;
                        margin: 0;
                        transition: color 0.2s;
                        display: -webkit-box;
                        -webkit-line-clamp: 2;
                        -webkit-box-orient: vertical;
                        overflow: hidden;
                    }

                    .featured-meta {
                        display: flex;
                        gap: 18px;
                        margin-top: 14px;
                        color: #6B7280;
                        font-size: 13px;

                        .meta-item {
                            display: inline-flex;
                            align-items: center;
                            gap: 5px;
                        }
                    }

                    .go-read {
                        display: inline-block;
                        margin-top: 16px;
                        font-size: 13px;
                        font-weight: 600;
                        color: #B4A5E3;
                        transition: color 0.2s;
                    }
                }

                .featured-cover {
                    width: 300px;
                    height: 180px;
                    flex-shrink: 0;
                    border-radius: 10px;
                    overflow: hidden;

                    :deep(img) {
                        width: 100%;
                        height: 100%;
                        object-fit: cover;
                        transition: transform 0.3s;
                    }

                    &:hover :deep(img) {
                        transform: scale(1.05);
                    }
                }
            }

            .article-grid {
                display: grid;
                grid-template-columns: repeat(2, 1fr);
                gap: 16px;

                .grid-card {
                    background: white;
                    border-radius: 14px;
                    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
                    overflow: hidden;
                    cursor: pointer;
                    transition: transform 0.2s, box-shadow 0.2s;

                    &:hover {
                        transform: translateY(-3px);
                        box-shadow: 0 10px 26px rgba(76, 58, 128, 0.16);

                        h3 {
                            color: #4c3a80;
                        }

                        .go-read {
                            color: #8a6fd8;
                        }
                    }

                    .grid-cover {
                        width: 100%;
                        height: 150px;
                        display: block;

                        :deep(img) {
                            width: 100%;
                            height: 100%;
                            object-fit: cover;
                            transition: transform 0.3s;
                        }

                        &:hover :deep(img) {
                            transform: scale(1.05);
                        }
                    }

                    .grid-body {
                        padding: 14px 16px 16px;

                        .grid-title h3 {
                            font-size: 16px;
                            color: #111827;
                            line-height: 1.5;
                            margin: 0;
                            transition: color 0.2s;
                            display: -webkit-box;
                            -webkit-line-clamp: 2;
                            -webkit-box-orient: vertical;
                            overflow: hidden;
                            min-height: 48px;
                        }

                        .grid-meta {
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            margin-top: 10px;

                            .meta-item {
                                display: inline-flex;
                                align-items: center;
                                gap: 4px;
                                color: #9CA3AF;
                                font-size: 12px;
                            }
                        }

                        .grid-foot {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            margin-top: 12px;
                            color: #9CA3AF;
                            font-size: 12px;

                            .go-read {
                                font-size: 13px;
                                font-weight: 600;
                                color: #B4A5E3;
                                transition: color 0.2s;
                            }
                        }
                    }
                }
            }

            .cover-fallback {
                width: 100%;
                height: 100%;
                display: flex;
                align-items: center;
                justify-content: center;
                background: #F3F0FA;
                color: #8a6fd8;
            }

            .empty-state {
                background: white;
                border-radius: 14px;
                padding: 60px 20px;
                text-align: center;
                color: #6B7280;

                p {
                    margin-top: 10px;
                }
            }
        }
    }

    .pagination-wrapper {
        display: flex;
        justify-content: center;
        padding-bottom: 30px;
    }
}
</style>
