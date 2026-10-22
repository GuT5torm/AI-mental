<template>
    <div class="home-page">
        <!-- 首屏 Hero -->
        <section class="hero">
            <div class="blob blob-1"></div>
            <div class="blob blob-2"></div>
            <div class="blob blob-3"></div>
            <div class="hero-inner">
                <div class="hero-text">
                    <span class="hero-badge">
                        <span class="dot"></span>
                        AI 心理陪伴助手 · 温暖在线
                    </span>
                    <h1 class="hero-title">
                        把心事说出来，<br />
                        让 AI 陪你<span class="highlight">慢慢聊</span>
                    </h1>
                    <p class="hero-desc">
                        深夜的焦虑、说不出口的压力、无处安放的情绪——在这里，有一位随时在线的
                        AI 伙伴，认真倾听你说的每一句话，陪你梳理心情、找回内心的平静。
                    </p>
                    <div class="hero-actions">
                        <el-button class="btn-primary" size="large" round @click="goToConsultations">
                            开始倾诉，获得陪伴
                        </el-button>
                        <el-button class="btn-ghost" size="large" round @click="goToEmotionDiary">
                            记录心情，释放情感
                        </el-button>
                    </div>
                    <div class="hero-meta">
                        <span>7×24 小时在线</span>
                        <i>|</i>
                        <span>逐字实时回复</span>
                        <i>|</i>
                        <span>温柔不评判</span>
                    </div>
                </div>
                <div class="hero-visual">
                    <div class="orbit">
                        <el-image class="mascot-image" :src="mascotUrl" alt="AI 伙伴" />
                    </div>
                    <div class="mood mood-1">
                        <el-image :src="moodHappy" alt="开心" />
                    </div>
                    <div class="mood mood-2">
                        <el-image :src="moodCalm" alt="平静" />
                    </div>
                    <div class="mood mood-3">
                        <el-image :src="moodAnxious" alt="焦虑" />
                    </div>
                    <div class="mood mood-4">
                        <el-image :src="moodSad" alt="悲伤" />
                    </div>
                </div>
            </div>
        </section>
    </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goToConsultations = () => {
    router.push('/consultations')
}

const goToEmotionDiary = () => {
    router.push('/emotion-diary')
}

const mascotUrl = new URL('@/assets/images/mascot.jpg', import.meta.url).href
const moodHappy = new URL('@/assets/images/开心.png', import.meta.url).href
const moodCalm = new URL('@/assets/images/平静.png', import.meta.url).href
const moodAnxious = new URL('@/assets/images/焦虑.png', import.meta.url).href
const moodSad = new URL('@/assets/images/悲伤.png', import.meta.url).href
</script>

<style scoped lang="scss">
.home-page {
    color: #1f2937;
}

/* ---------- Hero ---------- */
.hero {
    position: relative;
    overflow: hidden;
    background: linear-gradient(135deg, #4c3a80 0%, #7a63b8 55%, #9b85d6 100%);
    color: #fff;
    min-height: calc(100vh - 140px);
    display: flex;
    align-items: center;
    padding: 60px 0;

    .blob {
        position: absolute;
        border-radius: 50%;
        filter: blur(70px);
        pointer-events: none;
    }

    .blob-1 {
        width: 420px;
        height: 420px;
        top: -140px;
        right: -80px;
        background: rgba(255, 182, 200, 0.28);
    }

    .blob-2 {
        width: 360px;
        height: 360px;
        bottom: -160px;
        left: -100px;
        background: rgba(255, 255, 255, 0.14);
    }

    .blob-3 {
        width: 220px;
        height: 220px;
        top: 30%;
        left: 38%;
        background: rgba(196, 181, 253, 0.3);
    }

    .hero-inner {
        position: relative;
        z-index: 1;
        max-width: 1200px;
        margin: 0 auto;
        padding: 0 24px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 40px;
        width: 100%;
    }

    .hero-badge {
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
            background: #ffb6c8;
            box-shadow: 0 0 8px rgba(255, 182, 200, 0.9);
        }
    }

    .hero-title {
        font-size: 48px;
        line-height: 1.25;
        font-weight: 700;
        margin: 24px 0 18px;

        .highlight {
            color: #ffd98e;
            position: relative;
        }
    }

    .hero-desc {
        max-width: 480px;
        font-size: 17px;
        line-height: 1.9;
        color: rgba(255, 255, 255, 0.88);
        margin: 0 0 32px;
    }

    .hero-actions {
        display: flex;
        gap: 16px;
        margin-bottom: 28px;

        .btn-primary {
            background: linear-gradient(135deg, #ffd98e 0%, #ffb86b 100%);
            border: none;
            color: #4a3a5c;
            font-weight: 600;
            padding: 20px 28px;

            &:hover {
                background: linear-gradient(135deg, #ffe2a8 0%, #ffc285 100%);
                color: #4a3a5c;
            }
        }

        .btn-ghost {
            background: transparent;
            border: 1px solid rgba(255, 255, 255, 0.65);
            color: #fff;
            padding: 20px 28px;

            &:hover {
                background: rgba(255, 255, 255, 0.14);
                border-color: #fff;
                color: #fff;
            }
        }
    }

    .hero-meta {
        display: flex;
        align-items: center;
        gap: 14px;
        font-size: 14px;
        color: rgba(255, 255, 255, 0.75);

        i {
            font-style: normal;
            opacity: 0.4;
        }
    }

    .hero-visual {
        position: relative;
        width: 340px;
        height: 340px;
        flex-shrink: 0;

        .orbit {
            width: 260px;
            height: 260px;
            margin: 40px auto 0;
            border-radius: 50%;
            overflow: hidden;
            border: 4px solid rgba(255, 255, 255, 0.45);
            box-shadow: 0 20px 45px rgba(30, 20, 60, 0.35);
            animation: float 5s ease-in-out infinite;
        }

        .mascot-image {
            width: 100%;
            height: 100%;

            :deep(img) {
                width: 100%;
                height: 100%;
                object-fit: cover;
            }
        }

        .mood {
            position: absolute;
            width: 64px;
            height: 64px;
            padding: 8px;
            border-radius: 50%;
            background: #fff;
            box-shadow: 0 10px 24px rgba(0, 0, 0, 0.15);
            animation: float 4s ease-in-out infinite;

            :deep(img) {
                width: 100%;
                height: 100%;
                object-fit: contain;
            }
        }

        .mood-1 {
            top: 0;
            left: 8px;
        }

        .mood-2 {
            top: 60px;
            right: -10px;
            animation-delay: 0.8s;
        }

        .mood-3 {
            bottom: 30px;
            left: -14px;
            animation-delay: 1.6s;
        }

        .mood-4 {
            bottom: -12px;
            right: 56px;
            animation-delay: 2.4s;
        }
    }
}

@keyframes float {
    0%,
    100% {
        transform: translateY(0);
    }

    50% {
        transform: translateY(-12px);
    }
}

/* ---------- 响应式 ---------- */
@media (max-width: 992px) {
    .hero-inner {
        flex-direction: column;
        text-align: center;
    }

    .hero-desc {
        margin-left: auto;
        margin-right: auto;
    }

    .hero-actions {
        justify-content: center;
    }

    .hero-meta {
        justify-content: center;
    }
}
</style>
