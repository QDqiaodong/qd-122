<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { springApi } from '@/api'
import type { SpringArchive } from '@/types'
import { CheckCircle2, UserRound, ClipboardCheck, TriangleAlert, X } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  spring: SpringArchive | null
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', spring: SpringArchive): void
}>()

/** 质量主管工号本地记忆，刷新后仍可带出 */
const OPERATOR_ID_KEY = 'flag-countersign-operator-id'

const saving = ref(false)
const form = reactive({
  operatorId: localStorage.getItem(OPERATOR_ID_KEY) ?? '',
  note: '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const title = computed(() => {
  if (!props.spring) return '黄标摘标加签'
  return `摘标加签 · ${props.spring.springCode}`
})

/** 仍有偏离留样待闭环时，只能提示去留样页闭环，不允许提交 */
const pendingCount = computed(() => props.spring?.pendingDeviationCount ?? 0)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.operatorId = localStorage.getItem(OPERATOR_ID_KEY) ?? ''
      form.note = ''
    }
  }
)

function formatTime(time?: string | null) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

async function handleSubmit() {
  if (!props.spring) return
  // 工号或说明为空时不能加签
  if (!form.operatorId.trim()) {
    ElMessage.warning('请填写质量主管工号')
    return
  }
  if (!form.note.trim()) {
    ElMessage.warning('请填写加签说明')
    return
  }
  saving.value = true
  try {
    const response = await springApi.flagCountersign(props.spring.id, {
      operatorId: form.operatorId.trim(),
      note: form.note.trim(),
    })
    localStorage.setItem(OPERATOR_ID_KEY, form.operatorId.trim())
    ElMessage.success('摘标加签完成，黄标已摘除，该件恢复可勾选划转')
    dialogVisible.value = false
    emit('saved', response.data)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加签失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="520px" class="countersign-modal">
    <div v-if="spring" class="space-y-4">
      <!-- 弹簧摘要 -->
      <div class="p-3 bg-industrial-50 rounded-industrial text-sm space-y-1">
        <div class="flex justify-between">
          <span class="text-industrial-600">弹簧编号：</span>
          <span class="font-mono font-medium text-primary-800">{{ spring.springCode }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">型号：</span>
          <span class="font-medium">{{ spring.model }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">当前产线：</span>
          <span class="font-medium">{{ spring.currentLineName }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">待闭环偏离留样：</span>
          <span class="font-mono" :class="pendingCount > 0 ? 'text-amber-600 font-semibold' : 'text-green-700'">
            {{ pendingCount }} 张
          </span>
        </div>
      </div>

      <!-- 仍有待闭环留样：禁止加签 -->
      <div
        v-if="pendingCount > 0"
        class="flex items-start gap-2 p-3 rounded-industrial bg-amber-50 border border-amber-200 text-xs text-amber-700"
      >
        <TriangleAlert class="w-4 h-4 flex-shrink-0 mt-0.5" />
        <span>
          该件仍有 {{ pendingCount }} 张偏离留样待闭环，请先在「弹力抽检留样」完成闭环处置，
          全部闭环后才能摘标加签；加签完成前该件在划转申请中仍不可勾选。
        </span>
      </div>

      <!-- 上一次加签记录（加签信息持久化，刷新后仍在） -->
      <div
        v-else-if="spring.flagCountersignTime"
        class="p-3 rounded-industrial bg-green-50 border border-green-200 text-sm space-y-1"
      >
        <div class="flex items-center gap-2 text-green-700 font-medium mb-1">
          <CheckCircle2 class="w-4 h-4" />
          最近一次摘标加签
        </div>
        <div class="text-industrial-700">
          加签人工号：<span class="font-mono font-medium">{{ spring.flagCountersignOperator }}</span>
        </div>
        <div class="text-industrial-700">加签说明：{{ spring.flagCountersignNote }}</div>
        <div class="text-industrial-500 text-xs font-mono">{{ formatTime(spring.flagCountersignTime) }}</div>
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <UserRound class="w-4 h-4 inline mr-1" />
          质量主管工号 <span class="text-red-500">*</span>
        </label>
        <input
          v-model="form.operatorId"
          type="text"
          class="input-industrial"
          placeholder="请输入质量主管工号，例如：QZ-007"
          maxlength="32"
        />
      </div>
      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <ClipboardCheck class="w-4 h-4 inline mr-1" />
          加签说明 <span class="text-red-500">*</span>
        </label>
        <textarea
          v-model="form.note"
          rows="4"
          class="input-industrial resize-none"
          placeholder="例如：偏离留样均已闭环，复测合格，同意摘除黄标恢复划转；工号或说明为空不能加签"
          maxlength="255"
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
          :disabled="saving || pendingCount > 0"
          :title="pendingCount > 0 ? '仍有偏离留样待闭环，不能加签' : ''"
          @click="handleSubmit"
        >
          <CheckCircle2 class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '确认摘标加签' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.countersign-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.countersign-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
