<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { nightReviewApi } from '@/api'
import { useMeterStore } from '@/stores/meter'
import type { NightLoadReviewDetail, ProductionLine } from '@/types'
import { ClipboardSignature, Factory, UserRound, FileText, X, Zap, AlertTriangle } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  /** 可签发产线（已今夜签发的产线建议在外部排除） */
  lines: ProductionLine[]
  /** 今夜已签发复核单的产线ID集合，禁止重复签发 */
  issuedLineIds: Set<number>
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'issued', detail: NightLoadReviewDetail): void
}>()

const OPERATOR_KEY = 'night-review-operator'

/** 电表抄录缓存：夜班复核提交前该线当天必须已有白班抄录 */
const meterStore = useMeterStore()

const saving = ref(false)
const form = reactive({
  lineId: null as number | null,
  operator: localStorage.getItem(OPERATOR_KEY) ?? '',
  handoverRemark: '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const availableLines = computed(() => props.lines.filter((l) => !props.issuedLineIds.has(l.id)))

/** 选中产线当天的白班电表抄录（无则该线夜班复核不能提交） */
const dayReading = computed(() =>
  form.lineId == null ? null : meterStore.dayShiftOf(form.lineId)
)
/** 已今夜签发复核单的产线外，当天还没有白班抄录的产线（选择器置灰提示） */
const missingDayReading = (lineId: number) => !meterStore.hasDayShiftToday(lineId)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      // 默认选第一条当天已有白班抄录的产线，避免落到不能提交的产线
      form.lineId = availableLines.value.find((l) => meterStore.hasDayShiftToday(l.id))?.id
        ?? availableLines.value[0]?.id
        ?? null
      form.operator = localStorage.getItem(OPERATOR_KEY) ?? ''
      form.handoverRemark = ''
    }
  }
)

async function handleSubmit() {
  if (form.lineId == null) {
    ElMessage.warning('请选择要复核的产线')
    return
  }
  // 夜班复核提交前守卫：该线当天若还没有白班电表抄录不能交（后端同样强校验）
  if (!meterStore.hasDayShiftToday(form.lineId)) {
    const lineName = props.lines.find((l) => l.id === form.lineId)?.lineName ?? ''
    ElMessage.warning(`产线「${lineName}」当天还没有白班电表抄录，夜班复核不能提交，请先补抄白班读数`)
    return
  }
  if (!form.operator.trim()) {
    ElMessage.warning('请输入交班调度员姓名')
    return
  }
  saving.value = true
  try {
    // 快照数字（归属数/压承载/越界条数/待批划转）由后端按看板口径实时计算并固化
    const response = await nightReviewApi.issue({
      lineId: form.lineId,
      operator: form.operator.trim(),
      handoverRemark: form.handoverRemark.trim() || undefined,
    })
    localStorage.setItem(OPERATOR_KEY, form.operator.trim())
    ElMessage.success(`复核单 ${response.data.review.reviewNo} 已签发，等待接班人确认`)
    dialogVisible.value = false
    emit('issued', response.data)
  } catch (err) {
    ElMessage.error({
      message: err instanceof Error ? err.message : '签发失败',
      duration: 6000,
      showClose: true,
    })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="签发夜班承载复核单" width="520px" class="review-issue-modal">
    <div class="space-y-4">
      <div class="p-3 rounded-industrial border border-amber-200 bg-amber-50 text-xs text-amber-700">
        签发时系统自动固化四项承载快照：当前归属弹簧数、是否压到日承载、系数越界条数、次日必须跟进的待批划转。
        每条产线每个夜班只能签发一张；该线当天还没有白班电表抄录时不能提交。
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <Factory class="w-4 h-4 inline mr-1" />
          复核产线 <span class="text-red-500">*</span>
        </label>
        <select v-model="form.lineId" class="input-industrial">
          <option v-for="line in availableLines" :key="line.id" :value="line.id">
            {{ line.lineName }}（{{ line.lineCode }}）{{ missingDayReading(line.id) ? '— 当天缺白班抄录' : '' }}
          </option>
        </select>
        <p v-if="availableLines.length === 0" class="mt-1 text-xs text-red-500">
          今夜各产线均已签发复核单，无需重复签发
        </p>

        <!-- 白班电表抄录守卫：当天缺白班抄录的产线不能提交夜班复核 -->
        <div
          v-if="form.lineId != null && dayReading"
          class="mt-2 px-2.5 py-1.5 rounded-industrial text-xs bg-green-50 border border-green-200 text-green-700"
        >
          <Zap class="w-3.5 h-3.5 inline mr-1" />
          当天白班已抄表：读数 {{ Number(dayReading.readingValue).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }}
          kWh，抄表人 {{ dayReading.reader }}
          <span v-if="dayReading.abnormal" class="font-medium">· 该条白班读数为异常，已在电表抄录页标注</span>
        </div>
        <div
          v-else-if="form.lineId != null"
          class="mt-2 px-2.5 py-1.5 rounded-industrial text-xs bg-red-50 border border-red-200 text-red-700"
        >
          <AlertTriangle class="w-3.5 h-3.5 inline mr-1" />
          该产线当天还没有白班电表抄录，夜班复核不能提交；请先到「电表抄录」补抄当天白班读数
        </div>
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <UserRound class="w-4 h-4 inline mr-1" />
          交班调度员 <span class="text-red-500">*</span>
        </label>
        <input
          v-model="form.operator"
          type="text"
          class="input-industrial"
          placeholder="请输入交班调度员姓名"
          maxlength="32"
        />
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <FileText class="w-4 h-4 inline mr-1" />
          交班备注
        </label>
        <textarea
          v-model="form.handoverRemark"
          rows="3"
          class="input-industrial resize-none"
          placeholder="选填：夜班异常、需接班人重点关注事项等"
          maxlength="512"
        ></textarea>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <button class="btn-industrial-outline" @click="dialogVisible = false">
          <X class="w-4 h-4 inline mr-1" />
          取消
        </button>
        <button
          class="btn-industrial"
          :disabled="saving || availableLines.length === 0 || (form.lineId != null && missingDayReading(form.lineId))"
          @click="handleSubmit"
        >
          <ClipboardSignature class="w-4 h-4 inline mr-1" />
          {{ saving ? '签发中...' : '签发复核单' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.review-issue-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.review-issue-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
