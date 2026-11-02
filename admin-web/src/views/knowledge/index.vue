<template>
  <div class="knowledge-layout">
    <el-tabs v-model="activeTab" type="border-card">

      <!-- ==================== Tab 1: 上传文档 ==================== -->
      <el-tab-pane label="📤 上传文档" name="upload">
        <div class="tab-content">
          <el-upload
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="10"
            :on-exceed="() => ElMessage.warning('一次最多 10 个文件')"
            multiple
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF / Word / Excel / PPT / TXT / CSV · 单文件 ≤ 10MB · 可批量
              </div>
            </template>
          </el-upload>

          <el-divider />

          <el-form :model="uploadForm" label-width="100px" class="upload-form">
            <el-form-item label="文档名称">
              <el-input v-model="uploadForm.name" placeholder="留空则用文件名" />
            </el-form-item>
            <el-form-item label="归属分类">
              <el-select v-model="uploadForm.categoryId" placeholder="请选择分类" clearable style="width:240px">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
              <el-button link type="primary" @click="activeTab='categories'">+ 管理分类</el-button>
            </el-form-item>
            <el-form-item label="版本备注">
              <el-input v-model="uploadForm.versionNote" placeholder="如：2024年Q1考勤制度修订" />
            </el-form-item>
            <el-form-item label="生效时间">
              <el-date-picker v-model="uploadForm.effectiveAt" type="datetime" placeholder="可选" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
            <el-form-item label="标签">
              <el-input v-model="uploadForm.tags" placeholder="逗号分隔，如：考勤,制度,HR" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="uploading" @click="submitUpload">开始上传并入库</el-button>
              <el-button @click="resetUploadForm">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- ==================== Tab 2: 文档列表 ==================== -->
      <el-tab-pane label="📄 文档列表" name="documents">
        <div class="tab-content">
          <div class="filter-bar">
            <el-select v-model="docFilter.categoryId" placeholder="全部分类" clearable style="width:160px;margin-right:10px">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-select v-model="docFilter.status" placeholder="全部状态" clearable style="width:140px;margin-right:10px">
              <el-option label="待处理 pending" value="pending" />
              <el-option label="解析中 parsing" value="parsing" />
              <el-option label="切片中 chunking" value="chunking" />
              <el-option label="向量化 embedding" value="embedding" />
              <el-option label="已就绪 ready" value="ready" />
              <el-option label="失败 failed" value="failed" />
            </el-select>
            <el-input v-model="docFilter.keyword" placeholder="搜索文档名" style="width:220px;margin-right:10px" clearable />
            <el-button type="primary" @click="loadDocs">🔍 查询</el-button>
            <el-button @click="resetDocFilter">重置</el-button>
            <span style="flex:1" />
            <el-button @click="loadDocs">🔄 刷新</el-button>
          </div>

          <el-table :data="docs" stripe border v-loading="docLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="文档名称" min-width="200">
              <template #default="{ row }">
                <div><b>{{ row.name }}</b></div>
                <div class="sub">{{ row.fileName }} · {{ row.format }} · {{ formatSize(row.fileSize) }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="categoryId" label="分类" width="120">
              <template #default="{ row }">{{ catName(row.categoryId) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="tags" label="标签" min-width="120">
              <template #default="{ row }">
                <el-tag v-for="t in (row.tags || '').split(',').filter(Boolean)" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="uploadedAt" label="上传时间" width="170" />
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="viewChunks(row)">🧩 切片</el-button>
                <el-button v-if="row.status==='failed'" size="small" type="warning" @click="reprocess(row)">🔄 重试</el-button>
                <el-button size="small" type="danger" @click="deleteDoc(row)">🗑 删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="docTotal > 10"
            class="pagination"
            background
            layout="prev, pager, next, total"
            :total="docTotal"
            :page-size="docFilter.size"
            v-model:current-page="docFilter.current"
            @current-change="loadDocs"
          />
        </div>
      </el-tab-pane>

      <!-- ==================== Tab 3: 切片管理 ==================== -->
      <el-tab-pane label="🧩 切片预览" name="chunks">
        <div class="tab-content">
          <div class="filter-bar">
            <el-select v-model="chunkDocId" placeholder="选择文档" style="width:300px">
              <el-option v-for="d in readyDocs" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
            <el-button type="primary" @click="loadChunks">加载切片</el-button>
          </div>
          <el-table v-if="chunks.length > 0" :data="chunks" stripe border>
            <el-table-column prop="indexNo" label="#" width="60" />
            <el-table-column prop="title" label="标题" width="180">
              <template #default="{ row }">{{ row.title || '(无标题)' }}</template>
            </el-table-column>
            <el-table-column prop="content" label="内容" min-width="400">
              <template #default="{ row }">
                <div class="chunk-text">{{ row.content }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="alg" label="向量化" width="90" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="editChunk(row)">✏ 编辑</el-button>
                <el-button size="small" type="danger" @click="delChunk(row)">🗑 删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="请先选择一个已就绪的文档" />
        </div>
      </el-tab-pane>

      <!-- ==================== Tab 4: 入库任务 ==================== -->
      <el-tab-pane label="⏳ 入库任务" name="tasks">
        <div class="tab-content">
          <el-button @click="loadTasks" style="margin-bottom:12px">🔄 刷新任务列表</el-button>
          <el-table :data="tasks" stripe border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="文档" min-width="180">
              <template #default="{ row }">{{ docName(row.docId) }} ({{ row.docId }})</template>
            </el-table-column>
            <el-table-column label="阶段" width="130">
              <template #default="{ row }">
                <el-tag :type="stageTag(row.stage)">{{ row.stage }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="180">
              <template #default="{ row }">
                <el-progress :percentage="row.progress || 0" :status="row.stage==='failed' ? 'exception' : row.stage==='done' ? 'success' : ''" />
              </template>
            </el-table-column>
            <el-table-column prop="message" label="消息" min-width="200" />
            <el-table-column prop="retryCount" label="重试" width="70" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button v-if="row.stage==='failed'" size="small" type="warning" @click="retryTask(row.id)">🔄 重试</el-button>
                <el-button v-if="row.stage!=='done' && row.stage!=='failed'" size="small" type="danger" @click="cancelTask(row.id)">⏹ 取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ==================== Tab 5: 分类管理 ==================== -->
      <el-tab-pane label="🏷 分类管理" name="categories">
        <div class="tab-content">
          <div class="filter-bar">
            <el-button type="primary" @click="openCatDialog()">+ 新建分类</el-button>
          </div>
          <el-table :data="categories" stripe border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="分类名称" min-width="150" />
            <el-table-column prop="description" label="描述" min-width="250" />
            <el-table-column prop="sortOrder" label="排序" width="90" />
            <el-table-column label="启用" width="90">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '✅' : '❌' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="openCatDialog(row)">✏ 编辑</el-button>
                <el-button size="small" type="danger" @click="delCat(row)">🗑 删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

    </el-tabs>

    <!-- ========== 切片编辑 Dialog ========== -->
    <el-dialog v-model="chunkEditVisible" title="编辑切片" width="640px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="chunkEdit.title" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="chunkEdit.content" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chunkEditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveChunkEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 分类 Dialog ========== -->
    <el-dialog v-model="catDialogVisible" :title="catForm.id ? '编辑分类' : '新建分类'" width="480px">
      <el-form :model="catForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="catForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="catForm.description" type="textarea" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="catForm.active" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCat">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  listCategories, saveCategory, deleteCategory,
  uploadDocument, pageDocuments, deleteDocument, reprocessDocument,
  listChunks, updateChunk, deleteChunk,
  listTasks, retryTask as apiRetryTask, cancelTask as apiCancelTask
} from '../../api/knowledge'

const activeTab = ref('upload')

// ===== 公共数据 =====
const categories = ref([])
const docs = ref([])
const docTotal = ref(0)
const docLoading = ref(false)
const chunks = ref([])
const tasks = ref([])
const readyDocs = computed(() => docs.value.filter(d => d.status === 'ready' && d.active))
const docNameMap = computed(() => {
  const m = {}
  docs.value.forEach(d => m[d.id] = d.name)
  return m
})
function catName(id) { return categories.value.find(c => c.id === id)?.name || '-' }
function docName(id) { return docNameMap.value[id] || `文档#${id}` }

// ===== 上传 =====
const uploadForm = reactive({ name: '', categoryId: null, versionNote: '', effectiveAt: null, tags: '' })
const selectedFiles = ref([])
const uploading = ref(false)

function handleFileChange(file, files) {
  selectedFiles.value = files
}

async function submitUpload() {
  if (!selectedFiles.value.length) return ElMessage.warning('请先选择文件')
  uploading.value = true
  let ok = 0, fail = 0
  for (const f of selectedFiles.value) {
    try {
      const fd = new FormData()
      fd.append('file', f.raw)
      if (uploadForm.name) fd.append('name', uploadForm.name + '-' + f.name)
      if (uploadForm.categoryId) fd.append('categoryId', uploadForm.categoryId)
      if (uploadForm.versionNote) fd.append('versionNote', uploadForm.versionNote)
      if (uploadForm.effectiveAt) fd.append('effectiveAt', uploadForm.effectiveAt)
      if (uploadForm.tags) fd.append('tags', uploadForm.tags)
      await uploadDocument(fd)
      ok++
    } catch (e) { fail++ }
  }
  uploading.value = false
  ElMessage.success(`上传完成：成功 ${ok} 个，失败 ${fail} 个`)
  resetUploadForm()
  activeTab.value = 'documents'
  loadDocs()
}

function resetUploadForm() {
  uploadForm.name = ''; uploadForm.categoryId = null; uploadForm.versionNote = ''
  uploadForm.effectiveAt = null; uploadForm.tags = ''
  selectedFiles.value = []
}

// ===== 文档列表 =====
const docFilter = reactive({ current: 1, size: 10, categoryId: null, status: '', keyword: '' })
async function loadDocs() {
  docLoading.value = true
  try {
    const res = await pageDocuments(docFilter)
    docs.value = res.data.records || []
    docTotal.value = res.data.total || 0
  } finally { docLoading.value = false }
}
function resetDocFilter() {
  docFilter.current = 1; docFilter.categoryId = null; docFilter.status = ''; docFilter.keyword = ''
  loadDocs()
}
async function deleteDoc(row) {
  await ElMessageBox.confirm(`确认删除文档「${row.name}」？切片和物理文件都会被清理。`, '提示', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('已删除')
  loadDocs()
}
async function reprocess(row) {
  await reprocessDocument(row.id)
  ElMessage.success('已重新入库，请在「入库任务」Tab 查看进度')
  activeTab.value = 'tasks'
  loadTasks()
}
function viewChunks(row) { chunkDocId.value = row.id; activeTab.value = 'chunks'; loadChunks() }

// ===== 切片 =====
const chunkDocId = ref(null)
async function loadChunks() {
  if (!chunkDocId.value) return
  const res = await listChunks(chunkDocId.value)
  chunks.value = res.data || []
}

const chunkEditVisible = ref(false)
const chunkEdit = reactive({ id: null, title: '', content: '' })
function editChunk(row) {
  chunkEdit.id = row.id; chunkEdit.title = row.title || ''; chunkEdit.content = row.content
  chunkEditVisible.value = true
}
async function saveChunkEdit() {
  await updateChunk(chunkEdit.id, { title: chunkEdit.title, content: chunkEdit.content })
  ElMessage.success('已保存')
  chunkEditVisible.value = false
  loadChunks()
}
async function delChunk(row) {
  await ElMessageBox.confirm('确认删除这个切片？', '提示', { type: 'warning' })
  await deleteChunk(row.id)
  ElMessage.success('已删除')
  loadChunks()
}

// ===== 任务 =====
async function loadTasks() {
  const res = await listTasks()
  tasks.value = res.data || []
}
async function retryTask(id) {
  await apiRetryTask(id)
  ElMessage.success('已重试')
  loadTasks()
}
async function cancelTask(id) {
  await apiCancelTask(id)
  ElMessage.success('已取消')
  loadTasks()
}

// ===== 分类 =====
const catDialogVisible = ref(false)
const catForm = reactive({ id: null, name: '', description: '', sortOrder: 0, active: true })

function openCatDialog(row) {
  if (row) Object.assign(catForm, row)
  else { catForm.id = null; catForm.name = ''; catForm.description = ''; catForm.sortOrder = 0; catForm.active = true }
  catDialogVisible.value = true
}
async function saveCat() {
  if (!catForm.name) return ElMessage.warning('分类名称不能为空')
  await saveCategory(catForm)
  ElMessage.success('已保存')
  catDialogVisible.value = false
  loadCategories()
}
async function delCat(row) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '提示', { type: 'warning' })
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  loadCategories()
}

async function loadCategories() {
  const res = await listCategories()
  categories.value = res.data || []
}

// ===== 工具 =====
function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
function statusText(s) {
  const map = { pending: '待处理', parsing: '解析中', chunking: '切片中', embedding: '向量化中', ready: '已就绪', failed: '失败', deleted: '已删除' }
  return map[s] || s
}
function statusTag(s) {
  if (s === 'ready') return 'success'
  if (s === 'failed') return 'danger'
  if (s === 'deleted') return 'info'
  if (s === 'pending') return 'warning'
  return ''
}
function stageTag(s) {
  if (s === 'done') return 'success'
  if (s === 'failed') return 'danger'
  if (s === 'canceled') return 'info'
  return 'warning'
}

onMounted(async () => {
  await loadCategories()
  await loadDocs()
  try { await loadTasks() } catch (e) { /* 首次可能无任务 */ }
})
</script>

<style scoped>
.knowledge-layout { padding: 0; }
.tab-content { padding: 16px 20px 20px; }
.filter-bar { display: flex; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 6px; }
.sub { font-size: 12px; color: #718096; margin-top: 2px; }
.chunk-text { white-space: pre-wrap; font-size: 13px; line-height: 1.6; max-height: 120px; overflow-y: auto; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.upload-form { max-width: 560px; }
</style>
