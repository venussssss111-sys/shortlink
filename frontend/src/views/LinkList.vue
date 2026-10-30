<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageLinks, updateStatus, deleteLink, shortUrlOf } from '../api/link'
import CreateLinkDialog from '../components/CreateLinkDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await pageLinks({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    // 拦截器已经弹过提示
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onSizeChange() {
  page.value = 1
  load()
}

async function toggle(row, next) {
  const status = next ? 1 : 0
  try {
    await updateStatus(row.code, status)
    row.status = status
    ElMessage.success(
      status === 1 ? `已启用 ${row.code}` : `已停用 ${row.code}（访问会返回 404）`
    )
  } catch (e) {
    // 用的是 :model-value，失败时数据没变，开关自己弹回去
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除后 ${row.code} 就访问不了了，确定吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '再想想' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteLink(row.code)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    load()
  } catch (e) {
    // 已提示
  }
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('浏览器不让复制，手动选中吧')
  }
}

function isExpired(row) {
  if (!row.expireTime) return false
  return new Date(row.expireTime.replace(/-/g, '/')) < new Date()
}

function onCreated() {
  dialogVisible.value = false
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜短码 / 原链接"
        clearable
        style="width: 260px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button @click="search">搜索</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="dialogVisible = true">新建短链</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="code" label="短码" width="130">
        <template #default="{ row }">
          <el-link type="primary" :href="shortUrlOf(row.code)" target="_blank">
            {{ row.code }}
          </el-link>
        </template>
      </el-table-column>

      <el-table-column label="短链" min-width="230">
        <template #default="{ row }">
          <span class="mono">{{ shortUrlOf(row.code) }}</span>
          <el-button link type="primary" size="small" @click="copy(shortUrlOf(row.code))">
            复制
          </el-button>
        </template>
      </el-table-column>

      <el-table-column prop="originUrl" label="原链接" min-width="240" show-overflow-tooltip />

      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="(v) => toggle(row, v)" />
        </template>
      </el-table-column>

      <el-table-column label="过期时间" width="170">
        <template #default="{ row }">
          <span :class="{ expired: isExpired(row) }">{{ row.expireTime || '永久' }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="createTime" label="创建时间" width="170" />

      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <div style="padding: 32px 0; color: #8a9099">
          这里还没有短链，点右上角「新建短链」试试
        </div>
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>

    <CreateLinkDialog v-model="dialogVisible" @created="onCreated" />
  </div>
</template>
