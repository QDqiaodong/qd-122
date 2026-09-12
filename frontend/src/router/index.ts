import { createRouter, createWebHistory } from 'vue-router'
import LoadDashboard from '@/pages/LoadDashboard.vue'
import SpringArchive from '@/pages/SpringArchive.vue'
import TransferOperation from '@/pages/TransferOperation.vue'
import TransferSimulation from '@/pages/TransferSimulation.vue'
import TransferApproval from '@/pages/TransferApproval.vue'
import TraceQuery from '@/pages/TraceQuery.vue'
import NightReview from '@/pages/NightReview.vue'
import ElasticSamples from '@/pages/ElasticSamples.vue'

const routes = [
  {
    path: '/',
    name: 'LoadDashboard',
    component: LoadDashboard,
    meta: { title: '产线负载预警看板' },
  },
  {
    path: '/springs',
    name: 'SpringArchive',
    component: SpringArchive,
    meta: { title: '弹簧档案管理' },
  },
  {
    path: '/transfer',
    name: 'TransferOperation',
    component: TransferOperation,
    meta: { title: '产线划转申请' },
  },
  {
    path: '/simulation',
    name: 'TransferSimulation',
    component: TransferSimulation,
    meta: { title: '工序调拨模拟' },
  },
  {
    path: '/approval',
    name: 'TransferApproval',
    component: TransferApproval,
    meta: { title: '划转申请审批' },
  },
  {
    path: '/night-review',
    name: 'NightReview',
    component: NightReview,
    meta: { title: '夜班承载复核单' },
  },
  {
    path: '/elastic-samples',
    name: 'ElasticSamples',
    component: ElasticSamples,
    meta: { title: '弹力抽检留样' },
  },
  {
    path: '/trace',
    name: 'TraceQuery',
    component: TraceQuery,
    meta: { title: '划转轨迹查询' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || '弹簧产线划转管理系统'}`
  next()
})

export default router
