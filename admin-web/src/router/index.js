import { createRouter, createWebHistory } from 'vue-router'

// 路由表：URL 和页面的对应关系
const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  // ---- H5 员工端登录（必须在其他 /h5/** 之前）----
  { path: '/h5/login', component: () => import('../views/h5/Login.vue'), meta: { noAuth: true } },
  // ---- H5 员工端业务页面（需要 h5Token）----
  { path: '/h5', component: () => import('../views/h5/Portal.vue'), meta: { title: '联智智能考勤' } },
  { path: '/h5/checkin', component: () => import('../views/h5/Checkin.vue'), meta: { title: '考勤打卡' } },
  { path: '/h5/collect', component: () => import('../views/h5/Collect.vue'), meta: { title: '员工信息采集' } },
  { path: '/h5/home', component: () => import('../views/h5/Attendance.vue'), meta: { title: '个人考勤' } },
  { path: '/h5/calendar', component: () => import('../views/h5/Calendar.vue'), meta: { title: '考勤日历' } },
  { path: '/h5/apply', component: () => import('../views/h5/Apply.vue'), meta: { title: '发起申请' } },
  { path: '/h5/myapprovals', component: () => import('../views/h5/MyApprovals.vue'), meta: { title: '我的申请' } },
  { path: '/h5/todoapprovals', component: () => import('../views/h5/TodoApprovals.vue'), meta: { title: '待我审批' } },
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
      { path: 'approval', component: () => import('../views/approval/index.vue'), meta: { title: '审批中心' } },
      { path: 'profile', component: () => import('../views/Profile.vue'), meta: { title: '个人主页' } },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to) => {
  const isH5 = to.path.startsWith('/h5')
  if (isH5) {
    // H5 员工端：除 /h5/login 外都需要 h5Token
    if (!to.meta.noAuth && !localStorage.getItem('h5Token')) {
      return { path: '/h5/login', query: { redirect: to.fullPath } }
    }
    return
  }
  // 管理端：除 /login 外都需要 token
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
})

export default router
