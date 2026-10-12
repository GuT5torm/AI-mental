// 上传文件的访问基址。后端把 /files/** 映射到本地磁盘目录，
// 这一路径不在 vite proxy 的 /api 前缀下，因此直连后端端口。
// 切换环境时与 vite.config.js 的 proxy target 一起改。
export const fileBaseUrl = "http://localhost:1236"