<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { useMeterStore } from '@/stores/meter'
import { meterApi } from '@/api'
import type { MeterReading, MeterShift } from '@/types'
import {
  Zap,
  Factory,
  UserRound,
  Sun,
  MoonStar,
  AlertTriangle,
  RefreshCw,
  History,
  ClipboardList,
} from 'lucide-vue-next'

/** 约定跳变幅度 kWh：与上一条读数之差绝对值超过该值即标异常（与后端常量保持一致） */
const JUMP_THRESHOLD = 1000

const lineStore = useLineStore()
const meterStore = useMeterStore()

const submitting = ref(false)
const historyLoading = ref(false)
const history = ref<MeterReading[]>([])

const form = reactive({
  lineId: null as number | null,
  shift: 'DAY' as MeterShift,
  readingValue: null as number | null,
  reader: '',
})

/** 选中产线的最近一条抄录（卡片实时预览 + 跳变预判） */
const selectedLatest = computed(() =>
  form.lineId == null ? null : meterStore.latestOf(form.lineId)
)

/** 输入读数与上一条的差值（绝对值），用于提交前的异常预判提示 */
const previewDelta = computed(() => {
  if (form.readingValue == null || !selectedLatest.value) return null
  const prev = Number(selectedLatest.value.readingValue)
  return {
    delta: form.readingValue - prev,
    abnormal: Math.abs(form.readingValue - prev) > JUMP_THRESHOLD,
  }
})

/** 选中产线当天同班次是否已抄（给重复提交前置提示，后端仍强校验） */
const sameShiftAlreadyRead = computed(() => {
  const latest = selectedLatest.value
  if (!latest) return false
  return isToday(latest.readTime) && latest.shift === form.shift
})

/** 各产线最近一次读数卡片 */
const lineCards = computed(() =>
  lineStore.lines.map((line) => {
    const latest = meterStore.latestOf(line.id)
    return { line, latest }
  })
)

const abnormalLineCount = computed(
  () => lineCards.value.filter((c) => c.latest?.abnormal).length
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

function formatValue(value?: number | null) {
  if (value == null) return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchHistory() {
  if (form.lineId == null) {
    history.value = []
    return
  }
  historyLoading.value = true
  try {
    const response = await meterApi.listByLine(form.lineId)
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

/** 四项必填校验：产线、班次、读数、抄表人，缺一项不能提交 */
async function handleSubmit() {
  if (form.lineId == null) {
    ElMessage.warning('请选择抄表产线')
    return
  }
  if (form.readingValue == null) {
    ElMessage.warning('请填写电表读数')
    return
  }
  if (form.readingValue < 0) {
    ElMessage.warning('电表读数不能为负数')
    return
  }
  if (!form.reader.trim()) {
    ElMessage.warning('请填写抄表人')
    return
  }

  submitting.value = true
  try {
    const response = await meterApi.submit({
      lineId: form.lineId,
      shift: form.shift,
      readingValue: form.readingValue,
      reader: form.reader.trim(),
    })
    const saved = response.data
    if (saved.abnormal) {
      ElMessage.warning({
        message: `已登记（${saved.readingNo}），但该条读数异常：${saved.abnormalReason}`,
        duration: 8000,
        showClose: true,
      })
    } else {
      ElMessage.success(`产线「${saved.lineName}」${saved.shift === 'DAY' ? '白班' : '夜班'}电表抄录已登记（${saved.readingNo}）`)
    }
    // 登记成功后保留产线与抄表人，便于连续抄下一条产线/下一班次
    form.readingValue = null
    await meterStore.fetchLatest(true)
    await meterStore.fetchDayShiftToday()
    fetchHistory()
  } catch (err) {
    ElMessage.error({
      message: err instanceof Error ? err.message : '电表抄录提交失败',
      duration: 6000,
      showClose: true,
    })
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await lineStore.fetchLines()
  await meterStore.fetchLatest(true)
  await meterStore.fetchDayShiftToday()
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
    <!-- 电表抄录 -->
    <div class="card-industrial p-4 h-fit">
      <div class="flex items-center gap-2 mb-4">
        <Zap class="w-5 h-5 text-primary-600" />
        <h2 class="text-lg font-bold text-industrial-800">电表抄录</h2>
      </div>
      <p class="text-xs text-industrial-400 mb-4">
        每条产线每个班次（白班/夜班）留下一条电表读数，记下班次、读数、抄表人与抄表时间；
        与上一条相比读数跳变超过约定幅度（{{ JUMP_THRESHOLD.toLocaleString() }} kWh）的记录自动标为异常。
      </p>

      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Factory class="w-4 h-4 inline mr-1" />
            抄表产线 <span class="text-red-500">*</span>
          </label>
          <select v-model="form.lineId" class="input-industrial" @change="handleLineChange">
            <option :value="null" disabled>请选择产线</option>
            <option v-for="line in lineStore.lines" :key="line.id" :value="line.id">
              {{ line.lineName }} ({{ line.lineCode }})
            </option>
          </select>
          <!-- 选中产线的最近一次读数 -->
          <div
            v-if="selectedLatest"
            class="mt-2 px-2.5 py-1.5 rounded-industrial text-xs"
            :class="
              selectedLatest.abnormal
                ? 'bg-red-50 border border-red-200 text-red-700'
                : 'bg-industrial-50 border border-industrial-200 text-industrial-500'
            "
          >
            上一条读数
            <span class="font-mono font-bold">{{ formatValue(selectedLatest.readingValue) }}</span> kWh
            （{{ selectedLatest.shift === 'DAY' ? '白班' : '夜班' }}，{{ formatTime(selectedLatest.readTime) }}）
            <span v-if="selectedLatest.abnormal" class="font-medium">· 上一条为异常读数</span>
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            班次 <span class="text-red-500">*</span>
          </label>
          <div class="flex gap-3">
            <label
              class="flex-1 flex items-center justify-center gap-2 p-2.5 rounded-industrial cursor-pointer border-2 transition-all"
              :class="
                form.shift === 'DAY'
                  ? 'border-amber-500 bg-amber-50 text-amber-700'
                  : 'border-industrial-200 text-industrial-600 hover:bg-industrial-50'
              "
            >
              <input v-model="form.shift" type="radio" value="DAY" class="w-4 h-4" />
              <Sun class="w-4 h-4" />
              白班
            </label>
            <label
              class="flex-1 flex items-center justify-center gap-2 p-2.5 rounded-industrial cursor-pointer border-2 transition-all"
              :class="
                form.shift === 'NIGHT'
                  ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                  : 'border-industrial-200 text-industrial-600 hover:bg-industrial-50'
              "
            >
              <input v-model="form.shift" type="radio" value="NIGHT" class="w-4 h-4" />
              <MoonStar class="w-4 h-4" />
              夜班
            </label>
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Zap class="w-4 h-4 inline mr-1" />
            电表读数 (kWh) <span class="text-red-500">*</span>
          </label>
          <input
            v-model.number="form.readingValue"
            type="number"
            step="0.01"
            min="0"
            class="input-industrial"
            placeholder="按电表累计读数填写"
          />
          <!-- 跳变预判：与上一条读数比对，超幅度提前提示（最终以后端判定为准） -->
          <div v-if="previewDelta" class="mt-1 text-xs" :class="previewDelta.abnormal ? 'text-red-500' : 'text-industrial-400'">
            <template v-if="previewDelta.abnormal">
              <AlertTriangle class="w-3.5 h-3.5 inline mr-0.5" />
              较上一条{{ previewDelta.delta < 0 ? '回退' : '增加' }}
              {{ formatValue(Math.abs(previewDelta.delta)) }} kWh，超过约定幅度
              {{ JUMP_THRESHOLD.toLocaleString() }} kWh，提交后将标为异常
            </template>
            <template v-else>
              较上一条{{ previewDelta.delta < 0 ? '回退' : '增加' }}
              {{ formatValue(Math.abs(previewDelta.delta)) }} kWh，在约定幅度内
            </template>
          </div>
          <div v-else-if="form.readingValue != null && form.lineId != null" class="mt-1 text-xs text-industrial-400">
            该产线首条抄录，无上一条可比对，不判定异常
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <UserRound class="w-4 h-4 inline mr-1" />
            抄表人 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.reader"
            type="text"
            class="input-industrial"
            placeholder="请输入抄表人姓名"
            maxlength="32"
          />
        </div>

        <div v-if="sameShiftAlreadyRead" class="px-2.5 py-1.5 rounded-industrial text-xs bg-red-50 border border-red-200 text-red-600">
          该产线今天{{ form.shift === 'DAY' ? '白班' : '夜班' }}已抄录，每条产线每个班次只能留一条，不能重复提交
        </div>

        <button
          class="w-full btn-industrial-accent"
          :disabled="submitting"
          @click="handleSubmit"
        >
          <ClipboardList class="w-4 h-4 inline mr-1" />
          {{ submitting ? '提交中...' : '提交电表抄录' }}
        </button>
      </div>
    </div>

    <!-- 各产线最近一次读数 -->
    <div class="lg:col-span-2 space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Factory class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">各产线最近一次电表读数</h2>
          <span class="text-sm text-industrial-500">
            最新读数异常
            <span class="font-mono font-bold" :class="abnormalLineCount > 0 ? 'text-red-600' : 'text-primary-700'">
              {{ abnormalLineCount }}
            </span>
            / {{ lineStore.lines.length }} 条线
          </span>
          <button
            class="ml-auto p-1.5 hover:bg-industrial-100 rounded transition-colors"
            title="刷新"
            @click="meterStore.fetchLatest(true)"
          >
            <RefreshCw class="w-4 h-4 text-industrial-500" />
          </button>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div
            v-for="card in lineCards"
            :key="card.line.id"
            class="p-3 rounded-industrial border-2 transition-colors cursor-pointer"
            :class="
              card.latest
                ? card.latest.abnormal
                  ? 'border-red-300 bg-red-50/60'
                  : 'border-green-200 bg-green-50/40'
                : 'border-industrial-200 bg-industrial-50/50'
            "
            @click="form.lineId = card.line.id; fetchHistory()"
          >
            <div class="flex items-center gap-2">
              <span class="font-mono text-xs text-industrial-400">{{ card.line.lineCode }}</span>
              <span class="font-medium text-industrial-800 text-sm">{{ card.line.lineName }}</span>
              <span
                v-if="card.latest"
                class="ml-auto px-2 py-0.5 rounded-full text-xs font-medium"
                :class="
                  card.latest.abnormal
                    ? 'bg-red-100 text-red-700'
                    : 'bg-green-100 text-green-700'
                "
              >
                <span v-if="card.latest.abnormal"><AlertTriangle class="w-3 h-3 inline mr-0.5" />读数异常</span>
                <span v-else>读数正常</span>
              </span>
            </div>
            <div v-if="card.latest" class="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-industrial-500">
              <span class="col-span-2">
                最近读数：
                <span
                  class="font-mono text-base font-bold"
                  :class="card.latest.abnormal ? 'text-red-600' : 'text-industrial-800'"
                >
                  {{ formatValue(card.latest.readingValue) }}
                </span>
                kWh
              </span>
              <span>班次：{{ card.latest.shift === 'DAY' ? '白班' : '夜班' }}</span>
              <span>抄表人：{{ card.latest.reader }}</span>
              <span class="col-span-2 font-mono">{{ formatTime(card.latest.readTime) }}</span>
            </div>
            <div v-else class="mt-2 text-xs text-industrial-400">暂无抄表记录</div>
          </div>
        </div>
      </div>

      <!-- 抄表历史 -->
      <div class="card-industrial overflow-hidden">
        <div class="flex items-center gap-2 px-4 py-3 border-b border-industrial-200">
          <History class="w-4 h-4 text-primary-700" />
          <h3 class="font-semibold text-industrial-800">抄表历史</h3>
          <span class="text-xs text-industrial-400">
            {{ form.lineId ? lineStore.getLineName(form.lineId) : '请先选择产线' }}
          </span>
        </div>
        <div class="overflow-x-auto max-h-72 overflow-y-auto">
          <table class="table-industrial text-sm">
            <thead class="sticky top-0">
              <tr>
                <th class="py-2">抄表编号</th>
                <th class="py-2">日期</th>
                <th class="py-2">班次</th>
                <th class="py-2">读数 (kWh)</th>
                <th class="py-2">跳变量</th>
                <th class="py-2">抄表人</th>
                <th class="py-2">结论</th>
                <th class="py-2">抄表时间</th>
              </tr>
            </thead>
            <tbody v-if="!historyLoading && history.length > 0">
              <tr v-for="item in history" :key="item.id">
                <td class="py-2 font-mono text-xs text-primary-800">{{ item.readingNo }}</td>
                <td class="py-2 font-mono text-xs">{{ item.readingDate }}</td>
                <td class="py-2">{{ item.shift === 'DAY' ? '白班' : '夜班' }}</td>
                <td class="py-2 font-mono text-xs">{{ formatValue(item.readingValue) }}</td>
                <td class="py-2 font-mono text-xs" :class="item.abnormal ? 'text-red-600 font-bold' : 'text-industrial-500'">
                  <template v-if="item.jumpDelta != null">
                    {{ item.jumpDelta < 0 ? '-' : '+' }}{{ formatValue(Math.abs(Number(item.jumpDelta))) }}
                  </template>
                  <template v-else>-</template>
                </td>
                <td class="py-2">{{ item.reader }}</td>
                <td class="py-2">
                  <span
                    v-if="item.abnormal"
                    class="px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700 cursor-help"
                    :title="item.abnormalReason || ''"
                  >
                    <AlertTriangle class="w-3 h-3 inline mr-0.5" />异常
                  </span>
                  <span v-else class="px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700">
                    正常
                  </span>
                </td>
                <td class="py-2 font-mono text-xs text-industrial-500">{{ formatTime(item.readTime) }}</td>
              </tr>
            </tbody>
            <tbody v-else-if="!historyLoading">
              <tr>
                <td colspan="8" class="text-center py-10 text-industrial-400">
                  {{ form.lineId ? '该产线暂无抄表记录' : '选择产线后展示抄表历史' }}
                </td>
              </tr>
            </tbody>
            <tbody v-else>
              <tr>
                <td colspan="8" class="text-center py-10 text-industrial-400">
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
