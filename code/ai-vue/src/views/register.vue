<template>
    <div class="container">
        <div class="title">
            <div class="back-home">
                <el-icon><Back /></el-icon>   
                <p><router-link to="/auth/login">返回登录页</router-link></p>
           </div> 
            <div class="title-text">
                <h2>创建您的账户</h2>
                <p>请填写注册信息</p>
            </div>
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
                    <el-button type="primary" size="large" class="btn" @click="submitForm(submitFormRef)">注册</el-button>
                </el-form-item>
            </el-form>
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
    width: 384px;

    .flex-box {
        display: flex;
        align-items: center;
    }

    .title {
        .title-text {
            text-align: center;

            h2 {
                font-size: 36px;
                margin-bottom: 10px;
            }

            p {
                font-size: 18px;
                color: #6b7280;
            }
        }
    }

    .form-container {
        margin-top: 30px;

        .btn {
            margin-top: 40px;
            width: 100%;
        }

        .footer {
            padding: 30px;
            text-align: center;
        }
    }
}
</style>