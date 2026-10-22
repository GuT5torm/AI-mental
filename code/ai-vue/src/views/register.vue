<template>
    <div class="container">
        <div class="back-home">
            <el-icon><Back /></el-icon>
            <router-link to="/auth/login" class="back-link">返回登录页</router-link>
        </div>
        <div class="title-text">
            <h2>创建你的账户</h2>
            <p>几分钟即可开启第一段心灵对话</p>
        </div>
        <div class="form-container">
            <el-form label-position="top" :model="formData" :rules="rules" ref="submitFormRef">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="formData.username" placeholder="请输入用户名" size="large" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="formData.email" placeholder="请输入邮箱" size="large" />
                </el-form-item>
                <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="formData.nickname" placeholder="请输入昵称(可选)" size="large" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="formData.phone" placeholder="请输入手机号(可选)" size="large" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" type="password" placeholder="请输入密码" size="large"
                        show-password />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="formData.confirmPassword" type="password" placeholder="请确认密码" size="large"
                        show-password />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" size="large" class="btn" round
                        @click="submitForm(submitFormRef)">注 册</el-button>
                </el-form-item>
            </el-form>
            <div class="footer">
                <p>已有账户？<router-link to="/auth/login">直接登录</router-link></p>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { register } from '@/api/frontend.js'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const formData = reactive({
    username: '',
    email: '',
    nickname: '',
    phone: '',
    password: '',
    confirmPassword: '',
    gender: 0,
    userType: 1 // 1: 普通用户, 2: 管理员
})

const rules = reactive({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
    ],
    email: [
        { required: true, message: '请输入邮箱', trigger: 'blur' },
        { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
    ],
    confirmPassword: [
        { required: true, message: '请确认密码', trigger: 'blur' },
        {
            validator: (_rule, value, callback) => {
                if (value !== formData.password) {
                    callback(new Error('两次输入的密码不一致'))
                } else {
                    callback()
                }
            },
            trigger: 'blur'
        }
    ]
})

//表单提交
const router = useRouter()
const submitFormRef = ref(null)

// 从后端响应中提取可读的注册失败原因，避免只显示笼统的“注册失败”。
const getRegisterErrorMessage = (error) => {
    const responseData = error?.response?.data
    return responseData?.msg
        || responseData?.message
        || responseData?.data?.msg
        || responseData?.data?.message
        || (error?.response?.status ? `注册失败(HTTP ${error.response.status})` : '')
        || (error?.code === 'ECONNABORTED' ? '注册请求超时，请检查网络后重试' : '')
        || '注册失败，请稍后重试'
}

const submitForm = async (formEl) => {
    if (!formEl) return

    const valid = await formEl.validate().catch(() => false)
    if (!valid) return

    try {
        // 只提交后端注册接口需要的字段，避免空字符串触发数据库约束。
        const payload = {
            username: formData.username.trim(),
            email: formData.email.trim(),
            password: formData.password,
            userType: 1
        }

        if (formData.nickname.trim()) payload.nickname = formData.nickname.trim()
        if (formData.phone.trim()) payload.phone = formData.phone.trim()

        await register(payload)
        ElMessage.success('注册成功')
        router.push('/auth/login')
    } catch (error) {
        ElMessage.error(getRegisterErrorMessage(error))
    }
}

</script>
<style lang="scss" scoped>
.container {
    width: 400px;
    max-height: calc(100vh - 48px);
    overflow-y: auto;
    padding: 36px 44px 24px;
    background: #fff;
    border-radius: 20px;
    box-shadow: 0 20px 50px rgba(76, 58, 128, 0.14);
    border: 1px solid #eee9f7;

    .back-home {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-size: 14px;

        .back-link {
            color: #6b7280;
            text-decoration: none;
            transition: color 0.2s ease;

            &:hover {
                color: #7c5cd6;
            }
        }
    }

    .title-text {
        text-align: center;
        margin: 22px 0 26px;

        h2 {
            font-size: 30px;
            font-weight: 700;
            color: #1f2937;
            margin: 0 0 10px;
        }

        p {
            font-size: 15px;
            color: #6b7280;
            margin: 0;
        }
    }

    .form-container {
        .btn {
            margin-top: 12px;
            width: 100%;
            height: 46px;
            font-size: 16px;
            font-weight: 600;
            background: linear-gradient(135deg, #9d82e8 0%, #7c5cd6 100%);
            border: none;

            &:hover {
                background: linear-gradient(135deg, #ab93ec 0%, #8a6cde 100%);
            }
        }

        .footer {
            padding: 18px 0 4px;
            text-align: center;
            font-size: 14px;
            color: #6b7280;

            a {
                color: #7c5cd6;
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
