<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { springApi } from '@/api'
import type { SpringArchive } from '@/types'
import { Lock, LockOpen, UserRound, FileText, Calendar, ClipboardCheck, X } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  spring: SpringArchive | null
  /** seal-登记封存；unseal-解封并填写结论 */
  mode: 'seal' | 'unseal'
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', spring: SpringArchive): void
}>()

const OPERATOR_KEY = 'spring-seal-operator'

const saving = ref(false)
const form = reactive({
  operator: localStorage.getItem(OPERATOR_KEY) ?? '',
  reason: '',
  expectedUnsealDate: '',
  conclusion: '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const isUnseal = computed(() => props.mode === 'unseal')
const title = computed(() => {
  if (!props.spring) return '弹簧封存'
  return isUnseal.value ? `解封弹簧 · ${props.spring.springCode}` : `封存弹簧 · ${props.spring.springCode}`
})

/** 预计解封日最早为今天 */
const todayStr = new Date().toLocaleDateString('sv-SE')

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.operator = localStorage.getItem(OPERATOR_KEY) ?? ''
      form.reason = ''
      form.expectedUnsealDate = ''
      form.conclusion = ''
    }
  }
)

async function handleSubmit() {
  if (!props.spring) return
  if (!form.operator.trim()) {
    ElMessage.warning('请输入质量员姓名')
    return
  }
  if (isUnseal.value) {
    if (!form.conclusion.trim()) {
      ElMessage.warning('解封必须填写结论')
      return
    }
  } else {
    if (!form.reason.trim()) {
      ElMessage.warning('请填写封存原因')
      return
    }
    if (!form.expectedUnsealDate) {
      ElMessage.warning('请选择预计解封日')
      return
    }
  }

  saving.value = true
  try {
    const response = isUnseal.value
      ? await springApi.unseal(props.spring.id, {
          operator: form.operator.trim(),
          conclusion: form.conclusion.trim(),
        })
      : await springApi.seal(props.spring.id, {
          operator: form.operator.trim(),
          reason: form.reason.trim(),
          expectedUnsealDate: form.expectedUnsealDate,
        })
    localStorage.setItem(OPERATOR_KEY, form.operator.trim())
    ElMessage.success(isUnseal.value ? '弹簧已解封，恢复可划转/可模拟' : '弹簧已封存，封存期间不可划转、不可调拨模拟')
    dialogVisible.value = false
    emit('saved', response.data)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    saving.value = false
  }
}

function formatTime(time?: string | null) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="520px" class="seal-modal">
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
      </div>

      <!-- 解封时展示封存登记信息 -->
      <div
        v-if="isUnseal"
        class="p-3 rounded-industrial border border-red-200 bg-red-50 text-sm space-y-1"
      >
        <div class="flex items-center gap-2 text-red-700 font-medium mb-1">
          <Lock class="w-4 h-4" />
          封存登记信息
        </div>
        <div class="text-industrial-700">
          封存原因：<span class="font-medium">{{ spring.sealReason || '未登记' }}</span>
        </div>
        <div class="text-industrial-600 text-xs">
          质量员 {{ spring.sealOperator || '-' }} 封存于 {{ formatTime(spring.sealTime) }}
          <span v-if="spring.sealExpectedUnsealDate">，预计解封日 {{ spring.sealExpectedUnsealDate }}</span>
        </div>
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <UserRound class="w-4 h-4 inline mr-1" />
          质量员 <span class="text-red-500">*</span>
        </label>
        <input
          v-model="form.operator"
          type="text"
          class="input-industrial"
          placeholder="请输入质量员姓名"
          maxlength="32"
        />
      </div>

      <template v-if="!isUnseal">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <FileText class="w-4 h-4 inline mr-1" />
            封存原因 <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="form.reason"
            rows="3"
            class="input-industrial resize-none"
            placeholder="例如：抽检不合格，弹力系数复测偏差超限；或待复测，等待实验室结果"
            maxlength="255"
          ></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Calendar class="w-4 h-4 inline mr-1" />
            预计解封日 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.expectedUnsealDate"
            type="date"
            class="input-industrial"
            :min="todayStr"
          />
        </div>
        <p class="text-xs text-industrial-400">
          封存期间该弹簧不能进入划转申请和调拨模拟，解封后恢复
        </p>
      </template>

      <template v-else>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <ClipboardCheck class="w-4 h-4 inline mr-1" />
            解封结论 <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="form.conclusion"
            rows="3"
            class="input-industrial resize-none"
            placeholder="例如：复测合格，恢复使用；或判定报废，待下架处理"
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
          v-if="!isUnseal"
          class="px-4 py-2 rounded-industrial bg-red-600 text-white text-sm font-medium hover:bg-red-700 transition-colors disabled:opacity-50"
          :disabled="saving"
          @click="handleSubmit"
        >
          <Lock class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '确认封存' }}
        </button>
        <button
          v-else
          class="btn-industrial"
          :disabled="saving"
          @click="handleSubmit"
        >
          <LockOpen class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '确认解封' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.seal-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.seal-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
