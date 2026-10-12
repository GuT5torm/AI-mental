import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

//创建axios实例
const service = axios.create({
    baseURL: '/api',
    timeout: 5000, //请求的超时时间

})

//请求拦截器
service.interceptors.request.use(
    (config) => {
        //在发送请求之前做什么
        const token = localStorage.getItem('token')
        if (token) {
            config.headers[ 'token' ] = token
        }
        return config
    },
    (error) => {
        //请求错误做什么
        return Promise.reject(error)
    }
)

//响应拦截器
service.interceptors.response.use(
    (response) => {
        //对响应数据做点什么
        const { data, config } = response
        // 统一处理业务状态码，只有明确成功才把响应交给业务页面。
        if (String(data.code) === '200') {
            return data.data
        }

        const isLoginRequest = config.url?.includes('login')
        const token = localStorage.getItem('token')
        if (String(data.code) === '-1' && token && !isLoginRequest) {
            ElMessage.error(data.msg || '登录过期，请重新登录')
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            window.location.href = '/auth/login'
        }

        return Promise.reject({ response: { data } })
    },
    (error) => {
        //对响应错误数据做点什么
        return Promise.reject(error)
    }
)

export default service