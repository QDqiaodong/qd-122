<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLineStore } from '@/stores/lines'
import { springApi, applicationApi } from '@/api'
import type { SpringArchive, TransferApplication, ApplicationStatus, SubmitApplicationRequest } from '@/types'
import {
  GitBranch,
  Search,
  Factory,
  Send,
  ArrowLeftRight,
  Users,
  FileText,
  Clock,
  Cog,
} from 'lucide-vue-next'

const lineStore = useLineStore()
const loading = ref(false)
const submitting = ref(false)
const springs = ref<SpringArchive[]>([])
const selectedIds = ref<number[]>([])
const recentApplications = ref<TransferApplication[]>([])

const searchForm = reactive({
  lineId: null as number | null,
  keyword: '',
})

const applyForm = reactive<Omit<SubmitApplicationRequest, 'springIds'>>({
  toLineId: null as unknown as number,
  applicant: '',
  reason: '',
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

async function fetchRecentApplications() {
  try {
    const response = await applicationApi.list({ size: 5 })
    recentApplications.value = response.data.content
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

async function handleSubmitApplication() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要划转的弹簧')
    return
  }
  if (!applyForm.toLineId) {
    ElMessage.warning('请选择目标产线')
    return
  }
  if (!applyForm.applicant.trim()) {
    ElMessage.warning('请输入申请人')
    return
  }
  if (!applyForm.reason.trim()) {
    ElMessage.warning('请输入申请原因')
    return
  }

  const validSprings = selectedSprings.value.filter((s) => s.currentLineId !== applyForm.toLineId)
  if (validSprings.length === 0) {
    ElMessage.warning('所选弹簧已归属目标产线，无需划转')
    return
  }

  submitting.value = true
  try {
    const response = await applicationApi.submit({
      springIds: validSprings.map((s) => s.id),
      toLineId: applyForm.toLineId,
      applicant: applyForm.applicant.trim(),
      reason: applyForm.reason.trim(),
    })
    const app = response.data
    ElMessageBox.alert(
      `申请单号：${app.applicationNo}，共 ${app.totalCount ?? validSprings.length} 条弹簧已提交，待审批通过后生效。`,
      '划转申请提交成功',
      { confirmButtonText: '知道了', type: 'success' }
    )
    selectedIds.value = []
    applyForm.toLineId = null as unknown as number
    applyForm.reason = ''
    fetchRecentApplications()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '申请提交失败')
  } finally {
    submitting.value = false
  }
}

const statusMap: Record<ApplicationStatus, { text: string; class: string }> = {
  PENDING: { text: '待审批', class: 'bg-amber-100 text-amber-700' },
  APPROVED: { text: '全部通过', class: 'bg-green-100 text-green-700' },
  REJECTED: { text: '全部驳回', class: 'bg-red-100 text-red-700' },
  PARTIAL: { text: '部分处理', class: 'bg-blue-100 text-blue-700' },
}

function formatTime(time: string) {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(async () => {
  await lineStore.fetchLines()
  fetchSprings()
  fetchRecentApplications()
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
          <h2 class="text-lg font-bold text-industrial-800">划转申请区</h2>
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
                  applyForm.toLineId === line.id
                    ? 'border-primary-600 bg-primary-50'
                    : 'border-transparent hover:bg-industrial-50',
                ]"
              >
                <input
                  type="radio"
                  :value="line.id"
                  v-model="applyForm.toLineId"
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
                申请人 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="applyForm.applicant"
                type="text"
                class="input-industrial"
                placeholder="请输入申请人姓名"
                maxlength="32"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-industrial-700 mb-1">
                <FileText class="w-4 h-4 inline mr-1" />
                申请原因 <span class="text-red-500">*</span>
              </label>
              <textarea
                v-model="applyForm.reason"
                class="input-industrial h-24 resize-none"
                placeholder="请输入划转申请原因"
                maxlength="255"
              ></textarea>
            </div>
          </div>

          <div class="flex flex-col justify-center">
            <div class="card-industrial p-4 bg-industrial-50">
              <h3 class="font-semibold text-industrial-800 mb-3">申请信息确认</h3>
              <div class="space-y-2 text-sm">
                <div class="flex justify-between">
                  <span class="text-industrial-600">选中数量：</span>
                  <span class="font-mono font-medium">{{ selectedIds.length }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">目标产线：</span>
                  <span class="font-medium text-accent-600">
                    {{ applyForm.toLineId ? lineStore.getLineName(applyForm.toLineId) : '未选择' }}
                  </span>
                </div>
                <div class="flex justify-between">
                  <span class="text-industrial-600">申请人：</span>
                  <span class="font-medium">{{ applyForm.applicant || '未填写' }}</span>
                </div>
                <div class="pt-2 mt-2 border-t border-industrial-200">
                  <button
                    class="w-full btn-industrial-accent"
                    :disabled="submitting || selectedIds.length === 0 || !applyForm.toLineId || !applyForm.applicant || !applyForm.reason"
                    @click="handleSubmitApplication"
                  >
                    <Send class="w-4 h-4 inline mr-1" />
                    {{ submitting ? '提交中...' : '提交划转申请' }}
                  </button>
                  <p class="text-xs text-industrial-400 mt-2 text-center">
                    申请提交后需审批通过才会执行划转
                  </p>
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
          <h2 class="text-lg font-bold text-industrial-800">最近划转申请</h2>
        </div>
        <div class="space-y-3">
          <div
            v-for="app in recentApplications"
            :key="app.id"
            class="p-3 bg-industrial-50 rounded-industrial animate-stagger"
            :style="{ animationDelay: `${recentApplications.indexOf(app) * 50}ms` }"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="font-mono text-xs font-medium text-primary-700">
                {{ app.applicationNo }}
              </span>
              <span
                class="px-2 py-0.5 rounded text-xs font-medium"
                :class="statusMap[app.status].class"
              >
                {{ statusMap[app.status].text }}
              </span>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <span class="text-industrial-600 text-xs">目标产线</span>
              <span class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded text-xs">
                {{ app.toLineName }}
              </span>
              <span class="text-xs text-industrial-400">
                共 {{ app.totalCount ?? '-' }} 条
              </span>
            </div>
            <div class="mt-2 flex items-center justify-between text-xs text-industrial-500">
              <span>
                <Users class="w-3 h-3 inline mr-1" />
                {{ app.applicant }}
              </span>
              <span class="font-mono">{{ formatTime(app.applyTime) }}</span>
            </div>
          </div>
          <div
            v-if="recentApplications.length === 0"
            class="text-center py-8 text-industrial-400 text-sm"
          >
            暂无划转申请
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
