<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { elasticSampleApi } from '@/api'
import type { SpringArchive, ElasticSample, RegisterSampleRequest } from '@/types'
import { ClipboardPlus, UserRound, Ruler, Factory, AlertTriangle, CheckCircle2, X, FlaskConical } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  /** 来源弹簧；为空时弹窗内可按弹簧编号搜索选择 */
  spring: SpringArchive | null
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', sample: ElasticSample): void
}>()

const OPERATOR_KEY = 'elastic-sample-operator'

const lineStore = useLineStore()
const saving = ref(false)

const form = reactive<RegisterSampleRequest>({
  springId: null as unknown as number,
  lineId: null,
  measuredCoefficient: null as unknown as number,
  operator: localStorage.getItem(OPERATOR_KEY) ?? '',
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const targetSpring = computed(() => props.spring)
const targetLine = computed(() =>
  form.lineId ? lineStore.lines.find((l) => l.id === form.lineId) ?? null : null
)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.operator = localStorage.getItem(OPERATOR_KEY) ?? ''
      form.measuredCoefficient = null as unknown as number
      form.lineId = props.spring?.currentLineId ?? null
      form.springId = props.spring?.id ?? (null as unknown as number)
      if (lineStore.lines.length === 0) lineStore.fetchLines()
    }
  }
)

/** 实测系数是否落在所选产线适用区间（前端预判展示，最终以后端判定为准） */
const previewDeviated = computed<boolean | null>(() => {
  if (form.measuredCoefficient == null || !targetLine.value) return null
  const { elasticMin, elasticMax } = targetLine.value
  if (elasticMin != null && form.measuredCoefficient < elasticMin) return true
  if (elasticMax != null && form.measuredCoefficient > elasticMax) return true
  return false
})

function rangeText(min?: number | null, max?: number | null) {
  return `${min ?? '不限'} ~ ${max ?? '不限'}`
}

async function handleSubmit() {
  if (!form.springId) {
    ElMessage.warning('请选择抽检弹簧')
    return
  }
  if (form.measuredCoefficient == null || !(form.measuredCoefficient > 0)) {
    ElMessage.warning('请输入大于 0 的实测弹力系数')
    return
  }
  if (!form.operator.trim()) {
    ElMessage.warning('请输入质量员姓名')
    return
  }
  saving.value = true
  try {
    const response = await elasticSampleApi.register({
      springId: form.springId,
      // 始终按弹簧当前所在产线登记，避免选错产线
      lineId: targetSpring.value?.currentLineId ?? form.lineId,
      measuredCoefficient: Number(form.measuredCoefficient),
      operator: form.operator.trim(),
    })
    localStorage.setItem(OPERATOR_KEY, form.operator.trim())
    ElMessage.success(
      response.data.deviated
        ? `留样 ${response.data.sampleNo} 已登记：实测系数偏离适用区间，弹簧档案已挂黄标，闭环并经质量主管摘标加签前不能划转`
        : `留样 ${response.data.sampleNo} 已登记：实测系数在适用区间内`
    )
    dialogVisible.value = false
    emit('saved', response.data)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '登记失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="弹力抽检留样登记" width="520px" class="sample-modal">
    <div class="space-y-4">
      <!-- 弹簧摘要 -->
      <div v-if="targetSpring" class="p-3 bg-industrial-50 rounded-industrial text-sm space-y-1">
        <div class="flex justify-between">
          <span class="text-industrial-600">弹簧编号：</span>
          <span class="font-mono font-medium text-primary-800">{{ targetSpring.springCode }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">型号：</span>
          <span class="font-medium">{{ targetSpring.model }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-industrial-600">档案弹力系数：</span>
          <span class="font-mono">{{ targetSpring.elasticCoefficient }} N/mm</span>
        </div>
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <Factory class="w-4 h-4 inline mr-1" />
          登记产线（按弹簧当前所在产线）
        </label>
        <div class="input-industrial bg-industrial-50 text-industrial-700 flex items-center gap-2">
          <FlaskConical class="w-4 h-4 text-primary-500" />
          <span v-if="targetSpring">
            {{ targetSpring.currentLineName }}
            <span class="text-industrial-400 font-mono ml-1">
              适用区间 {{ rangeText(lineStore.lines.find((l) => l.id === targetSpring.currentLineId)?.elasticMin, lineStore.lines.find((l) => l.id === targetSpring.currentLineId)?.elasticMax) }}
            </span>
          </span>
          <span v-else class="text-industrial-400">请从留样管理页选择弹簧</span>
        </div>
      </div>

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
          placeholder="请输入抽检实测系数，例如 0.6200"
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
              ? `实测系数偏离该线适用区间（${targetLine ? rangeText(targetLine.elasticMin, targetLine.elasticMax) : ''}），登记后弹簧档案挂黄标，全部闭环并经质量主管摘标加签前不能勾进划转申请`
              : '实测系数在该线适用区间内，登记后为待闭环留样，不挂黄标' }}
          </span>
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
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <button class="btn-industrial-outline" @click="dialogVisible = false">
          <X class="w-4 h-4 inline mr-1" />
          取消
        </button>
        <button class="btn-industrial-accent" :disabled="saving" @click="handleSubmit">
          <ClipboardPlus class="w-4 h-4 inline mr-1" />
          {{ saving ? '提交中...' : '确认登记' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.sample-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.sample-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
