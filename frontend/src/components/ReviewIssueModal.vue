<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { nightReviewApi } from '@/api'
import type { NightLoadReviewDetail, ProductionLine } from '@/types'
import { ClipboardSignature, Factory, UserRound, FileText, X } from 'lucide-vue-next'

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

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.lineId = availableLines.value[0]?.id ?? null
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
        每条产线每个夜班只能签发一张。
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <Factory class="w-4 h-4 inline mr-1" />
          复核产线 <span class="text-red-500">*</span>
        </label>
        <select v-model="form.lineId" class="input-industrial">
          <option v-for="line in availableLines" :key="line.id" :value="line.id">
            {{ line.lineName }}（{{ line.lineCode }}）
          </option>
        </select>
        <p v-if="availableLines.length === 0" class="mt-1 text-xs text-red-500">
          今夜各产线均已签发复核单，无需重复签发
        </p>
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
        <button class="btn-industrial" :disabled="saving || availableLines.length === 0" @click="handleSubmit">
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
