<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { springApi, specApi } from '@/api'
import type { SpringArchive as SpringArchiveType } from '@/types'
import BatchTransferModal from '@/components/BatchTransferModal.vue'
import {
  Plus,
  Search,
  Cog,
  Ruler,
  Circle,
  Factory,
  Calendar,
  CheckCircle2,
  XCircle,
} from 'lucide-vue-next'

const lineStore = useLineStore()
const loading = ref(false)
const springs = ref<SpringArchiveType[]>([])
const selectedIds = ref<number[]>([])
const showAddModal = ref(false)
const showTransferModal = ref(false)

const searchForm = reactive({
  lineId: null as number | null,
  keyword: '',
})

const addForm = reactive({
  springCode: '',
  model: '',
  elasticCoefficient: null as number | null,
  outerDiameter: null as number | null,
  currentLineId: null as number | null,
})

const elasticSpecs = ref<number[]>([])

const pagination = reactive({
  page: 0,
  size: 20,
  total: 0,
})

const selectedSprings = computed(() => {
  return springs.value.filter((s) => selectedIds.value.includes(s.id))
})

const springsByLine = computed(() => {
  const map = new Map<number, SpringArchiveType[]>()
  lineStore.lines.forEach((line) => map.set(line.id, []))
  springs.value.forEach((spring) => {
    const list = map.get(spring.currentLineId) || []
    list.push(spring)
    map.set(spring.currentLineId, list)
  })
  return map
})

async function fetchSprings() {
  loading.value = true
  try {
    const response = await springApi.list({
      lineId: searchForm.lineId ?? undefined,
      keyword: searchForm.keyword || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    springs.value = response.data.content
    pagination.total = response.data.totalElements
  } finally {
    loading.value = false
  }
}

async function fetchElasticSpecs() {
  try {
    const response = await specApi.getElasticForce()
    elasticSpecs.value = response.data
  } catch {
    // ignore
  }
}

function handleSearch() {
  pagination.page = 0
  fetchSprings()
}

function handleReset() {
  searchForm.lineId = null
  searchForm.keyword = ''
  pagination.page = 0
  fetchSprings()
}

function handleSelectionChange(ids: number[]) {
  selectedIds.value = ids
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  fetchSprings()
}

async function handleAddSpring() {
  if (!addForm.springCode.trim()) {
    ElMessage.warning('请输入弹簧编号')
    return
  }
  if (!addForm.model.trim()) {
    ElMessage.warning('请输入弹簧型号')
    return
  }
  if (addForm.elasticCoefficient == null) {
    ElMessage.warning('请输入弹力系数')
    return
  }
  if (addForm.outerDiameter == null) {
    ElMessage.warning('请输入外径尺寸')
    return
  }
  if (addForm.currentLineId == null) {
    ElMessage.warning('请选择归属产线')
    return
  }

  try {
    await springApi.create({
      springCode: addForm.springCode.trim(),
      model: addForm.model.trim(),
      elasticCoefficient: addForm.elasticCoefficient,
      outerDiameter: addForm.outerDiameter,
      currentLineId: addForm.currentLineId,
      initialLineId: addForm.currentLineId,
    })
    ElMessage.success('弹簧档案创建成功')
    showAddModal.value = false
    resetAddForm()
    fetchSprings()
    fetchElasticSpecs()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  }
}

function resetAddForm() {
  addForm.springCode = ''
  addForm.model = ''
  addForm.elasticCoefficient = null
  addForm.outerDiameter = null
  addForm.currentLineId = null
}

function handleBatchTransfer() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要划转的弹簧')
    return
  }
  showTransferModal.value = true
}

function handleTransferSuccess() {
  selectedIds.value = []
  fetchSprings()
}

function handleRowAnimation(index: number) {
  return {
    animationDelay: `${index * 30}ms`,
  }
}

onMounted(async () => {
  await lineStore.fetchLines()
  fetchSprings()
  fetchElasticSpecs()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="card-industrial p-4">
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <Factory class="w-5 h-5 text-primary-600" />
          <span class="text-sm font-medium text-industrial-700">归属产线：</span>
          <select
            v-model="searchForm.lineId"
            class="input-industrial w-48"
            @change="handleSearch"
          >
            <option :value="null">全部产线</option>
            <option v-for="line in lineStore.lines" :key="line.id" :value="line.id">
              {{ line.lineName }}
            </option>
          </select>
        </div>
        <div class="flex items-center gap-2 flex-1 max-w-md">
          <Search class="w-5 h-5 text-industrial-400" />
          <input
            v-model="searchForm.keyword"
            type="text"
            class="input-industrial flex-1"
            placeholder="搜索弹簧编号或型号..."
            @keyup.enter="handleSearch"
          />
          <button class="btn-industrial" @click="handleSearch">
            搜索
          </button>
          <button class="btn-industrial-outline" @click="handleReset">
            重置
          </button>
        </div>
        <div class="flex items-center gap-2 ml-auto">
          <button
            class="btn-industrial-accent"
            :disabled="selectedIds.length === 0"
            @click="handleBatchTransfer"
          >
            批量划转 ({{ selectedIds.length }})
          </button>
          <button class="btn-industrial" @click="showAddModal = true">
            <Plus class="w-4 h-4 inline mr-1" />
            新增弹簧
          </button>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <div
        v-for="line in lineStore.lines"
        :key="line.id"
        class="card-industrial p-4 transition-all duration-200 hover:shadow-industrial-hover"
      >
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <div class="w-3 h-3 rounded-full bg-accent-500"></div>
            <span class="font-mono text-xs text-industrial-500">{{ line.lineCode }}</span>
          </div>
          <span class="text-2xl font-bold text-primary-800 font-mono">
            {{ springsByLine.get(line.id)?.length || 0 }}
          </span>
        </div>
        <div class="font-semibold text-industrial-800">{{ line.lineName }}</div>
        <div class="text-xs text-industrial-500 mt-1">{{ line.description }}</div>
      </div>
    </div>

    <div class="card-industrial overflow-hidden">
      <div class="overflow-x-auto">
        <table class="table-industrial">
          <thead>
            <tr>
              <th class="w-12">
                <input
                  type="checkbox"
                  :checked="selectedIds.length === springs.length && springs.length > 0"
                  @change="(e) => {
                    const checked = (e.target as HTMLInputElement).checked
                    selectedIds = checked ? springs.map(s => s.id) : []
                  }"
                  class="w-4 h-4"
                />
              </th>
              <th>弹簧编号</th>
              <th>型号</th>
              <th>弹力系数 (N/mm)</th>
              <th>外径尺寸 (mm)</th>
              <th>当前归属产线</th>
              <th>初始归属产线</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody v-if="!loading && springs.length > 0">
            <tr
              v-for="(spring, index) in springs"
              :key="spring.id"
              class="animate-stagger"
              :style="handleRowAnimation(index)"
            >
              <td>
                <input
                  type="checkbox"
                  :value="spring.id"
                  v-model="selectedIds"
                  class="w-4 h-4"
                />
              </td>
              <td class="font-mono text-sm font-medium text-primary-800">
                <Cog class="w-4 h-4 inline mr-1 text-primary-500" />
                {{ spring.springCode }}
              </td>
              <td class="text-industrial-800">
                <Circle class="w-4 h-4 inline mr-1 text-industrial-400" />
                {{ spring.model }}
              </td>
              <td class="font-mono text-sm">
                <Ruler class="w-4 h-4 inline mr-1 text-accent-500" />
                <span class="text-accent-700 font-medium">{{ spring.elasticCoefficient }}</span>
              </td>
              <td class="font-mono text-sm text-industrial-600">
                {{ spring.outerDiameter }}
              </td>
              <td>
                <span class="px-2 py-1 bg-primary-100 text-primary-800 rounded text-xs font-medium">
                  {{ spring.currentLineName || lineStore.getLineName(spring.currentLineId) }}
                </span>
              </td>
              <td>
                <span class="px-2 py-1 bg-industrial-100 text-industrial-600 rounded text-xs">
                  {{ spring.initialLineName || lineStore.getLineName(spring.initialLineId) }}
                </span>
              </td>
              <td class="text-sm text-industrial-500 font-mono">
                <Calendar class="w-4 h-4 inline mr-1" />
                {{ spring.createTime }}
              </td>
            </tr>
          </tbody>
          <tbody v-else-if="!loading">
            <tr>
              <td colspan="8" class="text-center py-16 text-industrial-400">
                <Cog class="w-16 h-16 mx-auto mb-4 opacity-30" />
                <p class="text-lg">暂无弹簧档案</p>
                <p class="text-sm mt-2">点击右上角"新增弹簧"按钮创建档案</p>
              </td>
            </tr>
          </tbody>
          <tbody v-else>
            <tr>
              <td colspan="8" class="text-center py-16 text-industrial-400">
                <div class="animate-pulse">加载中...</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="pagination.total > 0" class="flex items-center justify-between px-6 py-4 border-t border-industrial-200">
        <div class="text-sm text-industrial-600">
          共 <span class="font-mono font-medium">{{ pagination.total }}</span> 条记录
        </div>
        <div class="flex items-center gap-2">
          <button
            class="px-3 py-1 rounded-industrial border border-industrial-200 text-sm disabled:opacity-50"
            :disabled="pagination.page === 0"
            @click="handlePageChange(pagination.page)"
          >
            上一页
          </button>
          <span class="text-sm text-industrial-600">
            第 <span class="font-mono font-medium">{{ pagination.page + 1 }}</span> 页 / 共
            <span class="font-mono font-medium">{{ Math.ceil(pagination.total / pagination.size) }}</span> 页
          </span>
          <button
            class="px-3 py-1 rounded-industrial border border-industrial-200 text-sm disabled:opacity-50"
            :disabled="pagination.page >= Math.ceil(pagination.total / pagination.size) - 1"
            @click="handlePageChange(pagination.page + 2)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="showAddModal"
      title="新增弹簧档案"
      width="500px"
      class="add-modal"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Cog class="w-4 h-4 inline mr-1" />
            弹簧编号 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="addForm.springCode"
            type="text"
            class="input-industrial"
            placeholder="例如：SP-2024-0013"
            maxlength="32"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Circle class="w-4 h-4 inline mr-1" />
            弹簧型号 <span class="text-red-500">*</span>
          </label>
          <input
            v-model="addForm.model"
            type="text"
            class="input-industrial"
            placeholder="例如：C-Spring-06"
            maxlength="64"
          />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-industrial-700 mb-1">
              <Ruler class="w-4 h-4 inline mr-1" />
              弹力系数 (N/mm) <span class="text-red-500">*</span>
            </label>
            <input
              v-model.number="addForm.elasticCoefficient"
              type="number"
              step="0.0001"
              min="0"
              class="input-industrial"
              placeholder="例如：0.6000"
            />
            <datalist id="elasticList">
              <option v-for="spec in elasticSpecs" :key="spec" :value="spec" />
            </datalist>
          </div>
          <div>
            <label class="block text-sm font-medium text-industrial-700 mb-1">
              <Ruler class="w-4 h-4 inline mr-1" />
              外径尺寸 (mm) <span class="text-red-500">*</span>
            </label>
            <input
              v-model.number="addForm.outerDiameter"
              type="number"
              step="0.0001"
              min="0"
              class="input-industrial"
              placeholder="例如：14.0000"
            />
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-industrial-700 mb-1">
            <Factory class="w-4 h-4 inline mr-1" />
            初始归属产线 <span class="text-red-500">*</span>
          </label>
          <select v-model="addForm.currentLineId" class="input-industrial">
            <option :value="null" disabled>请选择产线</option>
            <option v-for="line in lineStore.lines" :key="line.id" :value="line.id">
              {{ line.lineName }} ({{ line.lineCode }})
            </option>
          </select>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-industrial-outline" @click="showAddModal = false; resetAddForm()">
            取消
          </button>
          <button class="btn-industrial" @click="handleAddSpring">
            <CheckCircle2 class="w-4 h-4 inline mr-1" />
            确认创建
          </button>
        </div>
      </template>
    </el-dialog>

    <BatchTransferModal
      v-model:visible="showTransferModal"
      :selected-springs="selectedSprings"
      @success="handleTransferSuccess"
    />
  </div>
</template>

<style scoped>
.add-modal :deep(.el-dialog__header) {
  border-bottom: 2px solid #1e40af;
  padding-bottom: 16px;
}

.add-modal :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1e40af;
}
</style>
