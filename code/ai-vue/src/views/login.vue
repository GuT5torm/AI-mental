<template>
    <div class="container">
        <div class="back-home">
            <el-icon><Back /></el-icon>
            <router-link to="/" class="back-link">返回首页</router-link>
        </div>
        <div class="title-text">
            <h2>欢迎回来</h2>
            <p>登录后，继续和你的 AI 伙伴聊聊近况</p>
        </div>
        <div class="form-container">
            <el-form
            ref="ruleFormRef"
            :model="formData"
            :rules="rules"
            label-position="top"
            >
            <el-form-item label="用户名或邮箱" prop="username">
                <el-input v-model="formData.username" size="large" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password />
            </el-form-item>
            <el-button class="btn" size="large" type="primary" round @click="submitForm(ruleFormRef)">登 录</el-button>
            </el-form>
            <div class="footer">
                <p>还没有账户？<router-link to="/auth/register">免费注册</router-link></p>
            </div>
        </div>
    </div>
</template>
<script setup>
import {ref,reactive} from 'vue'
import { login } from '@/api/index'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const ruleFormRef = ref()

const formData = reactive({
    username: '',
    password: ''
})
const rules = reactive({
    username: [
        { required: true, message:'请输入用户名', trigger: 'blur'}
    ],
    password: [
        { required: true, message:'请输入密码', trigger:'blur' }
    ]
})

//登录
const router = useRouter()

const submitForm = async (formEl) =>{
    if (!formEl) return
    const valid = await formEl.validate().catch(() => false)
    if (!valid) return

    try {
        const data = await login(formData)
        if (!data?.token || !data?.userInfo) {
            throw new Error('登录响应缺少用户信息')
        }

        localStorage.setItem('token', data.token)
        localStorage.setItem('userInfo', JSON.stringify(data.userInfo))

        if (Number(data.userInfo.userType) === 2) {
            await router.push('/back/dashboard')
        } else {
            await router.push('/')
        }
    } catch (error) {
        ElMessage.error(error?.response?.data?.msg || error?.message || '登录失败，请检查用户名和密码')
    }
}

</script>
<style scoped lang="scss">
    .container{
        width: 400px;
        padding: 44px 44px 30px;
        background: #fff;
        border-radius: 20px;
        box-shadow: 0 20px 50px rgba(76, 58, 128, 0.14);
        border: 1px solid #eee9f7;

        .back-home{
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-size: 14px;

            .back-link{
                color: #6b7280;
                text-decoration: none;
                transition: color 0.2s ease;

                &:hover{
                    color: #7c5cd6;
                }
            }
        }

        .title-text{
            text-align: center;
            margin: 26px 0 34px;

            h2{
                font-size: 32px;
                font-weight: 700;
                color: #1f2937;
                margin: 0 0 10px;
            }
            p{
                font-size: 15px;
                color: #6b7280;
                margin: 0;
            }
        }
        .form-container{
            .btn{
                margin-top: 24px;
                width: 100%;
                height: 46px;
                font-size: 16px;
                font-weight: 600;
                background: linear-gradient(135deg, #9d82e8 0%, #7c5cd6 100%);
                border: none;

                &:hover{
                    background: linear-gradient(135deg, #ab93ec 0%, #8a6cde 100%);
                }
            }
            .footer{
                padding: 26px 0 4px;
                text-align: center;
                font-size: 14px;
                color: #6b7280;

                a{
                    color: #7c5cd6;
                    font-weight: 600;
                    text-decoration: none;

                    &:hover{
                        text-decoration: underline;
                    }
                }
            }
        }
    }
</style>
