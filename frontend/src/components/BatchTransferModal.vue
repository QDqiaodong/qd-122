<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { transferApi } from '@/api'
import type { SpringArchive, TransferRecord } from '@/types'
import {
  ArrowRight,
  Users,
  FileText,
  AlertTriangle,
} from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  selectedSprings: SpringArchive[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', records: TransferRecord[]): void
}>()

const lineStore = useLineStore()
const toLineId = ref<number | null>(null)
const operator = ref('')
const remark = ref('')
const submitting = ref(false)

const availableTargetLines = computed(() => {
  if (props.selectedSprings.length === 0) return lineStore.lines
  const fromLineIds = new Set(props.selectedSprings.map((s) => s.currentLineId))
  return lineStore.lines.filter((line) => !fromLineIds.has(line.id))
})

const validSprings = computed(() => {
  if (!toLineId.value) return props.selectedSprings
  return props.selectedSprings.filter((s) => s.currentLineId !== toLineId.value)
})

const invalidSprings = computed(() => {
  if (!toLineId.value) return []
  return props.selectedSprings.filter((s) => s.currentLineId === toLineId.value)
})

async function handleSubmit() {
  if (!toLineId.value) {
    ElMessage.warning('请选择目标产线')
    return
  }
  if (!operator.value.trim()) {
    ElMessage.warning('请输入操作人')
    return
  }
  if (validSprings.value.length === 0) {
    ElMessage.warning('没有可划转的弹簧')
    return
  }

  submitting.value = true
  try {
    const response = await transferApi.batchTransfer({
      springIds: validSprings.value.map((s) => s.id),
      toLineId: toLineId.value,
      operator: operator.value.trim(),
      remark: remark.value.trim() || undefined,
    })
    ElMessage.success(`成功划转 ${response.data.length} 条弹簧`)
    emit('success', response.data)
    handleClose()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '划转失败')
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
  toLineId.value = null
  operator.value = ''
  remark.value = ''
}

watch(
  () => props.visible,
  (val) => {
    if (val && lineStore.lines.length === 0) {
      lineStore.fetchLines()
    }
  }
)
</script>

<template>
  <el-dialog
    :model-value="visible"
    width="800px"
    title="批量产线划转"
    @update:model-value="(val) => emit('update:visible', val)"
    @close="handleClose"
    class="transfer-modal"
  >
    <div class="space-y-6">
      <div class="grid grid-cols-2 gap-6">
        <div class="space-y-3">
          <div class="flex items-center gap-2 text-sm font-medium text-industrial-700">
            <span class="w-6 h-6 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-xs font-bold">
              1
            </span>
            选择目标产线
          </div>
          <div class="card-industrial p-4 h-64 overflow-y-auto">
            <div v-if="availableTargetLines.length === 0" class="text-center py-8 text-industrial-400">
              <AlertTriangle class="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>没有可选的目标产线</p>
              <p class="text-xs mt-1">所有弹簧已归属全部产线</p>
            </div>
            <div v-else class="space-y-2">
              <label
                v-for="line in availableTargetLines"
                :key="line.id"
                class="flex items-center gap-3 p-3 rounded-industrial cursor-pointer transition-all border-2"
                :class="[
                  toLineId === line.id
                    ? 'border-primary-600 bg-primary-50'
                    : 'border-transparent hover:bg-industrial-50',
                ]"
              >
                <input
                  type="radio"
                  :value="line.id"
                  v-model="toLineId"
                  class="w-4 h-4 text-primary-600"
                />
                <div class="flex-1 min-w-0">
                  <div class="font-medium text-industrial-800">{{ line.lineName }}</div>
                  <div class="text-xs text-industrial-500 font-mono">{{ line.lineCode }}</div>
                  <div class="text-xs text-industrial-400 truncate">{{ line.description }}</div>
                </div>
              </label>
            </div>
          </div>
        </div>

        <div class="space-y-3">
          <div class="flex items-center gap-2 text-sm font-medium text-industrial-700">
            <span class="w-6 h-6 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-xs font-bold">
              2
            </span>
            登记操作信息
          </div>
          <div class="card-industrial p-4 space-y-4">
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <Users class="w-4 h-4 inline mr-1" />
                操作人 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="operator"
                type="text"
                class="input-industrial"
                placeholder="请输入操作人姓名"
                maxlength="32"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <FileText class="w-4 h-4 inline mr-1" />
                划转备注
              </label>
              <textarea
                v-model="remark"
                class="input-industrial h-24 resize-none"
                placeholder="请输入划转备注（可选）"
                maxlength="255"
              ></textarea>
            </div>

            <div class="mt-4 p-3 bg-industrial-50 rounded-industrial text-sm">
              <div class="font-medium text-industrial-700 mb-2">划转信息确认</div>
              <div class="space-y-1 text-industrial-600">
                <div class="flex justify-between">
                  <span>选中弹簧数：</span>
                  <span class="font-mono">{{ selectedSprings.length }}</span>
                </div>
                <div class="flex justify-between">
                  <span>可划转数量：</span>
                  <span class="font-mono text-primary-700 font-medium">{{ validSprings.length }}</span>
                </div>
                <div v-if="invalidSprings.length > 0" class="flex justify-between">
                  <span>同产线跳过：</span>
                  <span class="font-mono text-industrial-400">{{ invalidSprings.length }}</span>
                </div>
                <div class="flex justify-between pt-2 border-t border-industrial-200 mt-2">
                  <span class="font-medium">目标产线：</span>
                  <span class="font-medium text-accent-600">
                    {{ toLineId ? lineStore.getLineName(toLineId) : '未选择' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="border-t border-industrial-200 pt-4">
        <div class="flex items-center gap-2 text-sm font-medium text-industrial-700 mb-3">
          <span class="w-6 h-6 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-xs font-bold">
            3
          </span>
          待划转弹簧列表
        </div>
        <div class="max-h-48 overflow-y-auto border border-industrial-200 rounded-industrial">
          <table class="table-industrial text-sm">
            <thead class="sticky top-0">
              <tr>
                <th class="py-2">弹簧编号</th>
                <th class="py-2">型号</th>
                <th class="py-2">弹力系数</th>
                <th class="py-2">当前产线</th>
                <th class="py-2">
                  <ArrowRight class="w-4 h-4 inline" />
                </th>
                <th class="py-2">目标产线</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="spring in selectedSprings" :key="spring.id" :class="{ 'opacity-50': spring.currentLineId === toLineId }">
                <td class="py-2 font-mono text-xs">{{ spring.springCode }}</td>
                <td class="py-2">{{ spring.model }}</td>
                <td class="py-2 font-mono text-xs">{{ spring.elasticCoefficient }} N/mm</td>
                <td class="py-2">
                  <span class="px-2 py-0.5 bg-primary-100 text-primary-700 rounded text-xs">
                    {{ spring.currentLineName }}
                  </span>
                </td>
                <td class="py-2 text-center">
                  <ArrowRight class="w-4 h-4 mx-auto text-industrial-400" />
                </td>
                <td class="py-2">
                  <span v-if="toLineId && spring.currentLineId !== toLineId" class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded text-xs">
                    {{ lineStore.getLineName(toLineId) }}
                  </span>
                  <span v-else-if="spring.currentLineId === toLineId" class="text-industrial-400 text-xs">
                    同产线跳过
                  </span>
                  <span v-else class="text-industrial-400 text-xs">未选择</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <button class="btn-industrial-outline" @click="handleClose">
          取消
        </button>
        <button
          class="btn-industrial-accent"
          :disabled="submitting || validSprings.length === 0"
          @click="handleSubmit"
        >
          {{ submitting ? '划转中...' : `确认划转 (${validSprings.length})` }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.transfer-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}

.transfer-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}

.transfer-modal :deep(.el-dialog__footer) {
  border-top: 1px solid #e2e8f0;
  padding-top: 16px;
}
</style>
