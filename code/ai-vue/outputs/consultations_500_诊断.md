# consultations.vue 网络 500 错误诊断

## 错误现象
```
{code: "500", msg: "系统错误", message: "系统错误", success: false}
```
这是**后端返回的业务错误信封**（HTTP 多半为 200，后端自行封装了 `code` 字段），前端本身不直接抛出。

## 涉及的两个接口
`consultations.vue` 中只有两处会触发后端请求：

| 调用点 | 函数 | 请求 |
|---|---|---|
| `onMounted` / `handleChange` | `getConsultationPage(pagination)` | `GET /api/psychological-chat/sessions` |
| 点击「详情」 | `getSessionDetail(row.id)` | `GET /api/psychological-chat/sessions/{id}/messages` |

## 可能造成 500 的原因（按可能性排序）

### 1. `row.id` 可能为空（最大嫌疑）
`viewSessionDetail(row)` 用 `row.id` 拼接口路径。若列表接口返回的主键字段不是 `id`（如 `sessionId`），则 `row.id` 为 `undefined`，请求变成：
`/psychological-chat/sessions/  undefined/messages`
后端按主键查询空指针 → 500。**需确认列表接口实际返回字段名。**

### 2. 分页参数与后端不一致
`handleSearch` 把整个 `pagination = {currentPage, size, total}` 作为 query 传给 `/sessions`。
- 后端常见分页参数名是 `page`/`pageNum` + `pageSize`，而非 `currentPage`/`size`。
- 若后端做了类型转换（字符串→数字）且未兼容，可能 500。
- `total` 被一并拼进 URL query，属于多余参数，可能干扰后端解析。

### 3. 接口路径不一致
- 参考目录 `admin.js` 写法：`/sessions/${id}/messages`（复数）
- 工作区 `gutts.js` 写法：`/sessions/${id}/message`（单数）
- `consultations.vue` 实际 `import from '@/api/admin'`，需确认项目真正加载的文件。若加载到 `/message` 单数版本，路由不匹配也可能返回 500/404。

### 4. 前端缺陷（放大问题，非根因）
- `viewSessionDetail` 无 `.catch`：`getSessionDetail` 报错后 `loadingMessages` 永远为 `true`，弹窗卡在 loading 且不提示。
- 响应拦截器（`request.js`）在 `code !== '200' && code !== '-1'` 时 `return response`（返回整个 axios 响应），而非 `reject`。导致 `handleSearch` 拿到的 `res` 是完整 response 对象，`res.records`/`res.total` 为 `undefined`，列表空白且静默失败（参考目录版本是 `Promise.reject(data)`，更合理）。

## 下一步建议
1. 浏览器 Network 面板确认：500 来自 `/sessions` 还是 `/sessions/{id}/messages`，并查看请求 URL 中 `id` 是否为 `undefined`。
2. 核对后端这两个路由的参数名（分页：`currentPage/size` 还是 `page/pageSize`；主键字段名）。
3. 给两个接口加 `.catch` 兜底；把拦截器异常分支改为 `return Promise.reject(data)`。

> 若提供后端代码（FastAPI）对应路由，可进一步定位具体空指针/参数错误行。
