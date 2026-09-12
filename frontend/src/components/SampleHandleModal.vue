<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { elasticSampleApi } from '@/api'
import type { ElasticSample, CloseSampleRequest, UpdateMeasuredRequest } from '@/types'
import {
  Lock,
  LockOpen,
  UserRound,
  Ruler,
  ClipboardCheck,
  X,
  AlertTriangle,
  CheckCircle2,
} from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  sample: ElasticSample | null
  /** measured-修改实测系数（仅待闭环）；close-闭环（处置结论必填） */
  mode: 'measured' | 'close'
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', sample: ElasticSample): void
}>()

const OPERATOR_KEY = 'elastic-sample-operator'

const saving = ref(false)
const form = reactive({
  measuredCoefficient: null as number | null,
  operator: localStorage.getItem(OPERATOR_KEY) ?? '',
  conclusion: '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const isClose = computed(() => props.mode === 'close')
const title = computed(() => {
  if (!props.sample) return '留样处置'
  return isClose.value ? `留样闭环 · ${props.sample.sampleNo}` : `修改实测系数 · ${props.sample.sampleNo}`
})

watch(
  () => props.visible,
  (v) => {
    if (v && props.sample) {
      form.measuredCoefficient = props.sample.measuredCoefficient
      form.operator = localStorage.getItem(OPERATOR_KEY) ?? ''
      form.conclusion = ''
    }
  }
)

function rangeText(sample: ElasticSample) {
  return `${sample.lineElasticMin ?? '不限'} ~ ${sample.lineElasticMax ?? '不限'}`
}

/** 修改后的偏离预判（后端最终判定） */
const previewDeviated = computed<boolean | null>(() => {
  if (!props.sample || isClose.value || form.measuredCoefficient == null) return null
  const { lineElasticMin, lineElasticMax } = props.sample
  if (lineElasticMin != null && form.measuredCoefficient < Number(lineElasticMin)) return true
  if (lineElasticMax != null && form.measuredCoefficient > Number(lineElasticMax)) return true
  return false
})

async function handleSubmit() {
  if (!props.sample) return
  if (!isClose.value) {
    if (form.measuredCoefficient == null || !(Number(form.measuredCoefficient) > 0)) {
      ElMessage.warning('请输入大于 0 的实测弹力系数')
      return
    }
  } else {
    if (!form.operator.trim()) {
      ElMessage.warning('请输入质量员姓名')
      return
    }
    if (!form.conclusion.trim()) {
      ElMessage.warning('不写处置结论不能闭环')
      return
    }
  }
  saving.value = true
  try {
    if (isClose.value) {
      const payload: CloseSampleRequest = {
        operator: form.operator.trim(),
        conclusion: form.conclusion.trim(),
      }
      const response = await elasticSampleApi.close(props.sample.id, payload)
      localStorage.setItem(OPERATOR_KEY, form.operator.trim())
      ElMessage.success('留样已闭环，偏离件处置完成后黄标自动摘除')
      dialogVisible.value = false
      emit('saved', response.data)
    } else {
      const payload: UpdateMeasuredRequest = { measuredCoefficient: Number(form.measuredCoefficient) }
      const response = await elasticSampleApi.updateMeasured(props.sample.id, payload)
      ElMessage.success(
        response.data.deviated
          ? '实测系数已更新：仍偏离适用区间，黄标保持'
          : '实测系数已更新：已回到适用区间，若该弹簧无其他未闭环偏离单则黄标摘除'
      )
      dialogVisible.value = false
      emit('saved', response.data)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="520px" class="sample-handle-modal">
    <div v-if="sample" class="space-y-4">
      <!-- 留样摘要 -->
      <div class="p-3 bg-industrial-50 rounded-industrial text-sm space-y-1">
        <div class="flex justify-between">
          <span class="text-industrial-600">留样编号：</span>
          <span class="font-mono font-medium text-primary-800">{{ sample.sampleNo }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">弹簧编号：</span>
          <span class="font-mono">{{ sample.springCode }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">登记产线（快照）：</span>
          <span>{{ sample.lineName }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">适用区间：</span>
          <span class="font-mono">{{ rangeText(sample) }} N/mm</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">当前实测系数：</span>
          <span class="font-mono" :class="sample.deviated ? 'text-amber-600 font-medium' : 'text-green-700'">
            {{ sample.measuredCoefficient }} N/mm
            <span class="ml-1">{{ sample.deviated ? '（偏离）' : '（在区间内）' }}</span>
          </span>
        </div>
      </div>

      <!-- 闭环模式：偏离提示 -->
      <div
        v-if="isClose && sample.deviated"
        class="flex items-start gap-2 p-2 rounded-industrial bg-amber-50 border border-amber-200 text-xs text-amber-700"
      >
        <AlertTriangle class="w-4 h-4 flex-shrink-0 mt-0.5" />
        <span>该件为偏离件，闭环后若该弹簧无其他未闭环偏离留样，档案黄标自动摘除并恢复可划转。</span>
      </div>

      <!-- 修改实测系数 -->
      <template v-if="!isClose">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Ruler class="w-4 h-4 inline mr-1" />
            实测弹力系数 (N/mm) <span class="text-red-500">*</span>
          </label>
          <input
            v-model.number="form.measuredCoefficient"
            type="number"
            step="0.0001"
            min="0"
            class="input-industrial"
            placeholder="请输入修正后的实测系数"
          />
          <div
            v-if="previewDeviated !== null"
            class="mt-2 flex items-start gap-2 text-xs rounded-industrial p-2"
            :class="previewDeviated ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-green-50 text-green-700 border border-green-200'"
          >
            <AlertTriangle v-if="previewDeviated" class="w-4 h-4 flex-shrink-0 mt-0.5" />
            <CheckCircle2 v-else class="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>
              {{ previewDeviated
                ? `修改后仍偏离适用区间（${rangeText(sample)}），黄标保持`
                : `修改后回到适用区间（${rangeText(sample)}），保存后黄标按剩余未闭环偏离单重算` }}
            </span>
          </div>
          <p class="text-xs text-industrial-400 mt-2">
            仅待闭环留样单可修改实测系数；已闭环单实测系数锁定不可改。
          </p>
        </div>
      </template>

      <!-- 闭环 -->
      <template v-else>
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
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <ClipboardCheck class="w-4 h-4 inline mr-1" />
            处置结论 <span class="text-red-500">*</span>
          </label>
          <textarea
            v-model="form.conclusion"
            rows="4"
            class="input-industrial resize-none"
            placeholder="例如：复测确认超差，安排返工/让步接收/报废处理；不写处置结论不能闭环"
            maxlength="512"
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
        <button v-if="isClose" class="btn-industrial" :disabled="saving" @click="handleSubmit">
          <Lock class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '确认闭环' }}
        </button>
        <button v-else class="btn-industrial-accent" :disabled="saving" @click="handleSubmit">
          <LockOpen class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '保存实测系数' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.sample-handle-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.sample-handle-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
