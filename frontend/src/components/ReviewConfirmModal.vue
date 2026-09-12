<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { nightReviewApi } from '@/api'
import type { NightLoadReviewDetail } from '@/types'
import { ClipboardCheck, UserRound, FileText, X, AlertTriangle, Gauge, Cog, ListChecks } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  /** 待确认复核单详情 */
  detail: NightLoadReviewDetail | null
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirmed', detail: NightLoadReviewDetail): void
}>()

const CONFIRMER_KEY = 'night-review-confirmer'

const saving = ref(false)
const form = reactive({
  confirmer: localStorage.getItem(CONFIRMER_KEY) ?? '',
  followUpNote: '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.confirmer = localStorage.getItem(CONFIRMER_KEY) ?? ''
      form.followUpNote = ''
    }
  }
)

function rateText(detail: NightLoadReviewDetail) {
  const r = detail.review
  if (r.loadRate == null) return '阈值未配置'
  return `${Number(r.loadRate).toFixed(2)}%`
}

async function handleConfirm() {
  if (!form.confirmer.trim()) {
    ElMessage.warning('请输入接班调度员姓名')
    return
  }
  // 硬规则：不写跟进说明不能确认
  if (!form.followUpNote.trim()) {
    ElMessage.warning('不写跟进说明不能确认')
    return
  }
  if (!props.detail) return
  saving.value = true
  try {
    const response = await nightReviewApi.confirm(props.detail.review.id, {
      confirmer: form.confirmer.trim(),
      followUpNote: form.followUpNote.trim(),
    })
    localStorage.setItem(CONFIRMER_KEY, form.confirmer.trim())
    ElMessage.success('复核单已确认，数字已锁定')
    dialogVisible.value = false
    emit('confirmed', response.data)
  } catch (err) {
    ElMessage.error({
      message: err instanceof Error ? err.message : '确认失败',
      duration: 6000,
      showClose: true,
    })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="确认夜班承载复核单" width="560px" class="review-confirm-modal">
    <div v-if="detail" class="space-y-4">
      <!-- 复核单承载快照（只读） -->
      <div class="p-3 bg-industrial-50 rounded-industrial text-sm space-y-2">
        <div class="flex items-center justify-between">
          <span class="font-medium text-primary-800">{{ detail.review.lineName }}</span>
          <span class="font-mono text-xs text-industrial-500">{{ detail.review.reviewNo }}</span>
        </div>
        <div class="grid grid-cols-2 gap-2 text-xs">
          <div class="flex items-center gap-1.5">
            <Cog class="w-3.5 h-3.5 text-industrial-400" />
            当前归属：<span class="font-mono font-bold">{{ detail.review.springCount }}</span> 件
          </div>
          <div class="flex items-center gap-1.5">
            <Gauge class="w-3.5 h-3.5 text-industrial-400" />
            承载率：<span class="font-mono font-bold">{{ rateText(detail) }}</span>
          </div>
        </div>
        <div class="flex flex-wrap gap-2 text-xs">
          <span
            class="px-2 py-0.5 rounded font-medium"
            :class="detail.review.overCapacity
              ? 'bg-red-100 text-red-700'
              : detail.review.capacityReached
                ? 'bg-amber-100 text-amber-700'
                : 'bg-green-100 text-green-700'"
          >
            {{ detail.review.overCapacity
              ? '已超日承载'
              : detail.review.capacityReached
                ? '已压到日承载（满载）'
                : '未压到日承载' }}
          </span>
          <span
            class="px-2 py-0.5 rounded font-medium"
            :class="detail.review.outOfRangeCount > 0 ? 'bg-amber-100 text-amber-700' : 'bg-green-100 text-green-700'"
          >
            系数越界 {{ detail.review.outOfRangeCount }} 条
          </span>
          <span
            class="px-2 py-0.5 rounded font-medium"
            :class="detail.review.pendingTransferCount > 0 ? 'bg-accent-100 text-accent-700' : 'bg-green-100 text-green-700'"
          >
            <ListChecks class="w-3 h-3 inline mr-0.5" />
            待批划转 {{ detail.review.pendingTransferCount }} 条 / {{ detail.review.pendingApplicationCount }} 单
          </span>
        </div>
      </div>

      <div
        v-if="detail.pendingTransfers.length > 0"
        class="max-h-40 overflow-y-auto rounded-industrial border border-industrial-200"
      >
        <table class="table-industrial text-xs">
          <thead class="sticky top-0">
            <tr>
              <th class="py-1.5">申请单号</th>
              <th class="py-1.5">弹簧编号</th>
              <th class="py-1.5">申请人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in detail.pendingTransfers" :key="idx">
              <td class="py-1.5 font-mono">
                {{ item.applicationNo }}
                <span v-if="item.urgent" class="ml-1 px-1 bg-red-100 text-red-700 rounded text-[10px]">急</span>
              </td>
              <td class="py-1.5 font-mono">{{ item.springCode }}</td>
              <td class="py-1.5">{{ item.applicant }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="p-3 rounded-industrial border border-amber-200 bg-amber-50 flex items-start gap-2 text-xs text-amber-700">
        <AlertTriangle class="w-4 h-4 mt-0.5 flex-shrink-0" />
        <span>确认后复核单数字将锁定，不能再修改；全部待确认复核单确认后才能提交新划转。</span>
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <UserRound class="w-4 h-4 inline mr-1" />
          接班调度员 <span class="text-red-500">*</span>
        </label>
        <input
          v-model="form.confirmer"
          type="text"
          class="input-industrial"
          placeholder="请输入接班调度员姓名"
          maxlength="32"
        />
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <FileText class="w-4 h-4 inline mr-1" />
          跟进说明 <span class="text-red-500">*</span>
        </label>
        <textarea
          v-model="form.followUpNote"
          rows="3"
          class="input-industrial resize-none"
          placeholder="必填：写明对超载/越界/待批划转的次日跟进安排，不写跟进说明不能确认"
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
        <button class="btn-industrial-accent" :disabled="saving" @click="handleConfirm">
          <ClipboardCheck class="w-4 h-4 inline mr-1" />
          {{ saving ? '确认中...' : '确认复核单' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.review-confirm-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.review-confirm-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
