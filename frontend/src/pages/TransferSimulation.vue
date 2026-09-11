<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { springApi, simulationApi } from '@/api'
import type {
  SpringArchive,
  SimulationEstimate,
  TransferSimulation,
  SimulationDetail,
  SimulationStatus,
  ApplicationStatus,
} from '@/types'
import SimulationEstimatePanel from '@/components/SimulationEstimatePanel.vue'
import {
  FlaskConical,
  Search,
  Factory,
  Cog,
  ArrowLeftRight,
  Users,
  FileText,
  Save,
  Play,
  Trash2,
  Eye,
  CheckCircle2,
  RefreshCw,
  ClipboardList,
} from 'lucide-vue-next'

const lineStore = useLineStore()
const loading = ref(false)
const estimating = ref(false)
const saving = ref(false)
const springs = ref<SpringArchive[]>([])
const selectedIds = ref<number[]>([])
const estimate = ref<SimulationEstimate | null>(null)

const searchForm = reactive({
  lineId: null as number | null,
  keyword: '',
})

const simForm = reactive({
  toLineId: null as number | null,
  operator: '',
  remark: '',
})

// ---------------- 已保存方案 ----------------
const plans = ref<TransferSimulation[]>([])
const plansLoading = ref(false)
const planFilter = ref<SimulationStatus | ''>('')
const planPage = ref(0)
const planTotalPages = ref(0)
const planTotal = ref(0)

// ---------------- 详情弹窗 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SimulationDetail | null>(null)

// ---------------- 采用弹窗 ----------------
const adoptVisible = ref(false)
const adopting = ref(false)
const adoptTarget = ref<TransferSimulation | null>(null)
const adoptForm = reactive({
  applicant: '',
  reason: '',
})

const selectedSprings = computed(() => {
  return springs.value.filter((s) => selectedIds.value.includes(s.id))
})

/** 封存中的弹簧（抽检不合格/待复测）不能进入调拨模拟 */
const selectableSprings = computed(() => springs.value.filter((s) => s.sealStatus !== 'SEALED'))

const availableTargetLines = computed(() => {
  if (selectedSprings.value.length === 0) return lineStore.lines
  const fromLineIds = new Set(selectedSprings.value.map((s) => s.currentLineId))
  return lineStore.lines.filter((line) => !fromLineIds.has(line.id))
})

// 选择或目标产线变化后，原预估结果失效
watch([selectedIds, () => simForm.toLineId], () => {
  estimate.value = null
}, { deep: true })

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

async function fetchPlans() {
  plansLoading.value = true
  try {
    const response = await simulationApi.list({
      status: planFilter.value || undefined,
      page: planPage.value,
      size: 8,
    })
    plans.value = response.data.content
    planTotalPages.value = response.data.totalPages
    planTotal.value = response.data.totalElements
  } catch {
    // ignore
  } finally {
    plansLoading.value = false
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
    ElMessage.warning(`弹簧 ${spring.springCode} 处于封存状态，封存期间不能进入调拨模拟`)
    return
  }
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

function validateSelection(): boolean {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要模拟调拨的弹簧')
    return false
  }
  if (!simForm.toLineId) {
    ElMessage.warning('请选择拟接收产线')
    return false
  }
  return true
}

async function handlePreview() {
  if (!validateSelection()) return
  estimating.value = true
  try {
    const response = await simulationApi.preview({
      springIds: selectedIds.value,
      toLineId: simForm.toLineId!,
    })
    estimate.value = response.data
  } catch (err) {
    showBlockError(err, '负载预估失败')
  } finally {
    estimating.value = false
  }
}

async function handleSave() {
  if (!validateSelection()) return
  if (!simForm.operator.trim()) {
    ElMessage.warning('请输入调度员')
    return
  }
  saving.value = true
  try {
    const response = await simulationApi.save({
      springIds: selectedIds.value,
      toLineId: simForm.toLineId!,
      operator: simForm.operator.trim(),
      remark: simForm.remark.trim() || undefined,
    })
    ElMessage.success(`模拟方案 ${response.data.simulationNo} 已保存，可采用或作废`)
    planPage.value = 0
    fetchPlans()
  } catch (err) {
    showBlockError(err, '方案保存失败')
  } finally {
    saving.value = false
  }
}

/** 后端拦截（如弹簧封存中）时给出明确原因，延长展示便于阅读 */
function showBlockError(err: unknown, fallback: string) {
  ElMessage.error({
    message: err instanceof Error ? err.message : fallback,
    duration: 6000,
    showClose: true,
  })
}

async function handleDetail(sim: TransferSimulation) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response = await simulationApi.detail(sim.id)
    detail.value = response.data
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载方案详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function handleAdopt(sim: TransferSimulation) {
  adoptTarget.value = sim
  adoptForm.applicant = sim.operator
  adoptForm.reason = ''
  adoptVisible.value = true
}

async function confirmAdopt() {
  if (!adoptTarget.value) return
  if (!adoptForm.applicant.trim()) {
    ElMessage.warning('请输入申请人')
    return
  }
  adopting.value = true
  try {
    const response = await simulationApi.adopt(adoptTarget.value.id, {
      applicant: adoptForm.applicant.trim(),
      reason: adoptForm.reason.trim() || undefined,
    })
    adoptVisible.value = false
    ElMessageBox.alert(
      `已生成待审批划转申请：${response.data.applicationNo}，审批通过后弹簧归属才会变更。`,
      '方案采用成功',
      { confirmButtonText: '知道了', type: 'success' }
    )
    fetchPlans()
  } catch (err) {
    showBlockError(err, '方案采用失败')
  } finally {
    adopting.value = false
  }
}

async function handleDiscard(sim: TransferSimulation) {
  try {
    await ElMessageBox.confirm(
      `确定作废模拟方案 ${sim.simulationNo} 吗？作废后不可采用。`,
      '作废确认',
      { confirmButtonText: '确定作废', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await simulationApi.discard(sim.id)
    ElMessage.success('方案已作废')
    fetchPlans()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '作废失败')
  }
}

function changePlanPage(delta: number) {
  const next = planPage.value + delta
  if (next < 0 || next >= planTotalPages.value) return
  planPage.value = next
  fetchPlans()
}

const simStatusMap: Record<SimulationStatus, { text: string; class: string }> = {
  DRAFT: { text: '已保存', class: 'bg-blue-100 text-blue-700' },
  ADOPTED: { text: '已采用', class: 'bg-green-100 text-green-700' },
  DISCARDED: { text: '已作废', class: 'bg-industrial-200 text-industrial-500' },
}

const appStatusMap: Record<ApplicationStatus, { text: string; class: string }> = {
  PENDING: { text: '待审批', class: 'bg-amber-100 text-amber-700' },
  APPROVED: { text: '全部通过', class: 'bg-green-100 text-green-700' },
  REJECTED: { text: '全部驳回', class: 'bg-red-100 text-red-700' },
  PARTIAL: { text: '部分处理', class: 'bg-blue-100 text-blue-700' },
}

function formatTime(time?: string) {
  return time ? time.replace('T', ' ').substring(0, 19) : '-'
}

onMounted(async () => {
  await lineStore.fetchLines()
  fetchSprings()
  fetchPlans()
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
    <div class="lg:col-span-2 space-y-6">
      <!-- 弹簧选择 -->
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Cog class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">选择模拟弹簧</h2>
          <span class="ml-auto text-sm text-industrial-500">
            已选择 <span class="font-mono font-bold text-primary-700">{{ selectedIds.length }}</span> 项
          </span>
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
            <button class="btn-industrial text-sm" @click="handleSearch">搜索</button>
            <button class="btn-industrial-outline text-sm" @click="handleReset">重置</button>
          </div>
        </div>

        <div class="max-h-72 overflow-y-auto border border-industrial-200 rounded-industrial">
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
                  spring.sealStatus === 'SEALED' ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer',
                  { 'bg-primary-50': selectedIds.includes(spring.id) },
                ]"
                @click="toggleSelect(spring.id)"
              >
                <td class="py-2" @click.stop>
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(spring.id)"
                    :disabled="spring.sealStatus === 'SEALED'"
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

      <!-- 模拟配置 -->
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <FlaskConical class="w-5 h-5 text-accent-600" />
          <h2 class="text-lg font-bold text-industrial-800">调拨模拟配置</h2>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div class="space-y-3">
            <label class="block text-sm font-medium text-industrial-700">
              <ArrowLeftRight class="w-4 h-4 inline mr-1" />
              拟接收产线 <span class="text-red-500">*</span>
            </label>
            <div class="space-y-2 max-h-44 overflow-y-auto">
              <label
                v-for="line in availableTargetLines"
                :key="line.id"
                class="flex items-center gap-3 p-3 rounded-industrial cursor-pointer transition-all border-2"
                :class="[
                  simForm.toLineId === line.id
                    ? 'border-primary-600 bg-primary-50'
                    : 'border-transparent hover:bg-industrial-50',
                ]"
              >
                <input
                  type="radio"
                  :value="line.id"
                  v-model="simForm.toLineId"
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
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <Users class="w-4 h-4 inline mr-1" />
                调度员 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="simForm.operator"
                type="text"
                class="input-industrial"
                placeholder="请输入调度员姓名"
                maxlength="32"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <FileText class="w-4 h-4 inline mr-1" />
                方案备注
              </label>
              <textarea
                v-model="simForm.remark"
                class="input-industrial h-20 resize-none"
                placeholder="选填，记录本次模拟的调拨目的"
                maxlength="255"
              ></textarea>
            </div>
          </div>

          <div class="flex flex-col justify-center">
            <div class="card-industrial p-4 bg-industrial-50">
              <h3 class="font-semibold text-industrial-800 mb-3">模拟信息确认</h3>
              <div class="space-y-2 text-sm">
                <div class="flex justify-between">
                  <span class="text-industrial-600">选中数量：</span>
                  <span class="font-mono font-medium">{{ selectedIds.length }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">拟接收产线：</span>
                  <span class="font-medium text-accent-600">
                    {{ simForm.toLineId ? lineStore.getLineName(simForm.toLineId) : '未选择' }}
                  </span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">调度员：</span>
                  <span class="font-medium">{{ simForm.operator || '未填写' }}</span>
                </div>
                <div class="pt-2 mt-2 border-t border-industrial-200 space-y-2">
                  <button
                    class="w-full btn-industrial"
                    :disabled="estimating || selectedIds.length === 0 || !simForm.toLineId"
                    @click="handlePreview"
                  >
                    <Play class="w-4 h-4 inline mr-1" />
                    {{ estimating ? '预估中...' : '预估负载' }}
                  </button>
                  <button
                    class="w-full btn-industrial-accent"
                    :disabled="saving || selectedIds.length === 0 || !simForm.toLineId || !simForm.operator"
                    @click="handleSave"
                  >
                    <Save class="w-4 h-4 inline mr-1" />
                    {{ saving ? '保存中...' : '保存模拟方案' }}
                  </button>
                  <p class="text-xs text-industrial-400 text-center">
                    保存方案不改变弹簧归属，采用后才生成划转申请
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 负载预估结果 -->
      <div v-if="estimate" class="card-industrial p-4 animate-fade-in">
        <div class="flex items-center gap-2 mb-4">
          <FlaskConical class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">负载预估结果</h2>
          <span class="text-sm text-industrial-500">
            {{ estimate.springCount }} 件弹簧划入「{{ estimate.toLineName }}」，共影响 {{ estimate.lines.length }} 条产线
          </span>
        </div>
        <SimulationEstimatePanel :estimate="estimate" />
      </div>
    </div>

    <!-- 已保存方案 -->
    <div class="space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <ClipboardList class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">模拟方案</h2>
          <button
            class="ml-auto p-1.5 hover:bg-industrial-100 rounded transition-colors"
            title="刷新"
            @click="fetchPlans"
          >
            <RefreshCw class="w-4 h-4 text-industrial-500" />
          </button>
        </div>

        <div class="mb-3">
          <select
            v-model="planFilter"
            class="input-industrial w-full text-sm"
            @change="planPage = 0; fetchPlans()"
          >
            <option value="">全部状态</option>
            <option value="DRAFT">已保存</option>
            <option value="ADOPTED">已采用</option>
            <option value="DISCARDED">已作废</option>
          </select>
        </div>

        <div v-if="plansLoading" class="text-center py-8 text-industrial-400 animate-pulse text-sm">
          加载中...
        </div>
        <div v-else-if="plans.length === 0" class="text-center py-8 text-industrial-400 text-sm">
          <FlaskConical class="w-10 h-10 mx-auto mb-2 opacity-30" />
          暂无模拟方案
        </div>
        <div v-else class="space-y-3">
          <div
            v-for="plan in plans"
            :key="plan.id"
            class="p-3 bg-industrial-50 rounded-industrial animate-stagger"
            :style="{ animationDelay: `${plans.indexOf(plan) * 50}ms` }"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="font-mono text-xs font-medium text-primary-700">
                {{ plan.simulationNo }}
              </span>
              <span
                class="px-2 py-0.5 rounded text-xs font-medium"
                :class="simStatusMap[plan.status].class"
              >
                {{ simStatusMap[plan.status].text }}
              </span>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <span class="text-industrial-600 text-xs">拟接收</span>
              <span class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded text-xs">
                {{ plan.toLineName }}
              </span>
              <span class="text-xs text-industrial-400">共 {{ plan.itemCount ?? '-' }} 条</span>
            </div>
            <div
              v-if="plan.status === 'ADOPTED' && plan.applicationNo"
              class="mt-2 flex items-center gap-2 text-xs"
            >
              <span class="text-industrial-500">申请单</span>
              <span class="font-mono text-primary-700">{{ plan.applicationNo }}</span>
              <span
                v-if="plan.applicationStatus"
                class="px-1.5 py-0.5 rounded font-medium"
                :class="appStatusMap[plan.applicationStatus as ApplicationStatus]?.class"
              >
                {{ appStatusMap[plan.applicationStatus as ApplicationStatus]?.text }}
              </span>
            </div>
            <div class="mt-2 flex items-center justify-between text-xs text-industrial-500">
              <span>
                <Users class="w-3 h-3 inline mr-1" />
                {{ plan.operator }}
              </span>
              <span class="font-mono">{{ formatTime(plan.createTime) }}</span>
            </div>
            <div class="mt-2 pt-2 border-t border-industrial-200 flex items-center gap-2">
              <button
                class="flex-1 px-2 py-1 text-xs rounded border border-industrial-300 text-industrial-600 hover:bg-white transition-colors"
                @click="handleDetail(plan)"
              >
                <Eye class="w-3 h-3 inline mr-1" />
                详情
              </button>
              <template v-if="plan.status === 'DRAFT'">
                <button
                  class="flex-1 px-2 py-1 text-xs rounded bg-green-600 text-white hover:bg-green-700 transition-colors"
                  @click="handleAdopt(plan)"
                >
                  <CheckCircle2 class="w-3 h-3 inline mr-1" />
                  采用
                </button>
                <button
                  class="flex-1 px-2 py-1 text-xs rounded border border-red-300 text-red-600 hover:bg-red-50 transition-colors"
                  @click="handleDiscard(plan)"
                >
                  <Trash2 class="w-3 h-3 inline mr-1" />
                  作废
                </button>
              </template>
            </div>
          </div>
        </div>

        <div v-if="planTotalPages > 1" class="mt-3 flex items-center justify-between text-xs text-industrial-500">
          <button
            class="px-2 py-1 rounded border border-industrial-300 disabled:opacity-40"
            :disabled="planPage === 0"
            @click="changePlanPage(-1)"
          >
            上一页
          </button>
          <span>{{ planPage + 1 }} / {{ planTotalPages }} 页（共 {{ planTotal }} 条）</span>
          <button
            class="px-2 py-1 rounded border border-industrial-300 disabled:opacity-40"
            :disabled="planPage >= planTotalPages - 1"
            @click="changePlanPage(1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- 方案详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      width="900px"
      title="模拟方案详情"
      class="simulation-modal"
    >
      <div v-if="detailLoading" class="text-center py-12 text-industrial-400 animate-pulse">
        加载中...
      </div>
      <div v-else-if="detail" class="space-y-5">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
          <div>
            <div class="text-xs text-industrial-500">方案编号</div>
            <div class="font-mono font-medium text-primary-800">{{ detail.simulation.simulationNo }}</div>
          </div>
          <div>
            <div class="text-xs text-industrial-500">方案状态</div>
            <span
              class="inline-block mt-0.5 px-2 py-0.5 rounded text-xs font-medium"
              :class="simStatusMap[detail.simulation.status].class"
            >
              {{ simStatusMap[detail.simulation.status].text }}
            </span>
          </div>
          <div>
            <div class="text-xs text-industrial-500">调度员</div>
            <div class="font-medium">{{ detail.simulation.operator }}</div>
          </div>
          <div>
            <div class="text-xs text-industrial-500">创建时间</div>
            <div class="font-mono text-xs">{{ formatTime(detail.simulation.createTime) }}</div>
          </div>
          <div>
            <div class="text-xs text-industrial-500">拟接收产线</div>
            <div class="font-medium text-accent-600">{{ detail.simulation.toLineName }}</div>
          </div>
          <div v-if="detail.simulation.applicationNo">
            <div class="text-xs text-industrial-500">关联划转申请</div>
            <div class="font-mono text-xs text-primary-700">
              {{ detail.simulation.applicationNo }}
              <span
                v-if="detail.simulation.applicationStatus"
                class="ml-1 px-1.5 py-0.5 rounded font-medium"
                :class="appStatusMap[detail.simulation.applicationStatus as ApplicationStatus]?.class"
              >
                {{ appStatusMap[detail.simulation.applicationStatus as ApplicationStatus]?.text }}
              </span>
            </div>
          </div>
          <div v-if="detail.simulation.remark" class="col-span-2">
            <div class="text-xs text-industrial-500">备注</div>
            <div class="text-industrial-700">{{ detail.simulation.remark }}</div>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-semibold text-industrial-800 mb-2">
            模拟弹簧明细（{{ detail.items.length }} 条）
          </h3>
          <div class="max-h-40 overflow-y-auto border border-industrial-200 rounded-industrial">
            <table class="table-industrial text-sm">
              <thead class="sticky top-0">
                <tr>
                  <th class="py-2">弹簧编号</th>
                  <th class="py-2">型号</th>
                  <th class="py-2">弹力系数</th>
                  <th class="py-2">模拟时产线</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.items" :key="item.id">
                  <td class="py-2 font-mono text-xs">{{ item.springCode }}</td>
                  <td class="py-2">{{ item.model }}</td>
                  <td class="py-2 font-mono text-xs">{{ item.elasticCoefficient }} N/mm</td>
                  <td class="py-2">
                    <span class="px-2 py-0.5 bg-primary-100 text-primary-700 rounded text-xs">
                      {{ item.fromLineName }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="detail.estimate">
          <h3 class="text-sm font-semibold text-industrial-800 mb-2">保存时的负载预估快照</h3>
          <SimulationEstimatePanel :estimate="detail.estimate" />
        </div>
      </div>
    </el-dialog>

    <!-- 采用方案弹窗 -->
    <el-dialog
      v-model="adoptVisible"
      width="480px"
      title="采用模拟方案"
      class="simulation-modal"
    >
      <div v-if="adoptTarget" class="space-y-4">
        <div class="p-3 bg-industrial-50 rounded-industrial text-sm space-y-1">
          <div class="flex justify-between">
            <span class="text-industrial-600">方案编号：</span>
            <span class="font-mono font-medium text-primary-800">{{ adoptTarget.simulationNo }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-industrial-600">拟接收产线：</span>
            <span class="font-medium text-accent-600">{{ adoptTarget.toLineName }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-industrial-600">弹簧数量：</span>
            <span class="font-mono">{{ adoptTarget.itemCount ?? '-' }}</span>
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Users class="w-4 h-4 inline mr-1" />
            申请人 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="adoptForm.applicant"
            type="text"
            class="input-industrial"
            placeholder="划转申请申请人"
            maxlength="32"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <FileText class="w-4 h-4 inline mr-1" />
            申请原因
          </label>
          <textarea
            v-model="adoptForm.reason"
            class="input-industrial h-20 resize-none"
            placeholder="选填，默认为「调拨模拟方案经负载预估后采用」"
            maxlength="255"
          ></textarea>
        </div>
        <p class="text-xs text-industrial-400">
          采用后将生成待审批划转申请，审批通过后弹簧归属才会变更
        </p>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-industrial-outline" @click="adoptVisible = false">取消</button>
          <button class="btn-industrial-accent" :disabled="adopting" @click="confirmAdopt">
            {{ adopting ? '提交中...' : '确认采用' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.simulation-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}

.simulation-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}

.simulation-modal :deep(.el-dialog__footer) {
  border-top: 1px solid #e2e8f0;
  padding-top: 16px;
}
</style>
