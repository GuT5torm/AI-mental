<template>
    <div class="ai-config-page">
        <div class="config-card">
            <div class="card-header">
                <div class="header-title">
                    <h2>全局 AI 配置</h2>
                    <el-tooltip placement="right" effect="dark">
                        <template #content>
                            保存后立即生效，无需重启。<br />
                            用户自行配置的 Key 仍优先于全局 Key；<br />
                            可在「用户管理」中按用户开关真实模型；<br />
                            未配置或用户开关关闭时，AI 以演示模式回复。
                        </template>
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                    </el-tooltip>
                </div>
                <el-tag :type="configured ? 'success' : 'info'" effect="light">
                    {{ configured ? '真实模型已启用' : '演示模式' }}
                </el-tag>
            </div>

            <el-alert v-if="configured" type="success" :closable="false" class="state-tip">
                <template #title>
                    当前 Key：<span class="masked">{{ maskedKey }}</span>
                    <span v-if="updatedAt" class="updated-at">（更新于 {{ updatedAt }}）</span>
                </template>
            </el-alert>
            <el-alert v-else type="info" :closable="false" class="state-tip"
                title="尚未配置全局 Key，AI 以演示模式回复预设内容。配置并保存后即自动接入真实模型。" />

            <el-form :model="form" label-width="90px" class="config-form">
                <el-form-item label="API Key" required>
                    <el-input v-model="form.apiKey" type="password" show-password maxlength="200"
                        :placeholder="configured ? '已保存 ' + maskedKey + '，重新输入可替换' : 'OpenAI 兼容服务的 API Key'" />
                </el-form-item>
                <el-form-item label="Base URL">
                    <el-input v-model="form.baseUrl" maxlength="255" placeholder="选填，默认 https://api.deepseek.com" />
                </el-form-item>
                <el-form-item label="模型名">
                    <el-input v-model="form.model" maxlength="100" placeholder="选填，默认 deepseek-chat" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
                    <el-button :loading="testing" @click="handleTest">测试连接</el-button>
                    <el-popover placement="top" width="240" trigger="hover"
                        content="清空全局 Key 后，未自配 Key 的用户将回到演示模式。">
                        <template #reference>
                            <el-button v-if="configured" type="danger" plain :loading="clearing"
                                @click="handleClear">恢复演示模式</el-button>
                        </template>
                    </el-popover>
                </el-form-item>
            </el-form>
        </div>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { getAdminAiConfig, saveAdminAiConfig, deleteAdminAiConfig, testAdminAiConfig } from '@/api/index.js'

const configured = ref(false)
const maskedKey = ref('')
const updatedAt = ref('')
const saving = ref(false)
const testing = ref(false)
const clearing = ref(false)
const form = reactive({ apiKey: '', baseUrl: '', model: '' })

const loadConfig = async () => {
    try {
        const data = await getAdminAiConfig()
        configured.value = !!data.configured
        maskedKey.value = data.maskedKey || ''
        updatedAt.value = data.updatedAt || ''
        form.baseUrl = data.baseUrl || ''
        form.model = data.model || ''
        form.apiKey = ''
    } catch (err) {
        ElMessage.error(err?.response?.data?.msg || '配置加载失败')
    }
}

const buildPayload = () => {
    const apiKey = form.apiKey.trim()
    if (!apiKey) {
        ElMessage.warning(configured.value ? '请输入新的 API Key 以替换现有配置' : '请输入 API Key')
        return null
    }
    return {
        apiKey,
        baseUrl: form.baseUrl.trim() || undefined,
        model: form.model.trim() || undefined
    }
}

const handleSave = async () => {
    const payload = buildPayload()
    if (!payload) return
    saving.value = true
    try {
        await saveAdminAiConfig(payload)
        ElMessage.success('已保存，真实模型对全部用户即时生效')
        loadConfig()
    } catch (err) {
        ElMessage.error(err?.response?.data?.msg || '保存失败')
    } finally {
        saving.value = false
    }
}

const handleTest = async () => {
    const payload = buildPayload()
    if (!payload) return
    testing.value = true
    try {
        const data = await testAdminAiConfig(payload)
        ElMessage.success(`连接成功，命中模型 ${data.model}`)
    } catch (err) {
        ElMessage.error(err?.response?.data?.msg || '连接失败')
    } finally {
        testing.value = false
    }
}

const handleClear = () => {
    ElMessageBox.confirm('清空后未自配 Key 的用户将回到演示模式，确认清空全局 Key 吗？', '确认', {
        confirmButtonText: '确认清空',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        clearing.value = true
        try {
            await deleteAdminAiConfig()
            ElMessage.success('已清空全局 Key')
            loadConfig()
        } catch (err) {
            ElMessage.error(err?.response?.data?.msg || '清空失败')
        } finally {
            clearing.value = false
        }
    }).catch(() => {})
}

onMounted(loadConfig)
</script>

<style lang="scss" scoped>
.ai-config-page {
    .config-card {
        background: #fff;
        border-radius: 12px;
        padding: 24px 28px;
        box-shadow: 0 1px 4px rgba(21, 34, 50, 0.06);
        max-width: 640px;
    }

    .card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 18px;

        .header-title {
            display: flex;
            align-items: center;
            gap: 8px;

            h2 {
                margin: 0;
                font-size: 18px;
                color: #1f2d3d;
            }

            .help-icon {
                color: #a8abb2;
                cursor: help;
                font-size: 16px;

                &:hover {
                    color: #6a52c9;
                }
            }
        }
    }

    .state-tip {
        margin-bottom: 18px;

        .masked {
            font-family: monospace;
            font-weight: 600;
        }

        .updated-at {
            color: #909399;
            font-size: 12px;
        }
    }

    .config-form {
        max-width: 480px;
    }
}
</style>
