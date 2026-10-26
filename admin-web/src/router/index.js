import { createRouter, createWebHistory } from 'vue-router'

// 路由表：URL 和页面的对应关系
const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  // ---- H5 员工端页面（手机浏览器访问，不需要登录）----
  { path: '/h5', component: () => import('../views/h5/Portal.vue'), meta: { title: '联智智能考勤' } },
  { path: '/h5/checkin', component: () => import('../views/h5/Checkin.vue'), meta: { title: '考勤打卡' } },
  { path: '/h5/collect', component: () => import('../views/h5/Collect.vue'), meta: { title: '员工信息采集' } },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/dashboard/index.vue'), meta: { title: '数据中心' } },
      { path: 'venue', component: () => import('../views/venue/index.vue'), meta: { title: '办公场所' } },
      { path: 'employee', component: () => import('../views/employee/index.vue'), meta: { title: '员工管理' } },
      { path: 'checkin', component: () => import('../views/checkin/index.vue'), meta: { title: '打卡记录' } },
      { path: 'rule', component: () => import('../views/rule/index.vue'), meta: { title: '考勤规则' } },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：没登录（无token）就跳回登录页；H5 页面员工用，不需要登录
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !to.path.startsWith('/h5') && !token) {
    return '/login'
  }
})

export default router
