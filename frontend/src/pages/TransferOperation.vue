<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { springApi, transferApi } from '@/api'
import type { SpringArchive, TransferRecord, BatchTransferRequest } from '@/types'
import {
  GitBranch,
  Search,
  Factory,
  CheckCircle2,
  ArrowLeftRight,
  Users,
  FileText,
  Clock,
  ChevronRight,
  Cog,
} from 'lucide-vue-next'

const lineStore = useLineStore()
const loading = ref(false)
const springs = ref<SpringArchive[]>([])
const selectedIds = ref<number[]>([])
const recentTransfers = ref<TransferRecord[]>([])

const searchForm = reactive({
  lineId: null as number | null,
  keyword: '',
})

const transferForm = reactive<BatchTransferRequest>({
  springIds: [],
  toLineId: null as unknown as number,
  operator: '',
  remark: '',
})

const selectedSprings = computed(() => {
  return springs.value.filter((s) => selectedIds.value.includes(s.id))
})

const availableTargetLines = computed(() => {
  if (selectedSprings.value.length === 0) return lineStore.lines
  const fromLineIds = new Set(selectedSprings.value.map((s) => s.currentLineId))
  return lineStore.lines.filter((line) => !fromLineIds.has(line.id))
})

async function fetchSprings() {
  loading.value = true
  try {
    const response = await springApi.list({
      lineId: searchForm.lineId ?? undefined,
      keyword: searchForm.keyword || undefined,
      size: 100,
    })
    springs.value = response.data.content
  } finally {
    loading.value = false
  }
}

async function fetchRecentTransfers() {
  try {
    const response = await transferApi.list({ size: 5 })
    recentTransfers.value = response.data.content
  } catch {
    // ignore
  }
}

function handleSearch() {
  fetchSprings()
}

function handleReset() {
  searchForm.lineId = null
  searchForm.keyword = ''
  fetchSprings()
}

function handleSelectAll() {
  if (selectedIds.value.length === springs.value.length) {
    selectedIds.value = []
  } else {
    selectedIds.value = springs.value.map((s) => s.id)
  }
}

function toggleSelect(id: number) {
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

async function handleTransfer() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要划转的弹簧')
    return
  }
  if (!transferForm.toLineId) {
    ElMessage.warning('请选择目标产线')
    return
  }
  if (!transferForm.operator.trim()) {
    ElMessage.warning('请输入操作人')
    return
  }

  const validSprings = selectedSprings.value.filter((s) => s.currentLineId !== transferForm.toLineId)
  if (validSprings.length === 0) {
    ElMessage.warning('所选弹簧已归属目标产线，无需划转')
    return
  }

  try {
    const response = await transferApi.batchTransfer({
      springIds: validSprings.map((s) => s.id),
      toLineId: transferForm.toLineId,
      operator: transferForm.operator.trim(),
      remark: transferForm.remark.trim() || undefined,
    })
    ElMessage.success(`成功划转 ${response.data.length} 条弹簧`)
    selectedIds.value = []
    transferForm.toLineId = null as unknown as number
    transferForm.operator = ''
    transferForm.remark = ''
    fetchSprings()
    fetchRecentTransfers()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '划转失败')
  }
}

function formatTime(time: string) {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(async () => {
  await lineStore.fetchLines()
  fetchSprings()
  fetchRecentTransfers()
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
    <div class="lg:col-span-2 space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Cog class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">待划转弹簧列表</h2>
        </div>
        <div class="flex flex-wrap items-center gap-4 mb-4">
          <div class="flex items-center gap-2">
            <Factory class="w-4 h-4 text-primary-600" />
            <span class="text-sm font-medium text-industrial-700">当前产线：</span>
            <select
              v-model="searchForm.lineId"
              class="input-industrial w-44"
              @change="handleSearch"
            >
              <option :value="null">全部产线</option>
              <option v-for="line in lineStore.lines" :key="line.id" :value="line.id">
                {{ line.lineName }}
              </option>
            </select>
          </div>
          <div class="flex items-center gap-2 flex-1 max-w-sm">
            <Search class="w-4 h-4 text-industrial-400" />
            <input
              v-model="searchForm.keyword"
              type="text"
              class="input-industrial flex-1"
              placeholder="搜索弹簧编号..."
              @keyup.enter="handleSearch"
            />
            <button class="btn-industrial text-sm" @click="handleSearch">
              搜索
            </button>
            <button class="btn-industrial-outline text-sm" @click="handleReset">
              重置
            </button>
          </div>
        </div>

        <div class="max-h-96 overflow-y-auto border border-industrial-200 rounded-industrial">
          <table class="table-industrial text-sm">
            <thead class="sticky top-0">
              <tr>
                <th class="w-12 py-2">
                  <input
                    type="checkbox"
                    :checked="selectedIds.length === springs.length && springs.length > 0"
                    @change="handleSelectAll"
                    class="w-4 h-4"
                  />
                </th>
                <th class="py-2">弹簧编号</th>
                <th class="py-2">型号</th>
                <th class="py-2">弹力系数</th>
                <th class="py-2">当前产线</th>
              </tr>
            </thead>
            <tbody v-if="!loading && springs.length > 0">
              <tr
                v-for="spring in springs"
                :key="spring.id"
                class="cursor-pointer transition-colors"
                :class="{ 'bg-primary-50': selectedIds.includes(spring.id) }"
                @click="toggleSelect(spring.id)"
              >
                <td class="py-2" @click.stop>
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(spring.id)"
                    @change="toggleSelect(spring.id)"
                    class="w-4 h-4"
                  />
                </td>
                <td class="py-2 font-mono text-xs font-medium text-primary-800">
                  {{ spring.springCode }}
                </td>
                <td class="py-2">{{ spring.model }}</td>
                <td class="py-2 font-mono text-xs">{{ spring.elasticCoefficient }} N/mm</td>
                <td class="py-2">
                  <span class="px-2 py-0.5 bg-primary-100 text-primary-700 rounded text-xs">
                    {{ spring.currentLineName || lineStore.getLineName(spring.currentLineId) }}
                  </span>
                </td>
              </tr>
            </tbody>
            <tbody v-else-if="!loading">
              <tr>
                <td colspan="5" class="text-center py-12 text-industrial-400">
                  <Cog class="w-12 h-12 mx-auto mb-3 opacity-30" />
                  <p>暂无弹簧数据</p>
                </td>
              </tr>
            </tbody>
            <tbody v-else>
              <tr>
                <td colspan="5" class="text-center py-12 text-industrial-400">
                  <div class="animate-pulse">加载中...</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <GitBranch class="w-5 h-5 text-accent-600" />
          <h2 class="text-lg font-bold text-industrial-800">划转操作区</h2>
          <span class="ml-auto text-sm text-industrial-500">
            已选择 <span class="font-mono font-bold text-primary-700">{{ selectedIds.length }}</span> 项
          </span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div class="space-y-3">
            <label class="block text-sm font-medium text-industrial-700">
              <ArrowLeftRight class="w-4 h-4 inline mr-1" />
              目标产线 <span class="text-red-500">*</span>
            </label>
            <div class="space-y-2 max-h-48 overflow-y-auto">
              <label
                v-for="line in availableTargetLines"
                :key="line.id"
                class="flex items-center gap-3 p-3 rounded-industrial cursor-pointer transition-all border-2"
                :class="[
                  transferForm.toLineId === line.id
                    ? 'border-primary-600 bg-primary-50'
                    : 'border-transparent hover:bg-industrial-50',
                ]"
              >
                <input
                  type="radio"
                  :value="line.id"
                  v-model="transferForm.toLineId"
                  class="w-4 h-4 text-primary-600"
                />
                <div class="flex-1 min-w-0">
                  <div class="font-medium text-industrial-800 text-sm">{{ line.lineName }}</div>
                  <div class="text-xs text-industrial-500 font-mono">{{ line.lineCode }}</div>
                </div>
              </label>
              <div
                v-if="availableTargetLines.length === 0"
                class="text-center py-4 text-industrial-400 text-sm"
              >
                请先选择弹簧
              </div>
            </div>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <Users class="w-4 h-4 inline mr-1" />
                操作人 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="transferForm.operator"
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
                v-model="transferForm.remark"
                class="input-industrial h-24 resize-none"
                placeholder="请输入划转备注（可选）"
                maxlength="255"
              ></textarea>
            </div>
          </div>

          <div class="flex flex-col justify-center">
            <div class="card-industrial p-4 bg-industrial-50">
              <h3 class="font-semibold text-industrial-800 mb-3">划转信息确认</h3>
              <div class="space-y-2 text-sm">
                <div class="flex justify-between">
                  <span class="text-industrial-600">选中数量：</span>
                  <span class="font-mono font-medium">{{ selectedIds.length }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">目标产线：</span>
                  <span class="font-medium text-accent-600">
                    {{ transferForm.toLineId ? lineStore.getLineName(transferForm.toLineId) : '未选择' }}
                  </span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">操作人：</span>
                  <span class="font-medium">{{ transferForm.operator || '未填写' }}</span>
                </div>
                <div class="pt-2 mt-2 border-t border-industrial-200">
                  <button
                    class="w-full btn-industrial-accent"
                    :disabled="selectedIds.length === 0 || !transferForm.toLineId || !transferForm.operator"
                    @click="handleTransfer"
                  >
                    <CheckCircle2 class="w-4 h-4 inline mr-1" />
                    确认划转
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Clock class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">最近划转记录</h2>
        </div>
        <div class="space-y-3">
          <div
            v-for="record in recentTransfers"
            :key="record.id"
            class="p-3 bg-industrial-50 rounded-industrial animate-stagger"
            :style="{ animationDelay: `${recentTransfers.indexOf(record) * 50}ms` }"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="font-mono text-xs font-medium text-primary-700">
                {{ record.springCode }}
              </span>
              <span class="text-xs text-industrial-400 font-mono">
                {{ formatTime(record.operateTime) }}
              </span>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <span class="px-2 py-0.5 bg-primary-100 text-primary-700 rounded text-xs">
                {{ record.fromLineName }}
              </span>
              <ChevronRight class="w-4 h-4 text-industrial-400" />
              <span class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded text-xs">
                {{ record.toLineName }}
              </span>
            </div>
            <div class="mt-2 text-xs text-industrial-500">
              <Users class="w-3 h-3 inline mr-1" />
              {{ record.operator }}
            </div>
          </div>
          <div
            v-if="recentTransfers.length === 0"
            class="text-center py-8 text-industrial-400 text-sm"
          >
            暂无划转记录
          </div>
        </div>
      </div>

      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Factory class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">产线概览</h2>
        </div>
        <div class="space-y-2">
          <div
            v-for="line in lineStore.lines"
            :key="line.id"
            class="flex items-center justify-between p-3 bg-industrial-50 rounded-industrial"
          >
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-accent-500"></div>
              <div>
                <div class="font-medium text-industrial-800 text-sm">{{ line.lineName }}</div>
                <div class="text-xs text-industrial-500 font-mono">{{ line.lineCode }}</div>
              </div>
            </div>
            <span class="font-mono font-bold text-primary-800">
              {{ springs.filter(s => s.currentLineId === line.id).length }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
