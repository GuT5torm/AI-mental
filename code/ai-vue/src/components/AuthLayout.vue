<template>
    <div class="auth-layout">
        <div class="left-section">
            <div class="blob blob-1"></div>
            <div class="blob blob-2"></div>
            <div class="content">
                <div class="brand">
                    <svg class="brand-heart" viewBox="0 0 100 100" aria-label="爱心">
                        <defs>
                            <linearGradient id="heartGradient" x1="0" y1="0" x2="1" y2="1">
                                <stop offset="0%" stop-color="#ff8fb0" />
                                <stop offset="100%" stop-color="#ff5f85" />
                            </linearGradient>
                        </defs>
                        <path
                            d="M50 88 C20 64 8 46 8 30 C8 16 19 6 32 6 C40 6 46 10 50 16 C54 10 60 6 68 6 C81 6 92 16 92 30 C92 46 80 64 50 88 Z"
                            fill="url(#heartGradient)" />
                        <ellipse cx="32" cy="28" rx="9" ry="6" fill="rgba(255,255,255,0.55)" transform="rotate(-25 32 28)" />
                    </svg>
                    <h2 class="title">心理健康 AI 助手</h2>
                    <p class="text">你的 AI 心灵伙伴，随时倾听、温暖陪伴</p>
                </div>
                <ul class="highlights">
                    <li>
                        <el-icon class="check"><CircleCheckFilled /></el-icon>
                        <span>随时倾诉心事，AI 逐字实时回应</span>
                    </li>
                    <li>
                        <el-icon class="check"><CircleCheckFilled /></el-icon>
                        <span>情绪日记与洞察分析，更懂自己</span>
                    </li>
                    <li>
                        <el-icon class="check"><CircleCheckFilled /></el-icon>
                        <span>精选心理知识，陪伴你慢慢成长</span>
                    </li>
                </ul>
                <div class="robot">
                    <el-image class="robot-image" :src="mascotUrl" alt="AI助手" />
                </div>
                <p class="quote">"每一次倾诉，都是一次自我关照"</p>
            </div>
        </div>
        <div class="right-section">
            <router-view></router-view>
        </div> 
    </div>
</template>

<script setup>
const mascotUrl = new URL('@/assets/images/mascot.jpg', import.meta.url).href
</script>

<style scoped lang="scss">
.brand-heart {
    width: 56px;
    height: 56px;
    margin-bottom: 16px;
    filter: drop-shadow(0 6px 14px rgba(255, 95, 133, 0.45));
}

.robot-image {
    width: 100%;
    height: 100%;

    :deep(img) {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }
}

.auth-layout {
    display: flex;

    .left-section {
        position: relative;
        display: flex;
        justify-content: center;
        align-items: center;
        flex: 1;
        overflow: hidden;
        background: linear-gradient(135deg, #4c3a80 0%, #7a63b8 60%, #9b85d6 100%);
        height: 100vh;

        .blob {
            position: absolute;
            border-radius: 50%;
            filter: blur(70px);
            pointer-events: none;
        }

        .blob-1 {
            width: 360px;
            height: 360px;
            top: -120px;
            right: -100px;
            background: rgba(255, 182, 200, 0.25);
        }

        .blob-2 {
            width: 320px;
            height: 320px;
            bottom: -140px;
            left: -90px;
            background: rgba(196, 181, 253, 0.28);
        }

        .content {
            position: relative;
            z-index: 1;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            padding: 0 40px;

            .brand {
                display: flex;
                flex-direction: column;
                align-items: center;
            }

            .title {
                margin-bottom: 14px;
                font-size: 34px;
                font-weight: bold;
                color: #fff;
                letter-spacing: 2px;
            }

            .text {
                margin-bottom: 32px;
                max-width: 420px;
                font-size: 17px;
                line-height: 1.8;
                color: rgba(255, 255, 255, 0.88);
                text-align: center;
            }

            .highlights {
                list-style: none;
                margin: 0 0 36px;
                padding: 22px 30px;
                border-radius: 14px;
                background: rgba(255, 255, 255, 0.12);
                border: 1px solid rgba(255, 255, 255, 0.22);
                display: flex;
                flex-direction: column;
                gap: 14px;

                li {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    font-size: 15px;
                    color: rgba(255, 255, 255, 0.95);
                }

                .check {
                    color: #ffd98e;
                    flex-shrink: 0;
                }
            }

            .robot {
                width: 150px;
                height: 150px;
                border-radius: 50%;
                overflow: hidden;
                border: 4px solid rgba(255, 255, 255, 0.45);
                box-shadow: 0 15px 35px rgba(30, 20, 60, 0.3);
                animation: float 5s ease-in-out infinite;
            }

            .quote {
                margin-top: 24px;
                font-size: 14px;
                color: rgba(255, 255, 255, 0.7);
                letter-spacing: 1px;
            }
        }
    }

    .right-section {
        display: flex;
        justify-content: center;
        align-items: center;
        flex: 1;
        height: 100vh;
        background: linear-gradient(180deg, #f6f3fc 0%, #ffffff 45%);
    }
}

@keyframes float {
    0%,
    100% {
        transform: translateY(0);
    }

    50% {
        transform: translateY(-10px);
    }
}
</style>
