<template>
  <div>
    <!-- 搜索 + 新增 -->
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="按场所名称搜索" style="width: 220px" clearable @change="loadData" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openDialog()">新增场所</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="场所名称" />
      <el-table-column prop="companyName" label="所属公司" />
      <el-table-column prop="address" label="地址" show-overflow-tooltip />
      <el-table-column label="坐标" width="200">
        <template #default="{ row }">{{ row.longitude }}, {{ row.latitude }}</template>
      </el-table-column>
      <el-table-column prop="radius" label="范围(米)" width="90" />
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="showQrCode(row)">场所码</el-button>
          <el-button size="small" type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.current"
      @current-change="(p) => { query.current = p; loadData() }"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑场所' : '新增场所'" width="720px" @open="onDialogOpen">
      <el-form :model="form" label-width="90px">
        <el-form-item label="场所名称" required>
          <el-input v-model="form.name" placeholder="如：北京总部" />
        </el-form-item>
        <el-form-item label="所属公司">
          <el-input v-model="form.companyName" placeholder="选填" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="详细地址，选填" />
        </el-form-item>
        <el-form-item label="打卡范围">
          <el-input-number v-model="form.radius" :min="50" :max="5000" /> 米
          <span class="hint">（员工在此范围内才能打卡）</span>
        </el-form-item>

        <!-- 地图选点区 -->
        <el-form-item label="地图选点">
          <div class="map-box">
            <!-- Leaflet 地图容器 -->
            <div id="venueMap" class="map"></div>
            <div class="map-tip">点击地图选择位置，或拖动红色标记调整</div>
            <div class="coord-display">
              经度：<b>{{ form.longitude || '--' }}</b>　纬度：<b>{{ form.latitude || '--' }}</b>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 二维码弹窗 -->
    <el-dialog v-model="qrVisible" title="场所码（手机扫码进入）" width="360px">
      <div style="text-align: center">
        <img :src="qrUrl" style="width: 280px; height: 280px" />
        <p>{{ currentVenue?.name }}</p>
        <p v-if="qrLink" class="qr-link">{{ qrLink }}</p>
        <p v-if="qrLink" class="qr-tip">手机连同一个 WiFi，用相机扫上面二维码即可</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'

const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, name: '' })

const dialogVisible = ref(false)
const form = ref({})

const qrVisible = ref(false)
const qrUrl = ref('')
const qrLink = ref('')
const currentVenue = ref(null)

// 地图对象和标记（全局，弹窗关闭时销毁）
let map = null
let marker = null

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/venue/page', { params: query })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  // 编辑时带上已有数据；新增时给默认范围
  form.value = row ? { ...row } : { radius: 300 }
  dialogVisible.value = true
}

/** 弹窗完全打开后初始化地图（必须等 DOM 渲染完） */
async function onDialogOpen() {
  await nextTick()
  initMap()
}

/** 初始化地图 */
function initMap() {
  // 防止重复初始化报错
  if (map) {
    map.remove()
    map = null
    marker = null
  }

  // 确定地图中心点：编辑模式用已有坐标，新增模式默认北京天安门
  let centerLng = 116.397428
  let centerLat = 39.90923
  if (form.value.longitude && form.value.latitude) {
    centerLng = Number(form.value.longitude)
    centerLat = Number(form.value.latitude)
  }

  // 创建地图，设置中心点和缩放级别
  map = L.map('venueMap').setView([centerLat, centerLng], 16)

  // 高德地图瓦片（免费、国内加载快、不需要 key）
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    {
      subdomains: ['1', '2', '3', '4'],
      attribution: '高德地图'
    }
  ).addTo(map)

  // 如果已有坐标，放一个标记
  if (form.value.longitude && form.value.latitude) {
    marker = L.marker([centerLat, centerLng], { draggable: true }).addTo(map)
    marker.on('dragend', onMarkerDrag)
  }

  // 点击地图 → 放置/移动标记 → 自动填入经纬度
  map.on('click', (e) => {
    const { lat, lng } = e.latlng
    if (marker) {
      marker.setLatLng([lat, lng])
    } else {
      marker = L.marker([lat, lng], { draggable: true }).addTo(map)
      marker.on('dragend', onMarkerDrag)
    }
    // 保留 6 位小数，数据库字段是 DECIMAL(10,6)
    form.value.longitude = lng.toFixed(6)
    form.value.latitude = lat.toFixed(6)
  })
}

/** 拖动标记结束 → 更新坐标 */
function onMarkerDrag(e) {
  const { lat, lng } = e.target.getLatLng()
  form.value.longitude = lng.toFixed(6)
  form.value.latitude = lat.toFixed(6)
}

/** 弹窗关闭时销毁地图，释放资源 */
function onDialogClose() {
  if (map) {
    map.remove()
    map = null
    marker = null
  }
}

async function handleSave() {
  if (!form.value.name) {
    ElMessage.warning('场所名称不能为空')
    return
  }
  if (!form.value.longitude || !form.value.latitude) {
    ElMessage.warning('请在地图上点击选择场所位置')
    return
  }
  if (form.value.id) {
    await request.put('/venue', form.value)
  } else {
    await request.post('/venue', form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  onDialogClose()
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '提示', { type: 'warning' })
  await request.delete(`/venue/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

async function showQrCode(row) {
  currentVenue.value = row
  qrUrl.value = `/api/qrcode/venue/${row.id}`
  qrLink.value = ''
  qrVisible.value = true
  // 取二维码里的真实网址，弹窗里显示出来，方便确认手机能不能访问
  try {
    const res = await request.get(`/qrcode/venue/${row.id}/url`)
    if (res.data.code === 200) {
      qrLink.value = res.data.data
    }
  } catch (e) {
    // 拿不到就只显示二维码，不影响使用
  }
}

// 组件销毁时清理地图
onBeforeUnmount(() => {
  if (map) {
    map.remove()
    map = null
  }
})

onMounted(loadData)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
.hint { color: #909399; font-size: 12px; margin-left: 8px; }

/* 地图容器 */
.map-box { width: 100%; }
.map { width: 100%; height: 360px; border-radius: 6px; border: 1px solid #dcdfe6; }
.map-tip { font-size: 12px; color: #909399; margin-top: 6px; text-align: center; }
.coord-display {
  margin-top: 8px; padding: 8px 12px; background: #f5f7fa;
  border-radius: 4px; font-size: 13px; color: #606266; text-align: center;
}
.coord-display b { color: #409eff; }
.qr-link { font-size: 12px; color: #409eff; word-break: break-all; margin-top: 4px; }
.qr-tip { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
