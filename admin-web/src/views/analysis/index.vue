<template>
  <div>
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="办公场所人流分析" name="flow">
          <VenueFlowTab v-if="activeTab === 'flow'" />
        </el-tab-pane>
        <el-tab-pane label="员工考勤工时分析" name="worktime">
          <WorktimeTab v-if="activeTab === 'worktime'" />
        </el-tab-pane>
        <el-tab-pane label="考勤缺勤趋势预测" name="predict">
          <PredictTab v-if="activeTab === 'predict'" />
        </el-tab-pane>
        <el-tab-pane label="场所人员热力画像" name="heatmap">
          <HeatmapTab v-if="activeTab === 'heatmap'" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import VenueFlowTab from './VenueFlowTab.vue'
import WorktimeTab from './WorktimeTab.vue'
import PredictTab from './PredictTab.vue'
import HeatmapTab from './HeatmapTab.vue'

const activeTab = ref('flow')
</script>
