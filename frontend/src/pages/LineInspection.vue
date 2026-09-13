<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { useInspectionStore } from '@/stores/inspections'
import { inspectionApi } from '@/api'
import type { LineInspection } from '@/types'
import {
  Wind,
  Factory,
  Gauge,
  Wrench,
  UserRound,
  CheckCircle2,
  XCircle,
  RefreshCw,
  History,
  ClipboardCheck,
} from 'lucide-vue-next'

const lineStore = useLineStore()
const inspectionStore = useInspectionStore()

const submitting = ref(false)
const historyLoading = ref(false)
const history = ref<LineInspection[]>([])

const form = reactive({
  lineId: null as number | null,
  airPressure: null as number | null,
  toolingIntact: null as boolean | null,
  inspector: '',
})

/** 选中产线的最近一次点检（卡片实时预览） */
const selectedLatest = computed(() =>
  form.lineId == null ? null : inspectionStore.latestOf(form.lineId)
)

/** 各产线当日点检状态卡片 */
const lineCards = computed(() =>
  lineStore.lines.map((line) => {
    const latest = inspectionStore.latestOf(line.id)
    const today = latest != null && isToday(latest.inspectTime)
    return { line, latest, today }
  })
)

const inspectedTodayCount = computed(
  () => lineCards.value.filter((c) => c.today).length
)

function isToday(time?: string | null) {
  if (!time) return false
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
    now.getDate()
  ).padStart(2, '0')}`
  return time.substring(0, 10) === today
}

function formatTime(time?: string | null) {
  return time ? time.substring(0, 16) : '-'
}

async function fetchHistory() {
  if (form.lineId == null) {
    history.value = []
    return
  }
  historyLoading.value = true
  try {
    const response = await inspectionApi.listByLine(form.lineId)
    history.value = response.data
  } catch {
    // ignore
  } finally {
    historyLoading.value = false
  }
}

function handleLineChange() {
  fetchHistory()
}

/** 三项必填校验：气源压力、工装完好、点检人，缺一项不能提交 */
async function handleSubmit() {
  if (form.lineId == null) {
    ElMessage.warning('请选择点检产线')
    return
  }
  if (form.airPressure == null) {
    ElMessage.warning('请登记气源压力')
    return
  }
  if (form.airPressure < 0 || form.airPressure > 2) {
    ElMessage.warning('气源压力需在 0 ~ 2.0 MPa 之间')
    return
  }
  if (form.toolingIntact == null) {
    ElMessage.warning('请确认工装是否完好')
    return
  }
  if (!form.inspector.trim()) {
    ElMessage.warning('请填写点检人')
    return
  }

  submitting.value = true
  try {
    const response = await inspectionApi.register({
      lineId: form.lineId,
      airPressure: form.airPressure,
      toolingIntact: form.toolingIntact,
      inspector: form.inspector.trim(),
    })
    const saved = response.data
    if (saved.passed) {
      ElMessage.success(`产线「${saved.lineName}」开班点检通过，已登记（${saved.inspectionNo}）`)
    } else {
      ElMessage.warning({
        message: `产线「${saved.lineName}」点检未通过，已登记（${saved.inspectionNo}）；该线今日不能作为调拨模拟接收方，请整改后重新点检`,
        duration: 6000,
        showClose: true,
      })
    }
    // 登记成功后保留产线与点检人，便于连续点检下一条产线
    form.airPressure = null
    form.toolingIntact = null
    await inspectionStore.fetchLatest(true)
    fetchHistory()
  } catch (err) {
    ElMessage.error({
      message: err instanceof Error ? err.message : '点检登记失败',
      duration: 6000,
      showClose: true,
    })
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await lineStore.fetchLines()
  await inspectionStore.fetchLatest(true)
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
    <!-- 点检登记 -->
    <div class="card-industrial p-4 h-fit">
      <div class="flex items-center gap-2 mb-4">
        <Wind class="w-5 h-5 text-primary-600" />
        <h2 class="text-lg font-bold text-industrial-800">开班点检登记</h2>
      </div>
      <p class="text-xs text-industrial-400 mb-4">
        质量员开班前逐线登记，气源压力、工装完好、点检人三项缺一不可提交；
        当日未点检或点检未通过的产线不能作为调拨模拟接收方。
      </p>

      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Factory class="w-4 h-4 inline mr-1" />
            点检产线 <span class="text-red-500">*</span>
          </label>
          <select v-model="form.lineId" class="input-industrial" @change="handleLineChange">
            <option :value="null" disabled>请选择产线</option>
            <option v-for="line in lineStore.lines" :key="line.id" :value="line.id">
              {{ line.lineName }} ({{ line.lineCode }})
            </option>
          </select>
          <!-- 选中产线的最近一次点检提示 -->
          <div
            v-if="selectedLatest"
            class="mt-2 px-2.5 py-1.5 rounded-industrial text-xs"
            :class="
              isToday(selectedLatest.inspectTime)
                ? selectedLatest.passed
                  ? 'bg-green-50 border border-green-200 text-green-700'
                  : 'bg-red-50 border border-red-200 text-red-700'
                : 'bg-industrial-50 border border-industrial-200 text-industrial-500'
            "
          >
            <template v-if="isToday(selectedLatest.inspectTime)">
              今日已点检 {{ formatTime(selectedLatest.inspectTime) }} ·
              {{ selectedLatest.passed ? '通过' : '未通过' }}，再次登记将覆盖判定口径（按最新记录）
            </template>
            <template v-else>
              最近一次点检为 {{ formatTime(selectedLatest.inspectTime) }}（非今日），今日尚未开班点检
            </template>
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Gauge class="w-4 h-4 inline mr-1" />
            气源压力 (MPa) <span class="text-red-500">*</span>
          </label>
          <input
            v-model.number="form.airPressure"
            type="number"
            step="0.01"
            min="0"
            max="2"
            class="input-industrial"
            placeholder="标准区间 0.40 ~ 0.80 MPa"
          />
          <p
            v-if="form.airPressure != null && (form.airPressure < 0.4 || form.airPressure > 0.8)"
            class="mt-1 text-xs text-red-500"
          >
            超出标准区间 0.40 ~ 0.80 MPa，提交后点检将判定为不通过
          </p>
        </div>

        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Wrench class="w-4 h-4 inline mr-1" />
            工装完好 <span class="text-red-500">*</span>
          </label>
          <div class="flex gap-3">
            <label
              class="flex-1 flex items-center justify-center gap-2 p-2.5 rounded-industrial cursor-pointer border-2 transition-all"
              :class="
                form.toolingIntact === true
                  ? 'border-green-600 bg-green-50 text-green-700'
                  : 'border-industrial-200 text-industrial-600 hover:bg-industrial-50'
              "
            >
              <input v-model="form.toolingIntact" type="radio" :value="true" class="w-4 h-4" />
              <CheckCircle2 class="w-4 h-4" />
              完好
            </label>
            <label
              class="flex-1 flex items-center justify-center gap-2 p-2.5 rounded-industrial cursor-pointer border-2 transition-all"
              :class="
                form.toolingIntact === false
                  ? 'border-red-600 bg-red-50 text-red-700'
                  : 'border-industrial-200 text-industrial-600 hover:bg-industrial-50'
              "
            >
              <input v-model="form.toolingIntact" type="radio" :value="false" class="w-4 h-4" />
              <XCircle class="w-4 h-4" />
              不完好
            </label>
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <UserRound class="w-4 h-4 inline mr-1" />
            点检人 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.inspector"
            type="text"
            class="input-industrial"
            placeholder="请输入质量员姓名"
            maxlength="32"
          />
        </div>

        <button
          class="w-full btn-industrial-accent"
          :disabled="submitting"
          @click="handleSubmit"
        >
          <ClipboardCheck class="w-4 h-4 inline mr-1" />
          {{ submitting ? '提交中...' : '提交开班点检' }}
        </button>
      </div>
    </div>

    <!-- 各产线当日点检状态 -->
    <div class="lg:col-span-2 space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Factory class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">各产线当日点检状态</h2>
          <span class="text-sm text-industrial-500">
            今日已点检
            <span class="font-mono font-bold text-primary-700">{{ inspectedTodayCount }}</span>
            / {{ lineStore.lines.length }} 条
          </span>
          <button
            class="ml-auto p-1.5 hover:bg-industrial-100 rounded transition-colors"
            title="刷新"
            @click="inspectionStore.fetchLatest(true)"
          >
            <RefreshCw class="w-4 h-4 text-industrial-500" />
          </button>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div
            v-for="card in lineCards"
            :key="card.line.id"
            class="p-3 rounded-industrial border-2 transition-colors"
            :class="
              card.today
                ? card.latest!.passed
                  ? 'border-green-200 bg-green-50/50'
                  : 'border-red-200 bg-red-50/50'
                : 'border-industrial-200 bg-industrial-50/50'
            "
          >
            <div class="flex items-center gap-2">
              <span class="font-mono text-xs text-industrial-400">{{ card.line.lineCode }}</span>
              <span class="font-medium text-industrial-800 text-sm">{{ card.line.lineName }}</span>
              <span
                class="ml-auto px-2 py-0.5 rounded-full text-xs font-medium"
                :class="
                  card.today
                    ? card.latest!.passed
                      ? 'bg-green-100 text-green-700'
                      : 'bg-red-100 text-red-700'
                    : 'bg-industrial-200 text-industrial-500'
                "
              >
                {{ card.today ? (card.latest!.passed ? '今日已点检 · 通过' : '今日已点检 · 未通过') : '未开班点检' }}
              </span>
            </div>
            <div v-if="card.latest" class="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-industrial-500">
              <span>气源压力：<span class="font-mono text-industrial-700">{{ card.latest.airPressure }} MPa</span></span>
              <span>工装：{{ card.latest.toolingIntact ? '完好' : '不完好' }}</span>
              <span>点检人：{{ card.latest.inspector }}</span>
              <span class="font-mono">{{ formatTime(card.latest.inspectTime) }}</span>
            </div>
            <div v-else class="mt-2 text-xs text-industrial-400">暂无点检记录</div>
          </div>
        </div>
      </div>

      <!-- 点检历史 -->
      <div class="card-industrial overflow-hidden">
        <div class="flex items-center gap-2 px-4 py-3 border-b border-industrial-200">
          <History class="w-4 h-4 text-primary-700" />
          <h3 class="font-semibold text-industrial-800">点检历史</h3>
          <span class="text-xs text-industrial-400">
            {{ form.lineId ? lineStore.getLineName(form.lineId) : '请先选择产线' }}
          </span>
        </div>
        <div class="overflow-x-auto max-h-72 overflow-y-auto">
          <table class="table-industrial text-sm">
            <thead class="sticky top-0">
              <tr>
                <th class="py-2">点检编号</th>
                <th class="py-2">气源压力</th>
                <th class="py-2">工装</th>
                <th class="py-2">点检人</th>
                <th class="py-2">结论</th>
                <th class="py-2">点检时间</th>
              </tr>
            </thead>
            <tbody v-if="!historyLoading && history.length > 0">
              <tr v-for="item in history" :key="item.id">
                <td class="py-2 font-mono text-xs text-primary-800">{{ item.inspectionNo }}</td>
                <td class="py-2 font-mono text-xs">{{ item.airPressure }} MPa</td>
                <td class="py-2">{{ item.toolingIntact ? '完好' : '不完好' }}</td>
                <td class="py-2">{{ item.inspector }}</td>
                <td class="py-2">
                  <span
                    class="px-2 py-0.5 rounded text-xs font-medium"
                    :class="item.passed ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                  >
                    {{ item.passed ? '通过' : '未通过' }}
                  </span>
                </td>
                <td class="py-2 font-mono text-xs text-industrial-500">{{ formatTime(item.inspectTime) }}</td>
              </tr>
            </tbody>
            <tbody v-else-if="!historyLoading">
              <tr>
                <td colspan="6" class="text-center py-10 text-industrial-400">
                  {{ form.lineId ? '该产线暂无点检记录' : '选择产线后展示点检历史' }}
                </td>
              </tr>
            </tbody>
            <tbody v-else>
              <tr>
                <td colspan="6" class="text-center py-10 text-industrial-400">
                  <div class="animate-pulse">加载中...</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
