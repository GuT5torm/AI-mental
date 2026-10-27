// 上传文件的访问基址。
// - 本地开发：vite proxy 只代理 /api，/files 直连本地后端端口，
//   基址来自 .env.development 的 VITE_FILE_BASE_URL
// - 生产构建：默认留空 = 同源相对路径（如 /files/xxx），经 nginx 反代到后端；
//   分离域名部署时通过 Dockerfile 构建参数 VITE_FILE_BASE_URL 传入完整地址
export const fileBaseUrl = import.meta.env.VITE_FILE_BASE_URL || ""
