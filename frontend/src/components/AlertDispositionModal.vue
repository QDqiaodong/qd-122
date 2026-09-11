<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { LoadAlertEvent } from '@/types'
import { loadAlertApi } from '@/api'
import { Siren, X, UserRound, ClipboardList, MessageSquareText } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  event: LoadAlertEvent | null
  /** confirm-确认责任人与处置计划；resolve-标记处理完成 */
  mode: 'confirm' | 'resolve'
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', event: LoadAlertEvent): void
}>()

const OPERATOR_KEY = 'load-alert-operator'

const saving = ref(false)
const form = reactive({
  operator: localStorage.getItem(OPERATOR_KEY) ?? '',
  responsiblePerson: '',
  handlePlan: '',
  remark: '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const isResolve = computed(() => props.mode === 'resolve')
const title = computed(() => {
  if (!props.event) return '告警处置'
  const line = `${props.event.lineName}（${props.event.lineCode}）`
  if (isResolve.value) return `标记处理完成 · ${line}`
  return props.event.status === 'PROCESSING'
    ? `更新处置计划 · ${line}`
    : `确认处置 · ${line}`
})

// 打开时用事件当前处置信息回填
watch(
  () => props.visible,
  (v) => {
    if (v && props.event) {
      form.operator = localStorage.getItem(OPERATOR_KEY) ?? ''
      form.responsiblePerson = props.event.responsiblePerson ?? ''
      form.handlePlan = props.event.handlePlan ?? ''
      form.remark = ''
    }
  }
)

async function handleSubmit() {
  if (!props.event) return
  if (!form.operator.trim()) {
    ElMessage.warning('请输入调度员姓名')
    return
  }
  if (isResolve.value) {
    if (!form.remark.trim()) {
      ElMessage.warning('请填写处理说明')
      return
    }
  } else {
    if (!form.responsiblePerson.trim()) {
      ElMessage.warning('请确认责任人')
      return
    }
    if (!form.handlePlan.trim()) {
      ElMessage.warning('请填写处置计划')
      return
    }
  }

  saving.value = true
  try {
    const payload = {
      action: isResolve.value ? ('RESOLVE' as const) : ('CONFIRM' as const),
      operator: form.operator.trim(),
      responsiblePerson: form.responsiblePerson.trim() || undefined,
      handlePlan: form.handlePlan.trim() || undefined,
      remark: form.remark.trim() || undefined,
    }
    const response = isResolve.value
      ? await loadAlertApi.resolve(props.event.id, payload)
      : await loadAlertApi.confirm(props.event.id, payload)
    localStorage.setItem(OPERATOR_KEY, form.operator.trim())
    ElMessage.success(isResolve.value ? '告警事件已关闭' : '处置信息已确认')
    dialogVisible.value = false
    emit('saved', response.data)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function statusText(status: LoadAlertEvent['status']) {
  return status === 'PENDING' ? '待处理' : status === 'PROCESSING' ? '处置中' : '已关闭'
}

function levelText(level: LoadAlertEvent['alertLevel']) {
  return level === 'OVERLOAD' ? '超载' : '预警'
}

function formatTime(time?: string | null) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="560px" class="alert-modal">
    <div v-if="event" class="space-y-4">
      <!-- 告警快照摘要 -->
      <div
        class="p-3 rounded-industrial border"
        :class="event.alertLevel === 'OVERLOAD'
          ? 'bg-red-50 border-red-200'
          : 'bg-accent-50 border-accent-200'"
      >
        <div class="flex items-center gap-2">
          <Siren
            class="w-4 h-4"
            :class="event.alertLevel === 'OVERLOAD' ? 'text-red-500' : 'text-accent-500'"
          />
          <span
            class="px-2 py-0.5 rounded-full text-xs font-medium"
            :class="event.alertLevel === 'OVERLOAD'
              ? 'bg-red-100 text-red-700'
              : 'bg-accent-100 text-accent-700'"
          >
            {{ levelText(event.alertLevel) }}
          </span>
          <span class="font-mono text-xs text-industrial-400">{{ event.eventNo }}</span>
          <span class="ml-auto text-xs text-industrial-400 font-mono">
            触发 {{ formatTime(event.triggerTime) }}
          </span>
        </div>
        <ul class="mt-2 space-y-1">
          <li
            v-for="(reason, idx) in (event.snapshot?.reasons ?? [])"
            :key="idx"
            class="text-xs text-industrial-600 flex items-start gap-1.5"
          >
            <span class="font-mono text-industrial-400 mt-0.5">{{ idx + 1 }}.</span>
            <span>{{ reason }}</span>
          </li>
        </ul>
        <div class="mt-2 text-xs text-industrial-500 flex items-center gap-3">
          <span>
            归属 <span class="font-mono font-semibold">{{ event.snapshot?.springCount }}</span>
            / 阈值 <span class="font-mono">{{ event.snapshot?.dailyCapacityThreshold ?? '未配置' }}</span>
          </span>
          <span v-if="event.snapshot?.loadRate != null">
            负载率 <span class="font-mono font-semibold">{{ event.snapshot.loadRate }}%</span>
          </span>
          <span v-if="(event.snapshot?.outOfRangeCount ?? 0) > 0">
            系数越界 <span class="font-mono font-semibold">{{ event.snapshot?.outOfRangeCount }}</span> 件
          </span>
          <span class="ml-auto">当前状态：{{ statusText(event.status) }}</span>
        </div>
      </div>

      <!-- 调度员 -->
      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          调度员 <span class="text-red-500">*</span>
        </label>
        <input v-model="form.operator" class="input-industrial" placeholder="请输入调度员姓名" />
      </div>

      <!-- 确认处置：责任人 + 处置计划 -->
      <template v-if="!isResolve">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <UserRound class="w-4 h-4 inline mr-1" />
            责任人 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.responsiblePerson"
            class="input-industrial"
            placeholder="确认本次告警的处置责任人"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <ClipboardList class="w-4 h-4 inline mr-1" />
            处置计划 <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="form.handlePlan"
            rows="3"
            class="input-industrial"
            placeholder="例如：今晚前协调划出 2 件至四号线，并复核弹力系数区间"
          ></textarea>
        </div>
      </template>

      <!-- 关闭：处理说明 -->
      <div v-else>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <MessageSquareText class="w-4 h-4 inline mr-1" />
          处理说明 <span class="text-red-500">*</span>
        </label>
        <textarea
          v-model="form.remark"
          rows="3"
          class="input-industrial"
          placeholder="说明已采取的处置措施与结果；若产线仍预警/超载，同一轮异常期间不再重复告警，恢复后再次触发会生成新事件"
        ></textarea>
      </div>

      <!-- 已有处置信息 -->
      <div v-if="event.responsiblePerson || event.handlePlan" class="text-xs text-industrial-500 space-y-1 bg-industrial-50 rounded-industrial p-3">
        <div v-if="event.responsiblePerson">
          已确认责任人：<span class="font-medium text-industrial-700">{{ event.responsiblePerson }}</span>
          <span class="text-industrial-400">（{{ formatTime(event.confirmTime) }}）</span>
        </div>
        <div v-if="event.handlePlan">
          处置计划：<span class="text-industrial-700">{{ event.handlePlan }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <button class="btn-industrial-outline" @click="dialogVisible = false">
          <X class="w-4 h-4 inline mr-1" />
          取消
        </button>
        <button class="btn-industrial" :disabled="saving" @click="handleSubmit">
          {{ saving ? '提交中...' : isResolve ? '确认关闭' : '确认处置' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.alert-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.alert-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
