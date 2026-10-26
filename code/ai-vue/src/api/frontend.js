import service from '@/utils/request'

export const register = (data) => {
    return service.post('/user/add', data)
}
     
export const startSession = (data) => {
    return service.post('/psychological-chat/session/start', data)
}

export const getSessionList = (params) => {
    return service.get('/psychological-chat/sessions', { params })
}

export const deleteSession = (sessionId) => {
    return service.delete(`/psychological-chat/sessions/${sessionId}`)
}

export const getSessionDetail = (sessionId) => {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export const getSessionEmotion = (sessionId) => {
    return service.get(`/psychological-chat/session/${sessionId}/emotion`)
}

// 当前用户自己的全部情绪记录（AI 咨询对话自动记录），按日期倒序
export const getMyEmotionDiary = () => {
    return service.get('/emotion-diary/my')
}

export const getKnowledgeList = (params) => {
    return service.get('/knowledge/article/page', { params })
}
export const getKnowledgeDetail = (articleId) => {
    return service.get(`/knowledge/article/${articleId}`)
}

// AI 模式状态：用户自配 Key 与管理员全局 Key 任一存在即非演示模式
export const getAiStatus = () => {
    return service.get('/ai-config/status')
}