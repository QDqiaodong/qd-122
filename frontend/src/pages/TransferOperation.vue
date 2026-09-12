<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { springApi, applicationApi, nightReviewApi } from '@/api'
import type { SpringArchive, TransferApplication, ApplicationStatus, SubmitApplicationRequest } from '@/types'
import {
  GitBranch,
  Search,
  Factory,
  Send,
  ArrowLeftRight,
  Users,
  FileText,
  Clock,
  Cog,
  Ban,
  OctagonPause,
  AlertTriangle,
} from 'lucide-vue-next'

const lineStore = useLineStore()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const springs = ref<SpringArchive[]>([])
const selectedIds = ref<number[]>([])
const recentApplications = ref<TransferApplication[]>([])

/** 接班门禁：未确认昨夜全部夜班承载复核单时禁止提交新划转（后端提交时也会强校验） */
const reviewBlocked = ref(false)
const reviewPendingCount = ref(0)

async function fetchReviewGuard() {
  try {
    const response = await nightReviewApi.guard()
    reviewBlocked.value = !response.data.allowed
    reviewPendingCount.value = response.data.pendingCount
  } catch {
    // 门禁查询失败不阻断页面，提交时后端仍会拦截
  }
}

function goNightReview() {
  router.push({ name: 'NightReview', query: { status: 'PENDING' } })
}

const searchForm = reactive({
  lineId: null as number | null,
  keyword: '',
})

const applyForm = reactive<Omit<SubmitApplicationRequest, 'springIds'>>({
  toLineId: null as unknown as number,
  applicant: '',
  reason: '',
})

const selectedSprings = computed(() => {
  return springs.value.filter((s) => selectedIds.value.includes(s.id))
})

/** 封存中的弹簧（抽检不合格/待复测）不能进入划转申请 */
const selectableSprings = computed(() =>
  springs.value.filter((s) => s.sealStatus !== 'SEALED' && !s.yellowFlag)
)

/** 临时停台的产线不能作为划转接收方 */
const haltedTargetLines = computed(() => {
  if (selectedSprings.value.length === 0) return lineStore.lines.filter((l) => l.haltStatus === 'HALTED')
  const fromLineIds = new Set(selectedSprings.value.map((s) => s.currentLineId))
  return lineStore.lines.filter(
    (l) => l.haltStatus === 'HALTED' && !fromLineIds.has(l.id)
  )
})

const availableTargetLines = computed(() => {
  if (selectedSprings.value.length === 0) {
    return lineStore.lines.filter((l) => l.haltStatus !== 'HALTED')
  }
  const fromLineIds = new Set(selectedSprings.value.map((s) => s.currentLineId))
  return lineStore.lines.filter((line) => !fromLineIds.has(line.id) && line.haltStatus !== 'HALTED')
})

/** 已选中的目标产线在列表刷新后变为停台时，提交前兜底提示 */
const selectedTargetHalted = computed(() => {
  if (!applyForm.toLineId) return null
  return lineStore.lines.find((l) => l.id === applyForm.toLineId && l.haltStatus === 'HALTED') ?? null
})

async function fetchSprings() {
  loading.value = true
  try {
    const response = await springApi.list({
      lineId: searchForm.lineId ?? undefined,
      keyword: searchForm.keyword || undefined,
      size: 100,
    })
    springs.value = response.data.content
  } finally {
    loading.value = false
  }
}

async function fetchRecentApplications() {
  try {
    const response = await applicationApi.list({ size: 5 })
    recentApplications.value = response.data.content
  } catch {
    // ignore
  }
}

function handleSearch() {
  fetchSprings()
}

function handleReset() {
  searchForm.lineId = null
  searchForm.keyword = ''
  fetchSprings()
}

function handleSelectAll() {
  if (selectedIds.value.length === selectableSprings.value.length) {
    selectedIds.value = []
  } else {
    selectedIds.value = selectableSprings.value.map((s) => s.id)
  }
}

function toggleSelect(id: number) {
  const spring = springs.value.find((s) => s.id === id)
  if (spring?.sealStatus === 'SEALED') {
    ElMessage.warning(`弹簧 ${spring.springCode} 处于封存状态，封存期间不能进入划转申请`)
    return
  }
  if (spring?.yellowFlag) {
    ElMessage.warning({
      message: `弹簧 ${spring.springCode} 弹力抽检偏离待闭环（黄标件，${spring.openDeviationCount ?? 0} 张未闭环留样），闭环处置前不能进入划转申请`,
      duration: 5000,
      showClose: true,
    })
    return
  }
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

async function handleSubmitApplication() {
  // 接班门禁：接班人须先确认昨夜复核单才能提交新划转
  if (reviewBlocked.value) {
    ElMessage.warning({
      message: `您还有 ${reviewPendingCount.value} 张夜班承载复核单未确认，请先确认后再提交划转`,
      duration: 6000,
      showClose: true,
    })
    goNightReview()
    return
  }
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要划转的弹簧')
    return
  }
  if (!applyForm.toLineId) {
    ElMessage.warning('请选择目标产线')
    return
  }
  if (!applyForm.applicant.trim()) {
    ElMessage.warning('请输入申请人')
    return
  }
  if (!applyForm.reason.trim()) {
    ElMessage.warning('请输入申请原因')
    return
  }
  if (selectedTargetHalted.value) {
    ElMessage.warning({
      message:
        `目标产线「${selectedTargetHalted.value.lineName}」临时停台中` +
        `（${selectedTargetHalted.value.haltReason || '停台原因未登记'}），不能作为划转接收方，请待复台后再提交`,
      duration: 6000,
      showClose: true,
    })
    return
  }

  const validSprings = selectedSprings.value.filter((s) => s.currentLineId !== applyForm.toLineId)
  if (validSprings.length === 0) {
    ElMessage.warning('所选弹簧已归属目标产线，无需划转')
    return
  }

  submitting.value = true
  try {
    const response = await applicationApi.submit({
      springIds: validSprings.map((s) => s.id),
      toLineId: applyForm.toLineId,
      applicant: applyForm.applicant.trim(),
      reason: applyForm.reason.trim(),
    })
    const app = response.data
    ElMessageBox.alert(
      `申请单号：${app.applicationNo}，共 ${app.totalCount ?? validSprings.length} 条弹簧已提交，待审批通过后生效。`,
      '划转申请提交成功',
      { confirmButtonText: '知道了', type: 'success' }
    )
    selectedIds.value = []
    applyForm.toLineId = null as unknown as number
    applyForm.reason = ''
    fetchRecentApplications()
  } catch (err) {
    // 后端拦截（如弹簧封存中）时给出明确原因，延长展示便于阅读
    ElMessage.error({
      message: err instanceof Error ? err.message : '申请提交失败',
      duration: 6000,
      showClose: true,
    })
  } finally {
    submitting.value = false
  }
}

const statusMap: Record<ApplicationStatus, { text: string; class: string }> = {
  PENDING: { text: '待审批', class: 'bg-amber-100 text-amber-700' },
  APPROVED: { text: '全部通过', class: 'bg-green-100 text-green-700' },
  REJECTED: { text: '全部驳回', class: 'bg-red-100 text-red-700' },
  PARTIAL: { text: '部分处理', class: 'bg-blue-100 text-blue-700' },
}

function formatTime(time: string) {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(async () => {
  await lineStore.fetchLines()
  fetchSprings()
  fetchRecentApplications()
  fetchReviewGuard()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 接班门禁：未确认昨夜复核单时置顶拦截，确认后自动放行 -->
    <div
      v-if="reviewBlocked"
      class="card-industrial p-4 border-l-4 border-amber-500 bg-amber-50 flex items-start gap-3"
    >
      <AlertTriangle class="w-6 h-6 text-amber-600 flex-shrink-0 mt-0.5" />
      <div class="flex-1">
        <div class="font-bold text-amber-800">接班交接未完成：{{ reviewPendingCount }} 张夜班承载复核单待确认</div>
        <p class="text-sm text-amber-700 mt-1">
          接班人须先打开看板确认昨夜全部复核单（填写跟进说明）后，才能提交新的划转申请。
        </p>
      </div>
      <button
        class="px-3 py-1.5 rounded-industrial bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 transition-colors"
        @click="goNightReview"
      >
        去确认
      </button>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
    <div class="lg:col-span-2 space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Cog class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">待划转弹簧列表</h2>
        </div>
        <div class="flex flex-wrap items-center gap-4 mb-4">
          <div class="flex items-center gap-2">
            <Factory class="w-4 h-4 text-primary-600" />
            <span class="text-sm font-medium text-industrial-700">当前产线：</span>
            <select
              v-model="searchForm.lineId"
              class="input-industrial w-44"
              @change="handleSearch"
            >
              <option :value="null">全部产线</option>
              <option v-for="line in lineStore.lines" :key="line.id" :value="line.id">
                {{ line.lineName }}
              </option>
            </select>
          </div>
          <div class="flex items-center gap-2 flex-1 max-w-sm">
            <Search class="w-4 h-4 text-industrial-400" />
            <input
              v-model="searchForm.keyword"
              type="text"
              class="input-industrial flex-1"
              placeholder="搜索弹簧编号..."
              @keyup.enter="handleSearch"
            />
            <button class="btn-industrial text-sm" @click="handleSearch">
              搜索
            </button>
            <button class="btn-industrial-outline text-sm" @click="handleReset">
              重置
            </button>
          </div>
        </div>

        <div class="max-h-96 overflow-y-auto border border-industrial-200 rounded-industrial">
          <table class="table-industrial text-sm">
            <thead class="sticky top-0">
              <tr>
                <th class="w-12 py-2">
                  <input
                    type="checkbox"
                    :checked="selectedIds.length === selectableSprings.length && selectableSprings.length > 0"
                    @change="handleSelectAll"
                    class="w-4 h-4"
                  />
                </th>
                <th class="py-2">弹簧编号</th>
                <th class="py-2">型号</th>
                <th class="py-2">弹力系数</th>
                <th class="py-2">当前产线</th>
              </tr>
            </thead>
            <tbody v-if="!loading && springs.length > 0">
              <tr
                v-for="spring in springs"
                :key="spring.id"
                class="transition-colors"
                :class="[
                  spring.sealStatus === 'SEALED' || spring.yellowFlag ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer',
                  { 'bg-primary-50': selectedIds.includes(spring.id) },
                  spring.yellowFlag && spring.sealStatus !== 'SEALED' ? 'bg-amber-50/60' : '',
                ]"
                @click="toggleSelect(spring.id)"
              >
                <td class="py-2" @click.stop>
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(spring.id)"
                    :disabled="spring.sealStatus === 'SEALED' || spring.yellowFlag"
                    @change="toggleSelect(spring.id)"
                    class="w-4 h-4 disabled:opacity-40 disabled:cursor-not-allowed"
                  />
                </td>
                <td class="py-2 font-mono text-xs font-medium text-primary-800">
                  {{ spring.springCode }}
                  <span
                    v-if="spring.sealStatus === 'SEALED'"
                    class="ml-1 px-1.5 py-0.5 bg-red-100 text-red-700 rounded text-xs font-medium cursor-help"
                    :title="`封存原因：${spring.sealReason || '未登记'}${spring.sealExpectedUnsealDate ? '，预计解封日：' + spring.sealExpectedUnsealDate : ''}`"
                  >
                    封存中
                  </span>
                  <span
                    v-else-if="spring.yellowFlag"
                    class="ml-1 inline-flex items-center px-1.5 py-0.5 bg-amber-100 text-amber-700 rounded text-xs font-medium cursor-help"
                    :title="`弹力抽检偏离待闭环（${spring.openDeviationCount ?? 0} 张未闭环留样），闭环前不能划转`"
                  >
                    <AlertTriangle class="w-3 h-3 mr-0.5" />
                    黄标
                  </span>
                </td>
                <td class="py-2">{{ spring.model }}</td>
                <td class="py-2 font-mono text-xs">{{ spring.elasticCoefficient }} N/mm</td>
                <td class="py-2">
                  <span class="px-2 py-0.5 bg-primary-100 text-primary-700 rounded text-xs">
                    {{ spring.currentLineName || lineStore.getLineName(spring.currentLineId) }}
                  </span>
                </td>
              </tr>
            </tbody>
            <tbody v-else-if="!loading">
              <tr>
                <td colspan="5" class="text-center py-12 text-industrial-400">
                  <Cog class="w-12 h-12 mx-auto mb-3 opacity-30" />
                  <p>暂无弹簧数据</p>
                </td>
              </tr>
            </tbody>
            <tbody v-else>
              <tr>
                <td colspan="5" class="text-center py-12 text-industrial-400">
                  <div class="animate-pulse">加载中...</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <GitBranch class="w-5 h-5 text-accent-600" />
          <h2 class="text-lg font-bold text-industrial-800">划转申请区</h2>
          <span class="ml-auto text-sm text-industrial-500">
            已选择 <span class="font-mono font-bold text-primary-700">{{ selectedIds.length }}</span> 项
          </span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div class="space-y-3">
            <label class="block text-sm font-medium text-industrial-700">
              <ArrowLeftRight class="w-4 h-4 inline mr-1" />
              目标产线 <span class="text-red-500">*</span>
            </label>
            <div class="space-y-2 max-h-48 overflow-y-auto">
              <label
                v-for="line in availableTargetLines"
                :key="line.id"
                class="flex items-center gap-3 p-3 rounded-industrial cursor-pointer transition-all border-2"
                :class="[
                  applyForm.toLineId === line.id
                    ? 'border-primary-600 bg-primary-50'
                    : 'border-transparent hover:bg-industrial-50',
                ]"
              >
                <input
                  type="radio"
                  :value="line.id"
                  v-model="applyForm.toLineId"
                  class="w-4 h-4 text-primary-600"
                />
                <div class="flex-1 min-w-0">
                  <div class="font-medium text-industrial-800 text-sm">{{ line.lineName }}</div>
                  <div class="text-xs text-industrial-500 font-mono">{{ line.lineCode }}</div>
                </div>
              </label>
              <div
                v-if="availableTargetLines.length === 0"
                class="text-center py-4 text-industrial-400 text-sm"
              >
                请先选择弹簧
              </div>
            </div>
            <!-- 停台产线：不能作为划转接收方，明确展示停台原因 -->
            <div
              v-if="haltedTargetLines.length > 0"
              class="mt-2 space-y-1"
            >
              <div
                v-for="line in haltedTargetLines"
                :key="line.id"
                class="flex items-center gap-2 p-2 rounded-industrial border border-red-200 bg-red-50 opacity-80"
                title="停台期间不能作为划转接收方"
              >
                <Ban class="w-4 h-4 text-red-500 flex-shrink-0" />
                <div class="flex-1 min-w-0">
                  <div class="text-sm text-red-700 line-through decoration-red-300">{{ line.lineName }}</div>
                  <div class="text-xs text-red-500 truncate">
                    停台中：{{ line.haltReason || '原因未登记' }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <Users class="w-4 h-4 inline mr-1" />
                申请人 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="applyForm.applicant"
                type="text"
                class="input-industrial"
                placeholder="请输入申请人姓名"
                maxlength="32"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <FileText class="w-4 h-4 inline mr-1" />
                申请原因 <span class="text-red-500">*</span>
              </label>
              <textarea
                v-model="applyForm.reason"
                class="input-industrial h-24 resize-none"
                placeholder="请输入划转申请原因"
                maxlength="255"
              ></textarea>
            </div>
          </div>

          <div class="flex flex-col justify-center">
            <div class="card-industrial p-4 bg-industrial-50">
              <h3 class="font-semibold text-industrial-800 mb-3">申请信息确认</h3>
              <div class="space-y-2 text-sm">
                <div class="flex justify-between">
                  <span class="text-industrial-600">选中数量：</span>
                  <span class="font-mono font-medium">{{ selectedIds.length }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">目标产线：</span>
                  <span class="font-medium text-accent-600">
                    {{ applyForm.toLineId ? lineStore.getLineName(applyForm.toLineId) : '未选择' }}
                  </span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">申请人：</span>
                  <span class="font-medium">{{ applyForm.applicant || '未填写' }}</span>
                </div>
                <div class="pt-2 mt-2 border-t border-industrial-200">
                  <button
                    class="w-full btn-industrial-accent"
                    :disabled="submitting || reviewBlocked || selectedIds.length === 0 || !applyForm.toLineId || !applyForm.applicant || !applyForm.reason"
                    @click="handleSubmitApplication"
                  >
                    <Send class="w-4 h-4 inline mr-1" />
                    {{ submitting ? '提交中...' : '提交划转申请' }}
                  </button>
                  <p class="text-xs text-industrial-400 mt-2 text-center">
                    申请提交后需审批通过才会执行划转
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Clock class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">最近划转申请</h2>
        </div>
        <div class="space-y-3">
          <div
            v-for="app in recentApplications"
            :key="app.id"
            class="p-3 bg-industrial-50 rounded-industrial animate-stagger"
            :style="{ animationDelay: `${recentApplications.indexOf(app) * 50}ms` }"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="font-mono text-xs font-medium text-primary-700">
                {{ app.applicationNo }}
              </span>
              <span
                class="px-2 py-0.5 rounded text-xs font-medium"
                :class="statusMap[app.status].class"
              >
                {{ statusMap[app.status].text }}
              </span>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <span class="text-industrial-600 text-xs">目标产线</span>
              <span class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded text-xs">
                {{ app.toLineName }}
              </span>
              <span class="text-xs text-industrial-400">
                共 {{ app.totalCount ?? '-' }} 条
              </span>
            </div>
            <div class="mt-2 flex items-center justify-between text-xs text-industrial-500">
              <span>
                <Users class="w-3 h-3 inline mr-1" />
                {{ app.applicant }}
              </span>
              <span class="font-mono">{{ formatTime(app.applyTime) }}</span>
            </div>
          </div>
          <div
            v-if="recentApplications.length === 0"
            class="text-center py-8 text-industrial-400 text-sm"
          >
            暂无划转申请
          </div>
        </div>
      </div>

      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Factory class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">产线概览</h2>
        </div>
        <div class="space-y-2">
          <div
            v-for="line in lineStore.lines"
            :key="line.id"
            class="flex items-center justify-between p-3 bg-industrial-50 rounded-industrial"
          >
            <div class="flex items-center gap-2">
              <div
                class="w-2 h-2 rounded-full"
                :class="line.haltStatus === 'HALTED' ? 'bg-red-500' : 'bg-accent-500'"
              ></div>
              <div>
                <div class="font-medium text-industrial-800 text-sm flex items-center gap-1">
                  {{ line.lineName }}
                  <OctagonPause
                    v-if="line.haltStatus === 'HALTED'"
                    class="w-3.5 h-3.5 text-red-500 cursor-help"
                    :title="`临时停台中：${line.haltReason || '原因未登记'}${line.haltExpectedResumeTime ? '，预计复台：' + formatTime(line.haltExpectedResumeTime) : ''}`"
                  />
                </div>
                <div class="text-xs text-industrial-500 font-mono">{{ line.lineCode }}</div>
              </div>
            </div>
            <span class="font-mono font-bold text-primary-800">
              {{ springs.filter(s => s.currentLineId === line.id).length }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
  </div>
</template>
