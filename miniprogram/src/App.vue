<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

onLaunch(() => {
  console.log('联智考勤小程序启动')
})

onShow(() => {
  console.log('App Show')
})

onHide(() => {
  console.log('App Hide')
})
</script>

<style lang="scss">
/* ===== 全局主题变量 ===== */
page {
  --brand-blue: #2b6cb0;
  --brand-blue-light: #4299e1;
  --brand-purple: #805ad5;
  --success: #48bb78;
  --warning: #ed8936;
  --danger: #f56565;
  --bg: #f5f7fa;
  --card: #ffffff;
  --text-primary: #1a202c;
  --text-secondary: #4a5568;
  --text-muted: #a0aec0;
  --border: #e2e8f0;
  --shadow-sm: 0 2rpx 8rpx rgba(0,0,0,0.04);
  --shadow-md: 0 4rpx 16rpx rgba(0,0,0,0.06);
  --shadow-lg: 0 8rpx 24rpx rgba(0,0,0,0.08);

  background-color: var(--bg);
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif;
  color: var(--text-primary);
  font-size: 28rpx;
  line-height: 1.6;
}

/* ===== 通用容器 ===== */
.page-wrap {
  padding: 24rpx;
  min-height: 100vh;
  background: var(--bg);
}

/* ===== 通用卡片 ===== */
.card {
  background: var(--card);
  border-radius: 20rpx;
  box-shadow: var(--shadow-sm);
  padding: 32rpx;
  margin-bottom: 24rpx;
}

/* ===== 通用按钮 ===== */
.btn-primary {
  background: linear-gradient(135deg, #4299e1, #2b6cb0);
  color: #fff;
  border-radius: 48rpx;
  padding: 20rpx 48rpx;
  font-size: 30rpx;
  font-weight: 500;
  box-shadow: 0 8rpx 24rpx rgba(66,153,225,0.35);
  text-align: center;
}
.btn-primary:active { opacity: 0.9; transform: scale(0.98); }

.btn-outline {
  background: transparent;
  color: var(--brand-blue);
  border: 2rpx solid var(--brand-blue);
  border-radius: 48rpx;
  padding: 18rpx 46rpx;
  font-size: 28rpx;
  text-align: center;
}

/* ===== 标签 ===== */
.tag {
  display: inline-block;
  padding: 6rpx 20rpx;
  border-radius: 24rpx;
  font-size: 22rpx;
  font-weight: 500;
}
.tag-blue { background: #ebf8ff; color: #2b6cb0; }
.tag-green { background: #f0fff4; color: #38a169; }
.tag-red { background: #fff5f5; color: #e53e3e; }
.tag-orange { background: #fffaf0; color: #dd6b20; }

/* ===== 分割线 ===== */
.divider {
  height: 1rpx;
  background: linear-gradient(90deg, transparent, #e2e8f0, transparent);
  margin: 24rpx 0;
}

/* ===== 空状态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 0;
  color: var(--text-muted);
}
.empty-state .empty-icon { font-size: 120rpx; margin-bottom: 24rpx; opacity: 0.5; }
.empty-state .empty-text { font-size: 28rpx; }
</style>
