<template>
  <div>
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="按姓名搜索" style="width: 200px" clearable @change="onQuery" />
      <el-select v-model="query.dept" placeholder="按部门筛选" clearable style="width: 180px" @change="onQuery">
        <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
      </el-select>
      <el-input v-model="query.position" placeholder="按职位搜索" style="width: 180px" clearable @change="onQuery" />
      <el-select v-model="query.status" placeholder="按状态筛选" clearable style="width: 140px" @change="onQuery">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="query.venueId" placeholder="按场所筛选" clearable style="width: 180px" @change="onQuery">
        <el-option v-for="v in venueList" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
      <el-button type="primary" @click="onQuery">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="empNo" label="工号" width="100" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="dept" label="部门" />
      <el-table-column prop="position" label="职位" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="人脸照片" width="100">
        <template #default="{ row }">
          <el-image v-if="row.facePhoto" :src="row.facePhoto" style="width: 50px; height: 50px" fit="cover" :preview-src-list="[row.facePhoto]" preview-teleported />
          <span v-else style="color: #a0aec0">未采集</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.current"
      @current-change="(p) => { query.current = p; loadData() }"
    />

    <!-- 编辑 / 新增 Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑员工' : '新增员工'" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="formRules">
        <el-form-item label="工号" prop="empNo">
          <el-input v-model="form.empNo" :disabled="!!form.id" placeholder="编辑时不可修改" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="form.idCard" />
        </el-form-item>
        <el-form-item label="部门" prop="dept">
          <el-select v-model="form.dept" style="width: 100%" placeholder="请选择">
            <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位" prop="position">
          <el-input v-model="form.position" />
        </el-form-item>
        <el-form-item label="所属场所">
          <el-select v-model="form.venueId" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="v in venueList" :key="v.id" :label="v.name" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'

const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const venueList = ref([])
const deptOptions = ['人力资源部', '研发中心', '产品设计部', '市场运营部', '财务部', '客户成功部']
const query = reactive({ current: 1, size: 10, name: '', venueId: null, dept: '', position: '', status: null })

const statusOptions = [
  { value: 1, label: '在职' },
  { value: 2, label: '病假' },
  { value: 3, label: '事假' },
  { value: 0, label: '离职' },
]
function statusLabel(s) { return (statusOptions.find(o => o.value === s) || {}).label || '未知' }
function statusTagType(s) {
  return { 1: 'success', 2: 'warning', 3: 'info', 0: 'danger' }[s] || ''
}

// Dialog
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, empNo: '', name: '', gender: 1, phone: '', idCard: '', dept: '', position: '', venueId: null, status: 1 })
const formRules = {
  empNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  dept: [{ required: true, message: '请输入部门', trigger: 'blur' }],
  position: [{ required: true, message: '请输入职位', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

function resetForm() {
  Object.assign(form, { id: null, empNo: '', name: '', gender: 1, phone: '', idCard: '', dept: '', position: '', venueId: null, status: 1 })
}

function onQuery() { query.current = 1; loadData() }
function onReset() {
  query.name = ''; query.dept = ''; query.position = ''; query.status = null; query.venueId = null
  query.current = 1; loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/employee/page', { params: query })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id, empNo: row.empNo, name: row.name, gender: row.gender,
    phone: row.phone, idCard: row.idCard, dept: row.dept, position: row.position,
    venueId: row.venueId, status: row.status,
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) {
      await request.put('/employee', form)
      ElMessage.success('已更新')
    } else {
      await request.post('/employee', form)
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除员工「${row.name}」吗？`, '提示', { type: 'warning' })
  await request.delete(`/employee/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

onMounted(async () => {
  loadData()
  const res = await request.get('/venue/list')
  venueList.value = res.data
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
