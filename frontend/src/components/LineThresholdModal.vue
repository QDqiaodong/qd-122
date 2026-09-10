<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { LineLoadStats } from '@/types'
import { lineLoadApi } from '@/api'
import { Settings2, X } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  line: LineLoadStats | null
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

const saving = ref(false)
const form = reactive({
  dailyCapacityThreshold: null as number | null,
  elasticMin: null as number | null,
  elasticMax: null as number | null,
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

function syncForm() {
  if (props.line) {
    form.dailyCapacityThreshold = props.line.dailyCapacityThreshold ?? null
    form.elasticMin = props.line.elasticMin ?? null
    form.elasticMax = props.line.elasticMax ?? null
  }
}
// 每次弹窗打开时用产线当前配置回填
watch(() => props.visible, (v) => {
  if (v) syncForm()
})

async function handleSave() {
  if (!props.line) return
  if (form.dailyCapacityThreshold == null || form.dailyCapacityThreshold < 1) {
    ElMessage.warning('请输入有效的日承载阈值（≥1）')
    return
  }
  if (
    form.elasticMin != null &&
    form.elasticMax != null &&
    Number(form.elasticMin) > Number(form.elasticMax)
  ) {
    ElMessage.warning('弹力系数下限不能大于上限')
    return
  }
  saving.value = true
  try {
    await lineLoadApi.updateThreshold(props.line.lineId, {
      dailyCapacityThreshold: form.dailyCapacityThreshold,
      elasticMin: form.elasticMin,
      elasticMax: form.elasticMax,
    })
    ElMessage.success('产线负载阈值已更新')
    dialogVisible.value = false
    emit('saved')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="维护产线负载阈值"
    width="480px"
    class="threshold-modal"
  >
    <div v-if="line" class="space-y-4">
      <div class="flex items-center gap-2 p-3 bg-industrial-100 rounded-industrial">
        <span class="font-mono text-xs text-industrial-500">{{ line.lineCode }}</span>
        <span class="font-semibold text-industrial-800">{{ line.lineName }}</span>
      </div>

      <div>
        <label class="block text-sm font-medium text-industrial-700 mb-1">
          <Settings2 class="w-4 h-4 inline mr-1" />
          日承载阈值（件/日） <span class="text-red-500">*</span>
        </label>
        <input
          v-model.number="form.dailyCapacityThreshold"
          type="number"
          min="1"
          step="1"
          class="input-industrial"
          placeholder="例如：5"
        />
        <p class="text-xs text-industrial-400 mt-1">当前归属数量超过该阈值即判定为超载</p>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            弹力系数下限 (N/mm)
          </label>
          <input
            v-model.number="form.elasticMin"
            type="number"
            min="0"
            step="0.0001"
            class="input-industrial"
            placeholder="留空不限制"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            弹力系数上限 (N/mm)
          </label>
          <input
            v-model.number="form.elasticMax"
            type="number"
            min="0"
            step="0.0001"
            class="input-industrial"
            placeholder="留空不限制"
          />
        </div>
      </div>
      <p class="text-xs text-industrial-400 -mt-2">归属弹簧系数超出该区间时触发预警</p>
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <button class="btn-industrial-outline" @click="dialogVisible = false">
          <X class="w-4 h-4 inline mr-1" />
          取消
        </button>
        <button class="btn-industrial" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : '保存配置' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.threshold-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}
.threshold-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
