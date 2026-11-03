<template>
  <div>
    <!-- Tab 切换：列表 ↔ 地图 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="📋 办公区列表" name="list">
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
      </el-tab-pane>

      <el-tab-pane label="🗺️ 场地地图" name="map">
        <div class="map-toolbar">
          <span class="map-stats">
            共 <b>{{ mapVenueList.length }}</b> 个办公场所
            <el-tag size="small" style="margin-left: 8px" type="info">点击标记查看详情</el-tag>
          </span>
          <el-button size="small" @click="loadMapData" :loading="mapLoading">🔄 刷新</el-button>
        </div>
        <div ref="mapChartRef" class="map-chart"></div>
      </el-tab-pane>
    </el-tabs>

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
          <div style="display: flex; width: 100%; gap: 6px; position: relative;">
            <el-input
              v-model="form.address"
              placeholder="如：北京市朝阳区建国路88号"
              @keyup.enter="searchAddress"
            />
            <el-button type="primary" :loading="searching" @click="searchAddress">🔍 搜索定位</el-button>
            <!-- 搜索结果候选列表 -->
            <div
              v-if="showResults && geocodeResults.length > 0"
              class="result-list"
            >
              <div
                v-for="(r, idx) in geocodeResults"
                :key="idx"
                class="result-item"
                @click="selectResult(r)"
              >
                <div class="result-addr">{{ r.formattedAddress }}</div>
                <div class="result-coord">{{ r.lng.toFixed(4) }}, {{ r.lat.toFixed(4) }}</div>
              </div>
            </div>
            <div v-else-if="showResults && !searching && geocodeResults.length === 0 && addressSearched" class="result-empty">
              未找到匹配地址，可在地图上手动点击选点
            </div>
          </div>
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
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import request from '../../api/request'
import { wgs84ToGcj02 } from '../../utils/coordTransform'
import chinaGeoJSON from '../../assets/china.json'

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

// 地址搜索相关
const searching = ref(false)
const showResults = ref(false)
const geocodeResults = ref([])
const addressSearched = ref(false)

// Tab + 地图
const activeTab = ref('list')
const mapChartRef = ref(null)
let mapChart = null
const mapVenueList = ref([])
const mapLoading = ref(false)

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
  // 清空搜索状态
  showResults.value = false
  geocodeResults.value = []
  addressSearched.value = false
}

/** 地址 → 经纬度搜索 */
async function searchAddress() {
  const addr = form.value.address?.trim()
  if (!addr) {
    ElMessage.warning('请先输入详细地址')
    return
  }
  searching.value = true
  showResults.value = true
  addressSearched.value = true
  geocodeResults.value = []
  try {
    const res = await request.get('/geocode', { params: { address: addr } })
    geocodeResults.value = res.data || []
    if (geocodeResults.value.length === 0) {
      // Nominatim 降级可能慢一点，给个提示
    } else {
      // 如果只有一条，自动选中
      if (geocodeResults.value.length === 1) {
        selectResult(geocodeResults.value[0])
      }
    }
  } catch (e) {
    ElMessage.error('地址搜索失败')
  } finally {
    searching.value = false
  }
}

/** 选一个搜索结果 → 地图飞到该点 + 放 marker */
function selectResult(r) {
  let lng = r.lng
  let lat = r.lat
  // Nominatim 返回 WGS-84，高德瓦片用 GCJ-02，需要转换
  if (r.source === 'nominatim') {
    const [gLng, gLat] = wgs84ToGcj02(lng, lat)
    lng = gLng
    lat = gLat
  }
  // 更新表单坐标
  form.value.longitude = lng.toFixed(6)
  form.value.latitude = lat.toFixed(6)

  // 如果还没初始化地图，先初始化（不太可能，因为搜索时弹窗已经打开）
  if (!map) {
    initMap()
  }

  // 地图飞到该点
  map.flyTo([lat, lng], 17, { duration: 0.8 })

  // 放 / 移动 marker
  if (marker) {
    marker.setLatLng([lat, lng])
  } else {
    marker = L.marker([lat, lng], { draggable: true }).addTo(map)
    marker.on('dragend', onMarkerDrag)
  }

  // 隐藏结果列表
  showResults.value = false

  ElMessage.success(`已定位到：${r.formattedAddress}`)
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

// ========== 场地地图 ==========

async function onTabChange(tab) {
  if (tab === 'map') {
    await nextTick()
    await loadMapData()
  }
}

async function loadMapData() {
  mapLoading.value = true
  try {
    // 拉所有有坐标的场所
    const res = await request.get('/venue/list')
    mapVenueList.value = (res.data || []).filter(v => v.longitude && v.latitude)
    renderMap()
  } catch (e) {
    ElMessage.error('加载地图数据失败')
  } finally {
    mapLoading.value = false
  }
}

function renderMap() {
  if (!mapChartRef.value) return

  // 注册中国地图 GeoJSON（ECharts 5 不再内置，需要手动注册）
  if (!echarts.getMap('china')) {
    echarts.registerMap('china', chinaGeoJSON)
  }

  // 销毁旧实例
  if (mapChart) {
    mapChart.dispose()
    mapChart = null
  }

  mapChart = echarts.init(mapChartRef.value)

  // 组装散点数据：[lng, lat, venue]
  const scatterData = mapVenueList.value.map(v => ({
    name: v.name,
    value: [Number(v.longitude), Number(v.latitude), v],
    venue: v
  }))

  const option = {
    backgroundColor: '#fff',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#eee',
      borderWidth: 1,
      textStyle: { color: '#333', fontSize: 13 },
      formatter: (params) => {
        if (params.seriesType === 'scatter' || params.seriesType === 'effectScatter') {
          const v = params.data.venue
          return `
            <div style="font-weight:600;color:#303133;margin-bottom:6px;">📍 ${v.name}</div>
            ${v.companyName ? `<div style="color:#606266;font-size:12px;">🏢 ${v.companyName}</div>` : ''}
            ${v.address ? `<div style="color:#909399;font-size:12px;margin-top:4px;">${v.address}</div>` : ''}
            <div style="color:#409eff;font-size:11px;margin-top:4px;">坐标: ${v.longitude}, ${v.latitude}</div>
          `
        }
        return ''
      }
    },
    geo: {
      map: 'china',
      roam: true,
      zoom: 1.2,
      center: [104, 36],
      label: {
        show: false
      },
      itemStyle: {
        areaColor: '#eef3fa',
        borderColor: '#b0c4de',
        borderWidth: 0.8
      },
      emphasis: {
        label: {
          show: true,
          color: '#333'
        },
        itemStyle: {
          areaColor: '#dce8f7'
        }
      }
    },
    series: [
      // 涟漪散点（主要标记，红色闪烁）
      {
        name: '办公场所',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: scatterData,
        symbolSize: (val) => 14,
        rippleEffect: {
          brushType: 'stroke',
          scale: 3,
          period: 4
        },
        itemStyle: {
          color: '#f56c6c',
          shadowBlur: 10,
          shadowColor: 'rgba(245,108,108,0.6)'
        },
        label: {
          show: true,
          position: 'right',
          formatter: (p) => p.data.name,
          fontSize: 12,
          color: '#303133',
          fontWeight: 500,
          textBorderColor: '#fff',
          textBorderWidth: 3
        },
        zlevel: 2
      }
    ]
  }

  mapChart.setOption(option)

  // 点击标记 → 弹窗显示详情
  mapChart.on('click', (params) => {
    if (params.seriesType === 'effectScatter' && params.data?.venue) {
      const v = params.data.venue
      ElMessageBox.alert(
        `<div style="line-height:1.8;">
          <div><b>📍 ${v.name}</b></div>
          ${v.companyName ? `<div>🏢 所属公司：${v.companyName}</div>` : ''}
          ${v.address ? `<div>📮 地址：${v.address}</div>` : ''}
          <div>📍 坐标：${v.longitude}, ${v.latitude}</div>
          <div>📏 范围：${v.radius || 300} 米</div>
        </div>`,
        '场所详情',
        { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
      )
    }
  })

  // 响应式
  window.addEventListener('resize', () => mapChart && mapChart.resize())
}

// 组件销毁时清理地图
onBeforeUnmount(() => {
  if (map) {
    map.remove()
    map = null
  }
  if (mapChart) {
    mapChart.dispose()
    mapChart = null
  }
  window.removeEventListener('resize', () => mapChart && mapChart.resize())
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

/* 地址搜索结果列表 */
.result-list {
  position: absolute; top: 100%; left: 0; right: 80px; margin-top: 4px;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.12); max-height: 240px; overflow-y: auto;
  z-index: 999;
}
.result-item {
  padding: 8px 12px; cursor: pointer; border-bottom: 1px solid #f2f6fc;
  transition: background 0.15s;
}
.result-item:last-child { border-bottom: none; }
.result-item:hover { background: #ecf5ff; }
.result-addr { font-size: 13px; color: #303133; }
.result-coord { font-size: 11px; color: #909399; margin-top: 2px; }
.result-empty {
  position: absolute; top: 100%; left: 0; right: 80px; margin-top: 4px;
  padding: 10px 12px; background: #fef0f0; border: 1px solid #fde2e2;
  border-radius: 6px; font-size: 12px; color: #f56c6c; z-index: 999;
}

/* 场地地图 */
.map-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 0;
}
.map-stats {
  font-size: 14px; color: #606266;
}
.map-stats b {
  color: #409eff; font-size: 18px; margin: 0 2px;
}
.map-chart {
  width: 100%; height: 620px;
  border: 1px solid #ebeef5; border-radius: 6px;
  background: #fff;
}
</style>
