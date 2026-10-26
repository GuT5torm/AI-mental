<template>
    <div class="users-page">
        <!-- 搜索栏 -->
        <div class="filter-card">
            <div class="filter-form">
                <el-input v-model="query.keyword" placeholder="搜索用户名 / 昵称 / 邮箱" clearable style="width: 240px" @keyup.enter="handleSearch" @clear="handleSearch">
                    <template #prefix>
                        <el-icon><Search /></el-icon>
                    </template>
                </el-input>
                <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="handleSearch">
                    <el-option label="正常" :value="1" />
                    <el-option label="禁用" :value="0" />
                </el-select>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button type="success" class="create-btn" @click="openCreate">
                    <el-icon style="margin-right: 4px"><Plus /></el-icon>新增用户
                </el-button>
            </div>
        </div>

        <!-- 用户表格 -->
        <div class="table-card">
            <el-table :data="records" v-loading="loading" stripe>
                <el-table-column label="用户" min-width="200">
                    <template #default="{ row }">
                        <div class="user-cell">
                            <el-image class="user-avatar" :src="row.avatar || defaultAvatar">
                                <template #error>
                                    <span class="avatar-fallback">{{ (row.nickname || row.username || '?').slice(0, 1).toUpperCase() }}</span>
                                </template>
                            </el-image>
                            <div class="user-info">
                                <div class="user-name">{{ row.username }}</div>
                                <div class="user-nickname">{{ row.nickname || '未设置昵称' }}</div>
                            </div>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="角色" width="100" align="center">
                    <template #default="{ row }">
                        <el-tag :type="row.userType === 2 ? 'warning' : 'info'" effect="light" size="small">
                            {{ row.userType === 2 ? '管理员' : '普通用户' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                        <el-switch :model-value="row.status === 1" :disabled="row.id === currentUserId"
                            style="--el-switch-on-color: #34c77b; --el-switch-off-color: #dcdfe6"
                            @change="(val) => handleStatusChange(row, val)" />
                    </template>
                </el-table-column>
                <el-table-column width="110" align="center">
                    <template #header>
                        <span class="ai-col-header">
                            真实模型
                            <el-tooltip placement="top" effect="dark"
                                content="开启后该用户可使用真实大模型（全局 Key）；关闭则强制演示模式。">
                                <el-icon class="ai-col-help"><QuestionFilled /></el-icon>
                            </el-tooltip>
                        </span>
                    </template>
                    <template #default="{ row }">
                        <el-switch :model-value="row.aiRealEnabled !== 0"
                            style="--el-switch-on-color: #7c5cff; --el-switch-off-color: #dcdfe6"
                            @change="(val) => handleAiRealChange(row, val)" />
                    </template>
                </el-table-column>
                <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.email || '-' }}</template>
                </el-table-column>
                <el-table-column label="手机号" width="130">
                    <template #default="{ row }">{{ row.phone || '-' }}</template>
                </el-table-column>
                <el-table-column label="注册时间" width="180">
                    <template #default="{ row }">{{ row.createdAt || '-' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right">
                    <template #default="{ row }">
                        <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                        <el-button text type="warning" size="small" @click="handleResetPassword(row)">重置密码</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div class="pagination-box">
                <el-pagination v-model:current-page="query.currentPage" :page-size="query.pageSize"
                    :total="total" layout="total, prev, pager, next, jumper" background
                    @current-change="loadList" />
            </div>
        </div>

        <!-- 新增用户弹窗 -->
        <el-dialog v-model="createVisible" title="新增用户" width="460px" destroy-on-close>
            <el-form :model="createForm" label-width="70px">
                <el-form-item label="用户名" required>
                    <el-input v-model="createForm.username" placeholder="登录账号，唯一" maxlength="20" />
                </el-form-item>
                <el-form-item label="密码">
                    <el-input v-model="createForm.password" type="password" show-password
                        placeholder="留空则使用初始密码 Abc@123456" maxlength="32" />
                </el-form-item>
                <el-form-item label="昵称">
                    <el-input v-model="createForm.nickname" placeholder="选填" maxlength="50" />
                </el-form-item>
                <el-form-item label="邮箱">
                    <el-input v-model="createForm.email" placeholder="选填" />
                </el-form-item>
                <el-form-item label="手机号">
                    <el-input v-model="createForm.phone" placeholder="选填" maxlength="11" />
                </el-form-item>
                <el-form-item label="性别">
                    <el-select v-model="createForm.gender" style="width: 100%">
                        <el-option label="保密" :value="0" />
                        <el-option label="男" :value="1" />
                        <el-option label="女" :value="2" />
                    </el-select>
                </el-form-item>
                <el-form-item label="角色">
                    <el-select v-model="createForm.userType" style="width: 100%">
                        <el-option label="普通用户" :value="1" />
                        <el-option label="管理员" :value="2" />
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="createVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreate">创建</el-button>
            </template>
        </el-dialog>

        <!-- 编辑弹窗 -->
        <el-dialog v-model="editVisible" title="编辑用户" width="460px" destroy-on-close>
            <el-form :model="editForm" label-width="70px">
                <el-form-item label="用户名">
                    <el-input :model-value="editForm.username" disabled />
                </el-form-item>
                <el-form-item label="昵称">
                    <el-input v-model="editForm.nickname" placeholder="未设置为空" maxlength="50" />
                </el-form-item>
                <el-form-item label="邮箱">
                    <el-input v-model="editForm.email" placeholder="选填" />
                </el-form-item>
                <el-form-item label="手机号">
                    <el-input v-model="editForm.phone" placeholder="选填" maxlength="11" />
                </el-form-item>
                <el-form-item label="性别">
                    <el-select v-model="editForm.gender" style="width: 100%">
                        <el-option label="保密" :value="0" />
                        <el-option label="男" :value="1" />
                        <el-option label="女" :value="2" />
                    </el-select>
                </el-form-item>
                <el-form-item label="角色">
                    <el-select v-model="editForm.userType" style="width: 100%" :disabled="editForm.id === currentUserId">
                        <el-option label="普通用户" :value="1" />
                        <el-option label="管理员" :value="2" />
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, QuestionFilled } from '@element-plus/icons-vue'
import { userPage, createUser, changeUserStatus, updateUser, resetUserPassword } from '@/api/index.js'

const defaultAvatar = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="20" fill="%23E8F0FE"/></svg>'

const loading = ref(false)
const saving = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ keyword: '', status: null, currentPage: 1, pageSize: 10 })
const currentUserId = JSON.parse(localStorage.getItem('userInfo') || '{}').id

const loadList = async () => {
    loading.value = true
    try {
        const res = await userPage({
            currentPage: query.currentPage,
            pageSize: query.pageSize,
            keyword: query.keyword || undefined,
            status: query.status ?? undefined
        })
        records.value = res.records || []
        total.value = Number(res.total || 0)
    } finally {
        loading.value = false
    }
}

const handleSearch = () => {
    query.currentPage = 1
    loadList()
}

const handleStatusChange = (row, val) => {
    const next = val ? 1 : 0
    const action = next === 1 ? '启用' : '禁用'
    ElMessageBox.confirm(`确定要${action}用户「${row.username}」吗？`, '提示', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    }).then(async () => {
        await changeUserStatus(row.id, next)
        ElMessage.success(`${action}成功`)
        loadList()
    }).catch(() => {})
}

const handleAiRealChange = (row, val) => {
    const next = val ? 1 : 0
    const action = next === 1 ? '开启' : '关闭'
    const tip = next === 1
        ? `开启后用户「${row.username}」将使用真实大模型回复，确认开启吗？`
        : `关闭后用户「${row.username}」将使用演示模式回复，确认关闭吗？`
    ElMessageBox.confirm(tip, '提示', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    }).then(async () => {
        await updateUser(row.id, { aiRealEnabled: next })
        ElMessage.success(`已${action}真实模型`)
        loadList()
    }).catch(() => {})
}

const editVisible = ref(false)
const editForm = reactive({ id: null, username: '', nickname: '', email: '', phone: '', gender: 0, userType: 1 })

const createVisible = ref(false)
const createForm = reactive({ username: '', password: '', nickname: '', email: '', phone: '', gender: 0, userType: 1 })

const openCreate = () => {
    createForm.username = ''
    createForm.password = ''
    createForm.nickname = ''
    createForm.email = ''
    createForm.phone = ''
    createForm.gender = 0
    createForm.userType = 1
    createVisible.value = true
}

const handleCreate = async () => {
    if (!createForm.username.trim()) {
        ElMessage.warning('请填写用户名')
        return
    }
    if (createForm.password && createForm.password.length < 6) {
        ElMessage.warning('密码长度至少 6 位')
        return
    }
    saving.value = true
    try {
        await createUser({
            username: createForm.username.trim(),
            password: createForm.password || undefined,
            nickname: createForm.nickname || undefined,
            email: createForm.email || undefined,
            phone: createForm.phone || undefined,
            gender: createForm.gender,
            userType: createForm.userType
        })
        ElMessage.success(createForm.password ? '创建成功' : '创建成功，初始密码为 Abc@123456')
        createVisible.value = false
        query.currentPage = 1
        loadList()
    } finally {
        saving.value = false
    }
}

const openEdit = (row) => {
    editForm.id = row.id
    editForm.username = row.username
    editForm.nickname = row.nickname || ''
    editForm.email = row.email || ''
    editForm.phone = row.phone || ''
    editForm.gender = row.gender ?? 0
    editForm.userType = row.userType ?? 1
    editVisible.value = true
}

const handleSave = async () => {
    saving.value = true
    try {
        await updateUser(editForm.id, {
            nickname: editForm.nickname,
            email: editForm.email,
            phone: editForm.phone,
            gender: editForm.gender,
            userType: editForm.userType
        })
        ElMessage.success('保存成功')
        editVisible.value = false
        loadList()
    } finally {
        saving.value = false
    }
}

const handleResetPassword = (row) => {
    ElMessageBox.confirm(
        `确定将用户「${row.username}」的密码重置为初始值 Abc@123456 吗？`,
        '重置密码', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    ).then(async () => {
        await resetUserPassword(row.id)
        ElMessage.success('密码已重置为 Abc@123456')
    }).catch(() => {})
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.users-page {
    .filter-card {
        background: white;
        border-radius: 12px;
        padding: 16px 20px;
        margin-bottom: 16px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

        .filter-form {
            display: flex;
            align-items: center;
            gap: 12px;

            .create-btn {
                margin-left: auto;
            }
        }
    }

    .table-card {
        background: white;
        border-radius: 12px;
        padding: 8px 20px 16px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

        .user-cell {
            display: flex;
            align-items: center;
            gap: 12px;

            .user-avatar {
                width: 40px;
                height: 40px;
                border-radius: 50%;
                overflow: hidden;
                flex-shrink: 0;

                .avatar-fallback {
                    width: 40px;
                    height: 40px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    background: #E8F0FE;
                    color: #4c3a80;
                    font-weight: 600;
                    border-radius: 50%;
                }
            }

            .user-info {
                .user-name {
                    font-weight: 600;
                    color: #111827;
                }

                .user-nickname {
                    font-size: 12px;
                    color: #9CA3AF;
                    margin-top: 2px;
                }
            }
        }

        .ai-col-header {
            display: inline-flex;
            align-items: center;
            gap: 2px;

            .ai-col-help {
                color: #a8abb2;
                cursor: help;
                font-size: 14px;

                &:hover {
                    color: #7c5cff;
                }
            }
        }

        .pagination-box {
            display: flex;
            justify-content: flex-end;
            padding-top: 16px;
        }
    }
}
</style>
