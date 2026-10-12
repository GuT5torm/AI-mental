import { createRouter, createWebHistory } from 'vue-router'
import BackendLayout from '@/components/BackendLayout.vue'
import AuthLayout from '@/components/AuthLayout.vue'
import Dashboard from '@/views/dashboard.vue'
import Knowledge from '@/views/knowledge.vue'
import Consultation from '@/views/consultation.vue'
import Emotional from '@/views/emotional.vue'
import component from 'element-plus/es/components/tree-select/src/tree-select-option.mjs'
import FrontendLayout from '@/components/FrontendLayout.vue'

const backendRoutes = [
    {
        path: '/back',
        redirect: '/back/dashboard',
        component: BackendLayout,
        children: [
            {
                path: 'dashboard',
                component: Dashboard,
                meta: { title: '数据分析', icon: 'PieChart' }
            },
            {
                path: 'knowledge',
                component: Knowledge,
                meta: { title: '知识文章', icon: 'Document' }
            },
            {
                path: 'consultation',
                component: Consultation,
                meta: { title: '咨询记录', icon: 'Message' }
            },
            {
                path: 'emotional',
                component: Emotional,
                meta: { title: '情绪日志', icon: 'User' }
            }
        ]
    },
    {
        path: '/auth',
        component: AuthLayout,
        children:[
            {
                path: 'login',
                component: () => import('@/views/login.vue'),
                meta:{
                    title: '登录',
                    icon: 'login'
                }
                
            },
            {
                path: 'register',
                component: () => import('@/views/register.vue'),
                meta:{
                    title: '登录',
                    icon: 'login'
                }                
            }
        ]
    }
]

const frontendRoutes = [
    {
        path: '/',
        component: FrontendLayout,
        children: [
            {
                path: '',
                component: () => import('@/views/home.vue')
            },
            {
                path: 'consultations',
                component: () => import('@/views/consultations.vue')
            },
            {
                path: 'emotion-diary',
                component: () => import('@/views/emotionDiary.vue')
            },
            {
                path: 'knowledge',
                component: () => import('@/views/frontendKnowledge.vue')
            },
            {
                path: 'knowledge/article/:id',
                component: () => import('@/views/articleDetail.vue'),
                props: true
            },
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes: [
        ...backendRoutes, ...frontendRoutes
    ]
})

//路由配置守卫
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    //当前用户是否登录
    if(token){
        let userInfo
        try {
            userInfo = JSON.parse(localStorage.getItem('userInfo'))
        } catch {
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            next('/auth/login')
            return
        }

        if (!userInfo?.userType) {
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            next('/auth/login')
            return
        }

        //如果是后台用户
        if(Number(userInfo.userType) === 2){
            if(to.path.startsWith('/back')) { 
                next()
            }else{
                next('/back/dashboard')
            }
        }else if(Number(userInfo.userType) === 1){
            //如果是普通用户，不允许访问后台页面
            if(to.path.startsWith('/back')){
                next('/auth/login')
            }else{
                next()
            }
        }else{
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            next('/auth/login')
        }
    }else{
        if(to.path.startsWith('/back')) {
            //如果是访问后台页面，那么跳转到登录页
            next('/auth/login')
        }else{
            next()
        }
    }
})

export default router

