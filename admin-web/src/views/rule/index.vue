<template>
  <!-- 考勤规则管理：给每个场所设置 上班时间/下班时间/迟到宽限分钟 -->
  <div>
    <el-card>
      <div class="tip">
        规则说明：员工在场所范围内的「当天第一次打卡」算上班卡，晚于 上班时间+宽限分钟 记为迟到；
        之后的打卡算下班卡，早于下班时间记为早退。没有配置规则的场所使用默认规则（09:00 / 18:00 / 宽限10分钟）。
      </div>
      <el-table :data="rows" border style="margin-top:16px">
        <el-table-column prop="venueName" label="场所名称" width="200" />
        <el-table-column label="上班时间" width="180">
          <template #default="{ row }">
            <el-time-picker v-model="row.workStart" format="HH:mm" value-format="HH:mm:ss" placeholder="上班时间" style="width:140px" />
          </template>
        </el-table-column>
        <el-table-column label="下班时间" width="180">
          <template #default="{ row }">
            <el-time-picker v-model="row.workEnd" format="HH:mm" value-format="HH:mm:ss" placeholder="下班时间" style="width:140px" />
          </template>
        </el-table-column>
        <el-table-column label="迟到宽限（分钟）" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.lateMinutes" :min="0" :max="120" />
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../api/request'

// 表格行：每个场所一行（规则 + 场所名合并后）
const rows = ref([])

// 打开页面时：先查所有场所，再查已有规则，合并成「每个场所一行」
async function load() {
  const [venueRes, ruleRes] = await Promise.all([
    request.get('/venue/list'),     // 所有场所
    request.get('/rule/list')       // 已配置的规则
  ])
  const venues = venueRes.data || []
  const rules = ruleRes.data || []
  // 用 venueId 做 key，方便按场所找规则
  const ruleMap = {}
  for (const r of rules) { ruleMap[r.venueId] = r }
  rows.value = venues.map(v => {
    const r = ruleMap[v.id]
    return {
      venueId: v.id,
      venueName: v.name,
      // 已有规则用已有值；没有就用默认值（只用于展示，保存时才真正写入）
      workStart: r ? r.workStart : '09:00:00',
      workEnd: r ? r.workEnd : '18:00:00',
      lateMinutes: r ? r.lateMinutes : 10
    }
  })
}

// 保存一行：后端会自动判断「有则更新，无则新增」
async function save(row) {
  await request.post('/rule/save', {
    venueId: row.venueId,
    workStart: row.workStart,
    workEnd: row.workEnd,
    lateMinutes: row.lateMinutes
  })
  ElMessage.success(`「${row.venueName}」规则已保存`)
}

onMounted(load)
</script>

<style scoped>
.tip { font-size: 13px; color: #718096; line-height: 1.8; background: #f7fafc; padding: 10px 14px; border-radius: 6px; }
</style>
