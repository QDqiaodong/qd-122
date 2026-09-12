<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applicationApi } from '@/api'
import type {
  TransferApplication,
  TransferApplicationLog,
  ApplicationDetail,
  ApplicationStatus,
  ItemStatus,
  ItemProcessResult,
} from '@/types'
import {
  ClipboardCheck,
  Search,
  FileText,
  Users,
  Clock,
  CheckCircle2,
  XCircle,
  History,
  Cog,
  ChevronRight,
  RefreshCw,
  OctagonPause,
  Flame,
  FlameKindling,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const VALID_STATUSES: ApplicationStatus[] = ['PENDING', 'PARTIAL', 'APPROVED', 'REJECTED']

function parseStatus(value: unknown): ApplicationStatus | null {
  return typeof value === 'string' && (VALID_STATUSES as string[]).includes(value)
    ? (value as ApplicationStatus)
    : null
}

const loading = ref(false)
const applications = ref<TransferApplication[]>([])

// 筛选条件在页面刷新后仍需保留：以路由 query 为持久化载体，刷新（F5）后从 URL 还原
function parseBooleanFlag(value: unknown): boolean | null {
  if (value === 'true') return true
  if (value === 'false') return false
  return null
}

const searchForm = reactive({
  status: parseStatus(route.query.status),
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  // 按目标产线是否停台筛选：true-仅停台 false-仅未停台 null-全部
  halted: parseBooleanFlag(route.query.halted),
  // 按是否加急筛选：true-仅加急 false-仅未加急 null-全部
  urgent: parseBooleanFlag(route.query.urgent),
})

const pagination = reactive({
  page: Math.max(0, (Number(route.query.page) || 1) - 1),
  size: 10,
  total: 0,
})

/** 将当前筛选条件与页码同步到 URL（replace 不产生多余历史记录），刷新后原样恢复 */
function syncQuery() {
  const query: Record<string, string> = {}
  if (searchForm.status) query.status = searchForm.status
  if (searchForm.keyword.trim()) query.keyword = searchForm.keyword.trim()
  if (searchForm.halted !== null) query.halted = String(searchForm.halted)
  if (searchForm.urgent !== null) query.urgent = String(searchForm.urgent)
  if (pagination.page > 0) query.page = String(pagination.page + 1)
  router.replace({ name: 'TransferApproval', query })
}

// 申请详情
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ApplicationDetail | null>(null)
const selectedItemIds = ref<number[]>([])
const approver = ref('')
const processing = ref(false)

// 驳回对话框
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const rejectTargetIds = ref<number[]>([])

// 加急/取消加急对话框：调度员标记加急填原因，取消加急填说明
const URGENT_OPERATOR_KEY = 'transfer-urgent-operator'
const urgentDialogVisible = ref(false)
const urgentMode = ref<'mark' | 'cancel'>('mark')
const urgentTarget = ref<TransferApplication | null>(null)
const urgentOperator = ref('')
const urgentReason = ref('')
const urgentSaving = ref(false)

const appStatusMap: Record<ApplicationStatus, { text: string; class: string }> = {
  PENDING: { text: '待审批', class: 'bg-amber-100 text-amber-700' },
  APPROVED: { text: '全部通过', class: 'bg-green-100 text-green-700' },
  REJECTED: { text: '全部驳回', class: 'bg-red-100 text-red-700' },
  PARTIAL: { text: '部分处理', class: 'bg-blue-100 text-blue-700' },
}

const itemStatusMap: Record<ItemStatus, { text: string; class: string }> = {
  PENDING: { text: '待审批', class: 'bg-amber-100 text-amber-700' },
  APPROVED: { text: '已通过', class: 'bg-green-100 text-green-700' },
  REJECTED: { text: '已驳回', class: 'bg-red-100 text-red-700' },
}

const pendingItems = computed(() => {
  return (detail.value?.items ?? []).filter((i) => i.status === 'PENDING')
})

const allPendingSelected = computed(() => {
  return pendingItems.value.length > 0 && selectedItemIds.value.length === pendingItems.value.length
})

async function fetchApplications() {
  loading.value = true
  try {
    const response = await applicationApi.list({
      status: searchForm.status ?? undefined,
      keyword: searchForm.keyword.trim() || undefined,
      halted: searchForm.halted ?? undefined,
      urgent: searchForm.urgent ?? undefined,
      page: pagination.page,
      size: pagination.size,
    })
    applications.value = response.data.content
    pagination.total = response.data.totalElements
    // 筛选条件下处理完最后一页数据后，自动回退到上一有数据的页，避免停留在空页
    if (applications.value.length === 0 && pagination.total > 0 && pagination.page > 0) {
      pagination.page = Math.max(0, Math.ceil(pagination.total / pagination.size) - 1)
      syncQuery()
      await fetchApplications()
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载申请列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 0
  syncQuery()
  fetchApplications()
}

function handleReset() {
  searchForm.status = null
  searchForm.keyword = ''
  searchForm.halted = null
  searchForm.urgent = null
  pagination.page = 0
  syncQuery()
  fetchApplications()
}

/** 手动刷新：保留当前筛选条件，仅重新拉取列表，打开详情时同步刷新详情 */
async function handleRefresh() {
  const tasks: Promise<unknown>[] = [fetchApplications()]
  if (detailVisible.value && detail.value) {
    tasks.push(fetchDetail(detail.value.application.id))
  }
  await Promise.all(tasks)
  ElMessage.success('已刷新为最新状态')
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  syncQuery()
  fetchApplications()
}

async function openDetail(app: TransferApplication) {
  detailVisible.value = true
  selectedItemIds.value = []
  await fetchDetail(app.id)
  // 打开含待审批明细且目标产线处于停台的申请单时给出明确提示
  if (app.toLineHalted && (app.pendingCount ?? 0) > 0) {
    ElMessageBox.alert(
      `目标产线「${app.toLineName}」当前处于临时停台状态（停台原因：${app.toLineHaltReason || '未登记'}` +
        `${app.toLineExpectedResumeTime ? '，预计复台：' + formatTime(app.toLineExpectedResumeTime) : ''}）。` +
        `该申请单的待审批明细在审批通过时将被拦截，须待产线复台后方可继续划转；如需处理可先驳回。`,
      '目标产线停台提示',
      { confirmButtonText: '知道了', type: 'warning' }
    )
  }
}

async function fetchDetail(id: number) {
  detailLoading.value = true
  try {
    const response = await applicationApi.detail(id)
    detail.value = response.data
    // 详情每次都以服务端最新状态为准；已处理明细（可能已被其他审批人处理）从勾选中剔除，
    // 杜绝拿着旧勾选集对已通过/已驳回明细再次发起审批
    selectedItemIds.value = selectedItemIds.value.filter((itemId) =>
      response.data.items.some((i) => i.id === itemId && i.status === 'PENDING')
    )
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载申请详情失败')
  } finally {
    detailLoading.value = false
  }
}

function toggleSelectAllPending() {
  if (allPendingSelected.value) {
    selectedItemIds.value = []
  } else {
    selectedItemIds.value = pendingItems.value.map((i) => i.id)
  }
}

function toggleSelectItem(id: number) {
  const index = selectedItemIds.value.indexOf(id)
  if (index > -1) {
    selectedItemIds.value.splice(index, 1)
  } else {
    selectedItemIds.value.push(id)
  }
}

function checkApprover(): boolean {
  if (!approver.value.trim()) {
    ElMessage.warning('请先填写审批人')
    return false
  }
  return true
}

async function handleApprove(itemIds: number[]) {
  if (itemIds.length === 0) {
    ElMessage.warning('请选择要审批的申请明细')
    return
  }
  if (!checkApprover()) return

  processing.value = true
  try {
    const response = await applicationApi.approve({
      itemIds,
      approver: approver.value.trim(),
    })
    showProcessResults(response.data, '审批通过')
    await refreshAfterProcess()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '审批操作失败')
  } finally {
    processing.value = false
  }
}

function openRejectDialog(itemIds: number[]) {
  if (itemIds.length === 0) {
    ElMessage.warning('请选择要驳回的申请明细')
    return
  }
  if (!checkApprover()) return
  rejectTargetIds.value = itemIds
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function handleRejectConfirm() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('驳回时必须填写驳回原因')
    return
  }
  processing.value = true
  try {
    const response = await applicationApi.reject({
      itemIds: rejectTargetIds.value,
      approver: approver.value.trim(),
      reason: rejectReason.value.trim(),
    })
    rejectDialogVisible.value = false
    showProcessResults(response.data, '驳回')
    await refreshAfterProcess()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '驳回操作失败')
  } finally {
    processing.value = false
  }
}

function showProcessResults(results: ItemProcessResult[], action: string) {
  const successCount = results.filter((r) => r.success).length
  const failures = results.filter((r) => !r.success)
  if (failures.length === 0) {
    ElMessage.success(`${action}成功，共处理 ${successCount} 条`)
  } else {
    const failureHtml = failures
      .map((f) => `<div class="text-left text-sm py-1">• ${f.springCode ?? `明细#${f.itemId}`}：${f.message}</div>`)
      .join('')
    ElMessageBox.alert(
      `<div class="text-left mb-2">成功 ${successCount} 条，失败 ${failures.length} 条：</div>${failureHtml}`,
      `${action}结果`,
      { confirmButtonText: '知道了', dangerouslyUseHTMLString: true, type: 'warning' }
    )
  }
}

async function refreshAfterProcess() {
  selectedItemIds.value = []
  if (detail.value) {
    await fetchDetail(detail.value.application.id)
  }
  fetchApplications()
}

/** 已结案（全部通过/全部驳回）单据不能再标记加急 */
function isClosed(app: TransferApplication): boolean {
  return app.status === 'APPROVED' || app.status === 'REJECTED'
}

/** 打开加急/取消加急对话框：前端先做一层明显拦截，后端仍会校验并返回明确原因 */
function openUrgentDialog(app: TransferApplication, mode: 'mark' | 'cancel') {
  if (mode === 'mark') {
    if (isClosed(app)) {
      ElMessage.warning(`该申请单已结案（${appStatusMap[app.status].text}），不能再标记加急`)
      return
    }
    if (app.urgent) {
      ElMessage.warning('该申请单已标记加急，请勿重复加急；如需调整可先取消加急后重新标记')
      return
    }
  } else if (!app.urgent) {
    ElMessage.warning('该申请单未标记加急，无需取消')
    return
  }
  urgentTarget.value = app
  urgentMode.value = mode
  urgentOperator.value = localStorage.getItem(URGENT_OPERATOR_KEY) ?? ''
  urgentReason.value = ''
  urgentDialogVisible.value = true
}

async function handleUrgentConfirm() {
  const app = urgentTarget.value
  if (!app) return
  if (!urgentOperator.value.trim()) {
    ElMessage.warning('请填写调度员姓名')
    return
  }
  if (!urgentReason.value.trim()) {
    ElMessage.warning(urgentMode.value === 'mark' ? '标记加急必须填写加急原因' : '取消加急必须填写取消说明')
    return
  }
  urgentSaving.value = true
  try {
    const payload = { operator: urgentOperator.value.trim(), reason: urgentReason.value.trim() }
    if (urgentMode.value === 'mark') {
      await applicationApi.urgent(app.id, payload)
      ElMessage.success(`申请单 ${app.applicationNo} 已标记加急，将优先展示在审批台`)
    } else {
      await applicationApi.cancelUrgent(app.id, payload)
      ElMessage.success(`申请单 ${app.applicationNo} 已取消加急`)
    }
    localStorage.setItem(URGENT_OPERATOR_KEY, urgentOperator.value.trim())
    urgentDialogVisible.value = false
    // 加急影响列表排序与看板统计：刷新列表；详情抽屉打开时同步刷新详情（含操作记录）
    if (detailVisible.value && detail.value && detail.value.application.id === app.id) {
      await fetchDetail(app.id)
    }
    await fetchApplications()
  } catch (err) {
    ElMessage.error({
      message: err instanceof Error ? err.message : '加急操作失败',
      duration: 6000,
      showClose: true,
    })
  } finally {
    urgentSaving.value = false
  }
}

function logActionText(action: TransferApplicationLog['action']) {
  return (
    {
      SUBMIT: '提交申请',
      APPROVE: '审批通过',
      REJECT: '审批驳回',
      URGENT: '标记加急',
      URGENT_CANCEL: '取消加急',
    } as Record<TransferApplicationLog['action'], string>
  )[action]
}

function logActionClass(action: TransferApplicationLog['action']) {
  return (
    {
      SUBMIT: 'bg-primary-100 text-primary-700',
      APPROVE: 'bg-green-100 text-green-700',
      REJECT: 'bg-red-100 text-red-700',
      URGENT: 'bg-red-100 text-red-700',
      URGENT_CANCEL: 'bg-industrial-100 text-industrial-600',
    } as Record<TransferApplicationLog['action'], string>
  )[action]
}

function formatTime(time?: string) {
  return time ? time.replace('T', ' ').substring(0, 19) : '-'
}

onMounted(() => {
  fetchApplications()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="card-industrial p-4">
      <div class="flex items-center gap-2 mb-4">
        <ClipboardCheck class="w-5 h-5 text-primary-600" />
        <h2 class="text-lg font-bold text-industrial-800">划转申请审批</h2>
      </div>
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-industrial-700">审批状态：</span>
          <select v-model="searchForm.status" class="input-industrial w-40" @change="handleSearch">
            <option :value="null">全部状态</option>
            <option value="PENDING">待审批</option>
            <option value="PARTIAL">部分处理</option>
            <option value="APPROVED">全部通过</option>
            <option value="REJECTED">全部驳回</option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-industrial-700">目标产线：</span>
          <select v-model="searchForm.halted" class="input-industrial w-36" @change="handleSearch">
            <option :value="null">全部</option>
            <option :value="true">停台中</option>
            <option :value="false">未停台</option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-industrial-700">加急：</span>
          <select v-model="searchForm.urgent" class="input-industrial w-32" @change="handleSearch">
            <option :value="null">全部</option>
            <option :value="true">仅加急</option>
            <option :value="false">未加急</option>
          </select>
        </div>
        <div class="flex items-center gap-2 flex-1 max-w-md">
          <Search class="w-4 h-4 text-industrial-400" />
          <input
            v-model="searchForm.keyword"
            type="text"
            class="input-industrial flex-1"
            placeholder="搜索申请单号或申请人..."
            @keyup.enter="handleSearch"
          />
          <button class="btn-industrial text-sm" @click="handleSearch">搜索</button>
          <button class="btn-industrial-outline text-sm" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <div class="card-industrial overflow-hidden">
      <div class="overflow-x-auto">
        <table class="table-industrial">
          <thead>
            <tr>
              <th>申请单号</th>
              <th>申请人</th>
              <th>申请时间</th>
              <th>目标产线</th>
              <th>申请原因</th>
              <th>明细进度</th>
              <th>状态</th>
              <th class="w-28">操作</th>
            </tr>
          </thead>
          <tbody v-if="!loading && applications.length > 0">
            <tr
              v-for="(app, index) in applications"
              :key="app.id"
              class="animate-stagger"
              :class="{ 'bg-red-50/50': app.urgent }"
              :style="{ animationDelay: `${index * 30}ms` }"
            >
              <td class="font-mono text-sm font-medium text-primary-800">
                <div class="flex flex-col items-start gap-1">
                  <span>{{ app.applicationNo }}</span>
                  <span
                    v-if="app.urgent"
                    class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700 cursor-help"
                    :title="`加急原因：${app.urgentReason || '未填写'}${app.urgentOperator ? '，加急人：' + app.urgentOperator : ''}${app.urgentTime ? '，加急时间：' + formatTime(app.urgentTime) : ''}`"
                  >
                    <Flame class="w-3 h-3" />
                    加急
                  </span>
                </div>
              </td>
              <td>
                <Users class="w-4 h-4 inline mr-1 text-industrial-400" />
                {{ app.applicant }}
              </td>
              <td class="font-mono text-sm text-industrial-500">{{ formatTime(app.applyTime) }}</td>
              <td>
                <div class="flex flex-col items-start gap-1">
                  <span class="px-2 py-1 bg-accent-100 text-accent-700 rounded text-xs font-medium">
                    {{ app.toLineName }}
                  </span>
                  <span
                    v-if="app.toLineHalted"
                    class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700 cursor-help"
                    :title="`停台原因：${app.toLineHaltReason || '未登记'}${app.toLineExpectedResumeTime ? '，预计复台：' + formatTime(app.toLineExpectedResumeTime) : ''}`"
                  >
                    <OctagonPause class="w-3 h-3" />
                    停台·接收将被拦
                  </span>
                </div>
              </td>
              <td class="max-w-48">
                <span class="text-sm text-industrial-600 truncate block" :title="app.reason">
                  {{ app.reason }}
                </span>
              </td>
              <td class="font-mono text-sm">
                <span class="text-green-600">{{ app.approvedCount ?? 0 }}</span> /
                <span class="text-red-600">{{ app.rejectedCount ?? 0 }}</span> /
                <span class="text-amber-600">{{ app.pendingCount ?? 0 }}</span>
                <span class="text-industrial-400 text-xs">（过/驳/待）</span>
              </td>
              <td>
                <span
                  class="px-2 py-1 rounded text-xs font-medium"
                  :class="appStatusMap[app.status].class"
                >
                  {{ appStatusMap[app.status].text }}
                </span>
              </td>
              <td>
                <div class="flex flex-col items-start gap-1.5">
                  <button class="btn-industrial-outline text-xs px-3 py-1" @click="openDetail(app)">
                    详情 / 审批
                  </button>
                  <button
                    v-if="app.urgent"
                    class="inline-flex items-center gap-1 px-2 py-1 text-xs rounded border border-industrial-300 text-industrial-600 hover:bg-industrial-50"
                    title="取消加急（须填写取消说明）"
                    @click="openUrgentDialog(app, 'cancel')"
                  >
                    <FlameKindling class="w-3.5 h-3.5" />
                    取消加急
                  </button>
                  <button
                    v-else-if="!isClosed(app)"
                    class="inline-flex items-center gap-1 px-2 py-1 text-xs rounded border border-red-300 text-red-600 hover:bg-red-50"
                    title="标记加急（须填写加急原因），加急单优先展示在审批台"
                    @click="openUrgentDialog(app, 'mark')"
                  >
                    <Flame class="w-3.5 h-3.5" />
                    加急
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
          <tbody v-else-if="!loading">
            <tr>
              <td colspan="8" class="text-center py-16 text-industrial-400">
                <ClipboardCheck class="w-16 h-16 mx-auto mb-4 opacity-30" />
                <p class="text-lg">暂无划转申请</p>
                <p class="text-sm mt-2">在「产线划转操作」页面提交划转申请后可在此审批</p>
              </td>
            </tr>
          </tbody>
          <tbody v-else>
            <tr>
              <td colspan="8" class="text-center py-16 text-industrial-400">
                <div class="animate-pulse">加载中...</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div
        v-if="pagination.total > 0"
        class="flex items-center justify-between px-6 py-4 border-t border-industrial-200"
      >
        <div class="text-sm text-industrial-600">
          共 <span class="font-mono font-medium">{{ pagination.total }}</span> 条申请
        </div>
        <div class="flex items-center gap-2">
          <button
            class="px-3 py-1 rounded-industrial border border-industrial-200 text-sm disabled:opacity-50"
            :disabled="pagination.page === 0"
            @click="handlePageChange(pagination.page)"
          >
            上一页
          </button>
          <span class="text-sm text-industrial-600">
            第 <span class="font-mono font-medium">{{ pagination.page + 1 }}</span> 页 / 共
            <span class="font-mono font-medium">{{ Math.ceil(pagination.total / pagination.size) }}</span> 页
          </span>
          <button
            class="px-3 py-1 rounded-industrial border border-industrial-200 text-sm disabled:opacity-50"
            :disabled="pagination.page >= Math.ceil(pagination.total / pagination.size) - 1"
            @click="handlePageChange(pagination.page + 2)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="detailVisible"
      size="75%"
      :title="detail ? `申请详情 - ${detail.application.applicationNo}` : '申请详情'"
    >
      <div v-if="detailLoading" class="text-center py-16 text-industrial-400">
        <div class="animate-pulse">加载中...</div>
      </div>
      <div v-else-if="detail" class="space-y-6">
        <div class="card-industrial p-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="font-semibold text-industrial-800">
              <FileText class="w-4 h-4 inline mr-1 text-primary-600" />
              申请信息
            </h3>
            <div class="flex items-center gap-2">
              <span
                v-if="detail.application.urgent"
                class="inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium bg-red-100 text-red-700"
              >
                <Flame class="w-3.5 h-3.5" />
                加急单
              </span>
              <span
                class="px-2 py-1 rounded text-xs font-medium"
                :class="appStatusMap[detail.application.status].class"
              >
                {{ appStatusMap[detail.application.status].text }}
              </span>
              <button
                v-if="detail.application.urgent"
                class="inline-flex items-center gap-1 px-2 py-1 text-xs rounded border border-industrial-300 text-industrial-600 hover:bg-industrial-50"
                title="取消加急（须填写取消说明）"
                @click="openUrgentDialog(detail.application, 'cancel')"
              >
                <FlameKindling class="w-3.5 h-3.5" />
                取消加急
              </button>
              <button
                v-else-if="!isClosed(detail.application)"
                class="inline-flex items-center gap-1 px-2 py-1 text-xs rounded border border-red-300 text-red-600 hover:bg-red-50"
                title="标记加急（须填写加急原因），加急单优先展示在审批台"
                @click="openUrgentDialog(detail.application, 'mark')"
              >
                <Flame class="w-3.5 h-3.5" />
                加急
              </button>
            </div>
          </div>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <div class="text-industrial-400 text-xs mb-1">申请单号</div>
              <div class="font-mono font-medium text-primary-800">{{ detail.application.applicationNo }}</div>
            </div>
            <div>
              <div class="text-industrial-400 text-xs mb-1">申请人</div>
              <div class="font-medium">{{ detail.application.applicant }}</div>
            </div>
            <div>
              <div class="text-industrial-400 text-xs mb-1">申请时间</div>
              <div class="font-mono">{{ formatTime(detail.application.applyTime) }}</div>
            </div>
            <div>
              <div class="text-industrial-400 text-xs mb-1">目标产线</div>
              <span class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded text-xs font-medium">
                {{ detail.application.toLineName }}
              </span>
            </div>
            <div class="col-span-2 md:col-span-4">
              <div class="text-industrial-400 text-xs mb-1">申请原因</div>
              <div class="text-industrial-700">{{ detail.application.reason }}</div>
            </div>
          </div>

          <!-- 加急信息：调度员标记的加急原因/加急人/加急时间 -->
          <div
            v-if="detail.application.urgent"
            class="mt-3 flex items-start gap-2 rounded-industrial border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            <Flame class="w-4 h-4 mt-0.5 flex-shrink-0" />
            <div>
              加急原因：{{ detail.application.urgentReason || '未填写' }}
              <span class="text-xs text-red-500 ml-2">
                加急人 {{ detail.application.urgentOperator || '-' }} · {{ formatTime(detail.application.urgentTime ?? undefined) }}
              </span>
            </div>
          </div>

          <!-- 目标产线停台提示：待审批明细将在审批通过时被拦截 -->
          <div
            v-if="detail.application.toLineHalted && (detail.application.pendingCount ?? 0) > 0"
            class="mt-3 flex items-start gap-2 rounded-industrial border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            <OctagonPause class="w-4 h-4 mt-0.5 flex-shrink-0" />
            <div>
              目标产线「{{ detail.application.toLineName }}」临时停台中
              （停台原因：{{ detail.application.toLineHaltReason || '未登记' }}<template
                v-if="detail.application.toLineExpectedResumeTime">，预计复台：{{ formatTime(detail.application.toLineExpectedResumeTime) }}</template>）。
              待审批明细审批通过时将被拦截，请待产线复台后再处理，或先驳回并通知申请人。
            </div>
          </div>
        </div>

        <div class="card-industrial p-4">
          <div class="flex flex-wrap items-center gap-3 mb-3">
            <h3 class="font-semibold text-industrial-800">
              <Cog class="w-4 h-4 inline mr-1 text-primary-600" />
              弹簧明细（{{ detail.items.length }}）
            </h3>
            <div class="ml-auto flex flex-wrap items-center gap-2">
              <input
                v-model="approver"
                type="text"
                class="input-industrial w-32 !py-1.5 text-sm"
                placeholder="审批人姓名"
                maxlength="32"
              />
              <button
                class="btn-industrial text-sm"
                :disabled="processing || selectedItemIds.length === 0"
                @click="handleApprove(selectedItemIds)"
              >
                <CheckCircle2 class="w-4 h-4 inline mr-1" />
                批量通过 ({{ selectedItemIds.length }})
              </button>
              <button
                class="btn-industrial-accent text-sm"
                :disabled="processing || selectedItemIds.length === 0"
                @click="openRejectDialog(selectedItemIds)"
              >
                <XCircle class="w-4 h-4 inline mr-1" />
                批量驳回
              </button>
            </div>
          </div>

          <div class="overflow-x-auto border border-industrial-200 rounded-industrial">
            <table class="table-industrial text-sm">
              <thead>
                <tr>
                  <th class="w-10 py-2">
                    <input
                      type="checkbox"
                      :checked="allPendingSelected"
                      :disabled="pendingItems.length === 0"
                      class="w-4 h-4"
                      @change="toggleSelectAllPending"
                    />
                  </th>
                  <th class="py-2">弹簧编号</th>
                  <th class="py-2">型号</th>
                  <th class="py-2">弹力系数</th>
                  <th class="py-2">外径</th>
                  <th class="py-2">产线划转</th>
                  <th class="py-2">状态</th>
                  <th class="py-2">处理人 / 时间</th>
                  <th class="py-2">驳回原因</th>
                  <th class="py-2 w-32">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.items" :key="item.id">
                  <td class="py-2">
                    <input
                      v-if="item.status === 'PENDING'"
                      type="checkbox"
                      :checked="selectedItemIds.includes(item.id)"
                      class="w-4 h-4"
                      @change="toggleSelectItem(item.id)"
                    />
                  </td>
                  <td class="py-2 font-mono text-xs font-medium text-primary-800">
                    {{ item.springCode }}
                  </td>
                  <td class="py-2">{{ item.model }}</td>
                  <td class="py-2 font-mono text-xs">{{ item.elasticCoefficient }} N/mm</td>
                  <td class="py-2 font-mono text-xs">{{ item.outerDiameter }} mm</td>
                  <td class="py-2">
                    <div class="flex items-center gap-1 text-xs">
                      <span class="px-2 py-0.5 bg-primary-100 text-primary-700 rounded">
                        {{ item.fromLineName }}
                      </span>
                      <ChevronRight class="w-3 h-3 text-industrial-400" />
                      <span class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded">
                        {{ item.toLineName }}
                      </span>
                    </div>
                  </td>
                  <td class="py-2">
                    <span
                      class="px-2 py-0.5 rounded text-xs font-medium"
                      :class="itemStatusMap[item.status].class"
                    >
                      {{ itemStatusMap[item.status].text }}
                    </span>
                    <div v-if="item.transferRecordId" class="text-xs text-industrial-400 mt-1 font-mono">
                      流水 #{{ item.transferRecordId }}
                    </div>
                  </td>
                  <td class="py-2 text-xs">
                    <template v-if="item.approver">
                      <div class="font-medium">{{ item.approver }}</div>
                      <div class="text-industrial-400 font-mono">{{ formatTime(item.approveTime) }}</div>
                    </template>
                    <span v-else class="text-industrial-300">-</span>
                  </td>
                  <td class="py-2 text-xs max-w-36">
                    <span v-if="item.rejectReason" class="text-red-600" :title="item.rejectReason">
                      {{ item.rejectReason }}
                    </span>
                    <span v-else class="text-industrial-300">-</span>
                  </td>
                  <td class="py-2">
                    <div v-if="item.status === 'PENDING'" class="flex gap-1">
                      <button
                        class="px-2 py-1 text-xs rounded bg-green-600 text-white hover:bg-green-700 disabled:opacity-50"
                        :disabled="processing"
                        @click="handleApprove([item.id])"
                      >
                        通过
                      </button>
                      <button
                        class="px-2 py-1 text-xs rounded bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
                        :disabled="processing"
                        @click="openRejectDialog([item.id])"
                      >
                        驳回
                      </button>
                    </div>
                    <span v-else class="text-industrial-300 text-xs">已处理</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="card-industrial p-4">
          <h3 class="font-semibold text-industrial-800 mb-4">
            <History class="w-4 h-4 inline mr-1 text-primary-600" />
            操作记录
          </h3>
          <div class="space-y-0">
            <div
              v-for="(log, index) in detail.logs"
              :key="log.id"
              class="flex gap-3 pb-4 relative"
            >
              <div class="flex flex-col items-center">
                <span
                  class="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0"
                  :class="logActionClass(log.action)"
                >
                  {{ index + 1 }}
                </span>
                <div
                  v-if="index < detail.logs.length - 1"
                  class="w-px flex-1 bg-industrial-200 mt-1"
                ></div>
              </div>
              <div class="flex-1 min-w-0 pb-1">
                <div class="flex flex-wrap items-center gap-2 text-sm">
                  <span
                    class="px-2 py-0.5 rounded text-xs font-medium"
                    :class="logActionClass(log.action)"
                  >
                    {{ logActionText(log.action) }}
                  </span>
                  <span class="font-medium text-industrial-800">{{ log.operator }}</span>
                  <span class="text-xs text-industrial-400 font-mono">
                    <Clock class="w-3 h-3 inline mr-1" />
                    {{ formatTime(log.operateTime) }}
                  </span>
                </div>
                <div class="text-sm text-industrial-600 mt-1">{{ log.detail }}</div>
              </div>
            </div>
            <div v-if="detail.logs.length === 0" class="text-center py-6 text-industrial-400 text-sm">
              暂无操作记录
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="rejectDialogVisible" title="驳回划转申请" width="480px">
      <div class="space-y-4">
        <p class="text-sm text-industrial-600">
          将驳回 <span class="font-mono font-medium">{{ rejectTargetIds.length }}</span> 条申请明细，驳回原因必填。
        </p>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            驳回原因 <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="rejectReason"
            class="input-industrial h-24 resize-none"
            placeholder="请输入驳回原因"
            maxlength="255"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-industrial-outline" @click="rejectDialogVisible = false">取消</button>
          <button class="btn-industrial-accent" :disabled="processing" @click="handleRejectConfirm">
            {{ processing ? '处理中...' : '确认驳回' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="urgentDialogVisible"
      :title="urgentMode === 'mark' ? '标记加急' : '取消加急'"
      width="480px"
    >
      <div v-if="urgentTarget" class="space-y-4">
        <div class="p-3 bg-industrial-50 rounded-industrial text-sm space-y-1">
          <div class="flex justify-between">
            <span class="text-industrial-600">申请单号：</span>
            <span class="font-mono font-medium text-primary-800">{{ urgentTarget.applicationNo }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-industrial-600">目标产线：</span>
            <span class="font-medium">{{ urgentTarget.toLineName }}</span>
          </div>
        </div>
        <p v-if="urgentMode === 'mark'" class="text-sm text-industrial-600">
          标记加急后该申请单将优先展示在审批台，并计入看板「加急待批」统计；申请单结案后加急标记自动解除。
        </p>
        <p v-else class="text-sm text-industrial-600">
          取消加急须填写取消说明，取消后该申请单恢复按申请时间排序。
        </p>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            调度员 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="urgentOperator"
            type="text"
            class="input-industrial"
            placeholder="请输入调度员姓名"
            maxlength="32"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            {{ urgentMode === 'mark' ? '加急原因' : '取消说明' }} <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="urgentReason"
            class="input-industrial h-24 resize-none"
            :placeholder="urgentMode === 'mark' ? '例如：客户催单/产线待料，需优先划转' : '例如：交期已协调，无需加急处理'"
            maxlength="255"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-industrial-outline" @click="urgentDialogVisible = false">取消</button>
          <button
            v-if="urgentMode === 'mark'"
            class="px-4 py-2 rounded-industrial bg-red-600 text-white text-sm font-medium hover:bg-red-700 transition-colors disabled:opacity-50"
            :disabled="urgentSaving"
            @click="handleUrgentConfirm"
          >
            <Flame class="w-4 h-4 inline mr-1" />
            {{ urgentSaving ? '提交中...' : '确认加急' }}
          </button>
          <button
            v-else
            class="btn-industrial"
            :disabled="urgentSaving"
            @click="handleUrgentConfirm"
          >
            {{ urgentSaving ? '提交中...' : '确认取消加急' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
