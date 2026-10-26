import service from "@/utils/request";

export function login(data){
    return service.post('/user/login', data)
}

// 管理员全局 AI 配置：保存后对话链路自动生效，无需重启
export function getAdminAiConfig() {
    return service.get('/ai/admin-config')
}

export function saveAdminAiConfig(data) {
    return service.put('/ai/admin-config', data)
}

export function deleteAdminAiConfig() {
    return service.delete('/ai/admin-config')
}

// 测试连接会发起一次真实的大模型调用，放宽超时避免慢网络误报失败
export function testAdminAiConfig(data) {
    return service.post('/ai/admin-config/test', data, { timeout: 30000 })
}

export function categoryTree(){
    return service.get('/knowledge/category/tree')
}


export function articlePage(params) {
    return service.get('/knowledge/article/page', { params })
}

export function uploadFile(file,businessInfo){
    const formData = new FormData()
    formData.append('file', file)
    formData.append('businessType', 'ARTICLE')
    formData.append('businessId', businessInfo.businessId)
    formData.append('businessField', 'cover')

    return service.post('/file/upload', formData, {
        headers :{
            'Content-Type' : 'multipart/form-data'
        }
    })

}

export function createArticle(data) {
    return service.post('/knowledge/article', data ) 
}

export function getArticleDetail(id) {
    return service.get(`/knowledge/article/${id}`)
}

export function updateArticle(id, data) {
    return service.put(`/knowledge/article/${id}`, data )
}

export function changeArticleStatus(id, data ) {
    return service.put(`/knowledge/article/${id}/status`, data )
}

export function changeArticleTop(id, top) {
    return service.put(`/knowledge/article/${id}/top`, null, { params: { top } })
}

// ==================== 后台用户管理 ====================

export function userPage(params) {
    return service.get('/user/admin/page', { params })
}

export function changeUserStatus(id, status) {
    return service.put(`/user/admin/${id}/status`, null, { params: { status } })
}

export function updateUser(id, data) {
    return service.put(`/user/admin/${id}`, data)
}

export function resetUserPassword(id) {
    return service.put(`/user/admin/${id}/password`)
}
export function createUser(data) {
    return service.post('/user/admin/create', data)
}
export function deleteArticle(id) {
    return service.delete(`/knowledge/article/${id}`)
}

export function getConsultationPage(params) {
    return service.get('/psychological-chat/sessions', {params} )
}

export function getSessionDetail(sessionId) {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export function getEmotionalPage(params) {
    return service.get(`/emotion-diary/admin/page`, { params })
}

export function deleteEmotional(id){
    return service.delete(`/emotion-diary/admin/${id}`)
}

export function getAnalyticsOverview(){
    return service.get(`/data-analytics/overview`)
}

export function logout(){
    return service.post(`/user/logout`)
}

