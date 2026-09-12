<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { lineApi } from '@/api'
import type { LineHaltGuard, ProductionLine } from '@/types'
import {
  OctagonPause,
  PlayCircle,
  UserRound,
  FileText,
  CalendarClock,
  ClipboardCheck,
  AlertTriangle,
  X,
} from 'lucide-vue-next'

/** 弹窗入参只需产线公共字段（看板概览项用 lineId，产线实体用 id，二者均可） */
type HaltLineLike = {
  id?: number
  lineId?: number
  lineCode: string
  lineName: string
  haltReason?: string | null
  haltExpectedResumeTime?: string | null
  haltOperator?: string | null
  haltTime?: string | null
}

function lineIdOf(line: HaltLineLike): number {
  return (line.id ?? line.lineId) as number
}

const props = defineProps<{
  visible: boolean
  line: HaltLineLike | null
  /** halt-登记停台；resume-复台并填写结论 */
  mode: 'halt' | 'resume'
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', line: ProductionLine): void
}>()

const OPERATOR_KEY = 'line-halt-operator'

const saving = ref(false)
const guardLoading = ref(false)
const guard = ref<LineHaltGuard | null>(null)
const pendingConfirmed = ref(false)
const form = reactive({
  operator: localStorage.getItem(OPERATOR_KEY) ?? '',
  reason: '',
  expectedResumeTime: '',
  conclusion: '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const isResume = computed(() => props.mode === 'resume')
const title = computed(() => {
  if (!props.line) return '产线停台'
  return isResume.value ? `产线复台 · ${props.line.lineName}` : `登记停台 · ${props.line.lineName}`
})

/** 停台时间选择器最早为当前时刻 */
const nowStr = (() => {
  const d = new Date()
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
})()

async function loadGuard() {
  if (!props.line) return
  guardLoading.value = true
  guard.value = null
  try {
    const response = await lineApi.haltGuard(lineIdOf(props.line))
    guard.value = response.data
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '待审批单查询失败')
  } finally {
    guardLoading.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.operator = localStorage.getItem(OPERATOR_KEY) ?? ''
      form.reason = ''
      form.expectedResumeTime = ''
      form.conclusion = ''
      pendingConfirmed.value = false
      if (!isResume.value) {
        void loadGuard()
      }
    }
  }
)

/** 登记停台前是否必须二次确认：流向该产线的待审批申请不为空时强制 */
const pendingNeedConfirm = computed(() => (guard.value?.pendingItemCount ?? 0) > 0)

async function handleSubmit() {
  if (!props.line) return
  if (!form.operator.trim()) {
    ElMessage.warning('请输入调度员姓名')
    return
  }
  if (isResume.value) {
    if (!form.conclusion.trim()) {
      ElMessage.warning('复台必须填写结论')
      return
    }
  } else {
    if (!form.reason.trim()) {
      ElMessage.warning('请填写停台原因')
      return
    }
    if (!form.expectedResumeTime) {
      ElMessage.warning('请选择预计复台时间')
      return
    }
    if (pendingNeedConfirm.value && !pendingConfirmed.value) {
      ElMessage.warning(`该产线存在 ${guard.value?.pendingApplicationCount} 张待审批单，请勾选确认后再登记停台`)
      return
    }
  }

  saving.value = true
  try {
    const targetId = lineIdOf(props.line)
    const response = isResume.value
      ? await lineApi.resume(targetId, {
          operator: form.operator.trim(),
          conclusion: form.conclusion.trim(),
        })
      : await lineApi.halt(targetId, {
          operator: form.operator.trim(),
          reason: form.reason.trim(),
          // datetime-local（yyyy-MM-ddTHH:mm）转为后端要求的 yyyy-MM-dd HH:mm:ss
          expectedResumeTime: form.expectedResumeTime.replace('T', ' ') + ':00',
        })
    localStorage.setItem(OPERATOR_KEY, form.operator.trim())
    ElMessage.success(
      isResume.value
        ? '产线已复台，恢复可作为划转接收方'
        : '产线已停台，停台期间不能作为划转接收方'
    )
    dialogVisible.value = false
    emit('saved', response.data)
  } catch (err) {
    ElMessage.error({
      message: err instanceof Error ? err.message : '操作失败',
      duration: 6000,
      showClose: true,
    })
  } finally {
    saving.value = false
  }
}

function formatTime(time?: string | null) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 16)
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="520px" class="line-halt-modal">
    <div v-if="line" class="space-y-4">
      <!-- 产线摘要 -->
      <div class="p-3 bg-industrial-50 rounded-industrial text-sm space-y-1">
        <div class="flex justify-between">
          <span class="text-industrial-600">产线编码：</span>
          <span class="font-mono font-medium text-primary-800">{{ line.lineCode }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">产线名称：</span>
          <span class="font-medium">{{ line.lineName }}</span>
        </div>
      </div>

      <!-- 复台时展示停台登记信息 -->
      <div
        v-if="isResume"
        class="p-3 rounded-industrial border border-red-200 bg-red-50 text-sm space-y-1"
      >
        <div class="flex items-center gap-2 text-red-700 font-medium mb-1">
          <OctagonPause class="w-4 h-4" />
          停台登记信息
        </div>
        <div class="text-industrial-700">
          停台原因：<span class="font-medium">{{ line.haltReason || '未登记' }}</span>
        </div>
        <div class="text-industrial-600 text-xs">
          调度员 {{ line.haltOperator || '-' }} 登记于 {{ formatTime(line.haltTime) }}
          <span v-if="line.haltExpectedResumeTime">，预计复台时间 {{ formatTime(line.haltExpectedResumeTime) }}</span>
        </div>
      </div>

      <!-- 登记停台前的待审批单影响提示 -->
      <div
        v-else-if="guardLoading"
        class="p-3 rounded-industrial border border-industrial-200 bg-industrial-50 text-xs text-industrial-500"
      >
        正在查询流向该产线的待审批单...
      </div>
      <div
        v-else-if="guard && pendingNeedConfirm"
        class="p-3 rounded-industrial border border-accent-300 bg-accent-50 text-sm space-y-2"
      >
        <div class="flex items-start gap-2 text-accent-700">
          <AlertTriangle class="w-4 h-4 mt-0.5 flex-shrink-0" />
          <div>
            该产线当前存在
            <span class="font-mono font-bold">{{ guard.pendingApplicationCount }}</span>
            张待审批划转申请（共
            <span class="font-mono font-bold">{{ guard.pendingItemCount }}</span>
            条明细）。停台后这些申请在审批通过时将被拦截，需待复台后方可继续划转。
          </div>
        </div>
        <label class="flex items-center gap-2 text-xs text-industrial-700 cursor-pointer">
          <input v-model="pendingConfirmed" type="checkbox" class="w-4 h-4" />
          我已知晓上述待审批单影响，仍要登记停台
        </label>
      </div>
      <div
        v-else
        class="p-3 rounded-industrial border border-industrial-200 bg-industrial-50 text-xs text-industrial-500"
      >
        当前无流向该产线的待审批申请，可直接登记停台。
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <UserRound class="w-4 h-4 inline mr-1" />
          调度员 <span class="text-red-500">*</span>
        </label>
        <input
          v-model="form.operator"
          type="text"
          class="input-industrial"
          placeholder="请输入调度员姓名"
          maxlength="32"
        />
      </div>

      <template v-if="!isResume">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <FileText class="w-4 h-4 inline mr-1" />
            停台原因 <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="form.reason"
            rows="3"
            class="input-industrial resize-none"
            placeholder="例如：设备检修、缺料待料、工艺调整等"
            maxlength="255"
          ></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <CalendarClock class="w-4 h-4 inline mr-1" />
            预计复台时间 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.expectedResumeTime"
            type="datetime-local"
            class="input-industrial"
            :min="nowStr"
          />
        </div>
        <p class="text-xs text-industrial-400">
          停台期间该产线不能作为划转接收方，新申请提交、模拟预估与审批通过均会被拦截
        </p>
      </template>

      <template v-else>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <ClipboardCheck class="w-4 h-4 inline mr-1" />
            复台结论 <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="form.conclusion"
            rows="3"
            class="input-industrial resize-none"
            placeholder="例如：设备检修完成并试产合格，恢复正常生产；或缺料已补齐，复台"
            maxlength="255"
          ></textarea>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <button class="btn-industrial-outline" @click="dialogVisible = false">
          <X class="w-4 h-4 inline mr-1" />
          取消
        </button>
        <button
          v-if="!isResume"
          class="px-4 py-2 rounded-industrial bg-red-600 text-white text-sm font-medium hover:bg-red-700 transition-colors disabled:opacity-50"
          :disabled="saving"
          @click="handleSubmit"
        >
          <OctagonPause class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '确认停台' }}
        </button>
        <button
          v-else
          class="btn-industrial"
          :disabled="saving"
          @click="handleSubmit"
        >
          <PlayCircle class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '确认复台' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.line-halt-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.line-halt-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
