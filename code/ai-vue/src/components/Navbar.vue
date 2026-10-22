<template>
    <div class="navbar">
        <div class="flex-box">
            <el-button @click="handleCollapse">
                <el-icon>
                    <Expand />
                </el-icon>
            </el-button>
            <p class="page-title">{{ route.meta.title }}</p>
        </div>
        <div class="flex-box">
            <el-dropdown @command="handleCommand">
                <div class="flex-box">
                    <el-avatar src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
                    <p class="page-title">{{ nickname }}</p>
                    <el-icon>
                        <ArrowDown />
                    </el-icon>
                </div>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>
    </div>
</template>
<script setup>

import { ref, computed } from 'vue'
import { useLayoutStore } from '@/stores/layout.js'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { logout } from '@/api/index.js'

const router = useRouter()
const route = useRoute()

// 右上角显示当前登录用户的昵称，缺省时回退为中性名
const nickname = computed(() => {
    try {
        const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
        return info.nickname || '心理健康 AI 助手'
    } catch {
        return '心理健康 AI 助手'
    }
})

const handleCommand = (command) => {
    console.log(command)
    if (command === 'logout') {
        // console.log('退出登录')
        ElMessageBox.confirm('确定退出登录吗?', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
        }).then(() => {
            // 用户确认退出登录
            logout().catch(() => {}).finally(() => {
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                router.push('/auth/login')
            })
        })
    }

}
const handleCollapse = () => {
    useLayoutStore().toggleCollapse()
}

</script>

<style lang="scss" scoped>
.navbar {
    height: 100%;
    background: white;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 15px;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    border-bottom: 1px solid #e5e7eb;

    .page-title {
        font-size: 18px;
        font-weight: bold;
        color: #1f2937;
        margin-left: 10px;
    }

    .flex-box {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .page-title {
        margin-left: 20px;
        font-size: 26px;
        font-weight: bold;
        color: #1f2937;
    }
}
</style>