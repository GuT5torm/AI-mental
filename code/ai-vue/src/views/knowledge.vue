<template>
    <div>   
        <PageHead title="知识文章">
            <template #buttons>
                <el-button @click=" handleEdit({}) " type="primary">新增</el-button>
            </template>
        </PageHead>
        <Tablesearch :formItem="formItem" @search="handleSearch" />
        <el-table :data="tableData" style=" width: 100%; margin-top: 25px">
            <el-table-column prop="title" width="450" label="文章标题" fixed="left">
                <template #default="scope">
                    <div style="display: flex; align-items: center">
                        <el-icon><timer /></el-icon>
                        <span>{{ scope.row.title }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="分类" width="200">
                <template #default="scope">
                    <div style="display: flex; align-items: center">
                        <el-icon><timer /></el-icon>
                        <span>{{ categoryMap[ scope.row.categoryId ] }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="authorName" label="作者" width="150"/>
            <el-table-column prop="readCount" label="阅读量" width="150"/>
            <el-table-column prop="updateAt" label="发布时间" width="150"/>
            <el-table-column label="操作" width="240" fixed="right">
                <template #default="scope">
                    <el-button @click="handleEdit(scope.row)" text type="primary">编辑</el-button>
                    <el-button @click="handlePublish(scope.row)" v-if = "scope.row.status === 0 || scope.row.status === 2" type="success">发布</el-button>
                    <el-button  @click="handleUnpublish(scope.row)" v-if = "scope.row.status === 1 " type="warning">下线</el-button>
                    <el-button @click="handleDelete(scope.row)" text type="danger">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
            <el-pagination 
            style="margin-top:25px"
            :page-size="pagination.size"
            layout="prev, pager, next" 
            :total="pagination.total" 
            @change="handleChange"
            />
            <ArticleDialog v-model:modelValue="dialogVisible" :article="currentArticle" :categories="categories" @success="handleSuccess" />
    </div>
</template>
<script setup>
import { onMounted, ref, reactive } from 'vue'
import PageHead from '@/components/PageHead.vue'
import Tablesearch from '@/components/Tablesearch.vue'
import { categoryTree, articlePage, getArticleDetail, changeArticleStatus, deleteArticle } from '@/api/index'
import ArticleDialog from '@/components/ArticleDialog.vue'
import { ElMessageBox, ElMessage } from 'element-plus'

const formItem = [
    { comp: 'input', prop: 'title', label: '文章标题', placeholder: '请输入文章标题' },
    { comp: 'select', prop: 'categoryId', label: '分类', placeholder:'请选择分类' },
    { comp: 'select', prop: 'status', label: '状态', placeholder:'请选择文章状态', options: [{
        label: '草稿',
        value: '0'
    },{
        label: '已发布',
        value: '1'
    },{
        label: '已下线',
        value: '2'
    }] }
]

//分页参数
const pagination = reactive({
    currentPage: 1,
    pageSize: 10,
    total: 0
})

const handleSearch = async (formData) => {
    const params = {
        ...pagination,
        ...formData
    }

    try {
        const { records, total } = await articlePage(params)
        tableData.value = records
        pagination.total = total
    } catch (err) {
        tableData.value = []
        pagination.total = 0
        ElMessage.error(err?.response?.data?.msg || '查询失败')
    }
}

const handleChange = (page) =>{
    pagination.currentPage = page
    handleSearch()
}

//分类映射
const categoryMap = reactive({})
//分类列表
const categories = ref([])

//列表数据
const tableData = ref([])

//新增和编辑
const dialogVisible = ref(false)
const currentArticle = ref(null)
const handleSuccess = () => {
    dialogVisible.value = false
    //刷新列表
    handleSearch()
}
const handleEdit = (row) => {
    if (!row.id) {
        //新增
        currentArticle.value = null
        dialogVisible.value = true
    }else{
        //编辑
        getArticleDetail(row.id).then(res =>{
            currentArticle.value = res
            dialogVisible.value = true
        }).catch(err => {
            ElMessage.error(err?.response?.data?.msg || '文章详情加载失败')
        })
    }
}

//发布
const handlePublish = (row) => {
    ElMessageBox.confirm(
        `确认发布文章${row.title}吗?`,
        '确认',
        {
            confirmButtonText: '确认发布',
            cancelButtonText: '取消',
            type: 'info'
        }
    ).then(() => {
        changeArticleStatus(row.id, { status: 1 }).then(res => {
            ElMessage.success('发布成功')
            handleSearch()
        }).catch(err => {
            ElMessage.error(err?.response?.data?.msg || '发布失败')
        })
    })

}

const handleUnpublish = (row) => {
    ElMessageBox.confirm(
        `确认下线文章${row.title}吗?`,
        '确认',
        {
            confirmButtonText: '确认下线',
            confirmButtonText: '取消',
            type: 'warning'
        }
    ).then(() => {
        changeArticleStatus(row.id, { status: 2 }).then(res => {
            ElMessage.success('下线成功')
            handleSearch()
        }).catch(err => {
            ElMessage.error(err?.response?.data?.msg || '下线失败')
        })
    })
}

const handleDelete = (row) => {
    ElMessageBox.confirm(
        `确认删除文章${row.title}吗?`,
        '确认',
        {
            confirmButtonText: '确认删除',
            confirmButtonText: '取消',
            type: 'danger'
        }
    ).then(() => {
        deleteArticle(row.id).then (res => {
            ElMessage.success('删除成功')
            handleSearch()
        }).catch(err => {
            ElMessage.error(err?.response?.data?.msg || '删除失败')
        })
    })    
}

onMounted(async () => {
    try {
        const data = await categoryTree()
        categories.value = data.map(item =>{
            categoryMap[item.id] = item.categoryName
            return{
            label: item.categoryName,
            value: item.id
            }
        })
        formItem[1].options = categories.value
    } catch (err) {
        // 分类拉不到时筛选项为空，列表照常加载
        ElMessage.error(err?.response?.data?.msg || '分类加载失败')
    }

    handleSearch()
})
</script>