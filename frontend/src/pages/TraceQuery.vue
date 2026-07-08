<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useLineStore } from '@/stores/lines'
import { springApi, transferApi } from '@/api'
import type { SpringArchive, TransferRecord } from '@/types'
import {
  History,
  Search,
  Cog,
  Factory,
  Calendar,
  Users,
  FileText,
  ArrowRight,
  Clock,
  ChevronDown,
  ChevronUp,
  Map as MapIcon,
} from 'lucide-vue-next'

const lineStore = useLineStore()
const loading = ref(false)
const traceLoading = ref(false)
const springs = ref<SpringArchive[]>([])
const selectedSpringId = ref<number | null>(null)
const transferTrace = ref<TransferRecord[]>([])
const expandedLineIds = ref<number[]>([])

const searchForm = reactive({
  keyword: '',
  lineId: null as number | null,
})

const springsByLine = computed(() => {
  const map = new Map<number, SpringArchive[]>()
  lineStore.lines.forEach((line) => {
    const lineSprings = springs.value.filter((s) => s.currentLineId === line.id)
    if (lineSprings.length > 0 || !searchForm.lineId || searchForm.lineId === line.id) {
      map.set(line.id, lineSprings)
    }
  })
  return map
})

const selectedSpring = computed(() => {
  return springs.value.find((s) => s.id === selectedSpringId.value) || null
})

async function fetchSprings() {
  loading.value = true
  try {
    const response = await springApi.list({
      lineId: searchForm.lineId ?? undefined,
      keyword: searchForm.keyword || undefined,
      size: 200,
    })
    springs.value = response.data.content
  } finally {
    loading.value = false
  }
}

async function fetchTrace(springId: number) {
  selectedSpringId.value = springId
  traceLoading.value = true
  try {
    const response = await springApi.getTrace(springId)
    transferTrace.value = response.data
  } finally {
    traceLoading.value = false
  }
}

function handleSearch() {
  selectedSpringId.value = null
  transferTrace.value = []
  fetchSprings()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.lineId = null
  selectedSpringId.value = null
  transferTrace.value = []
  fetchSprings()
}

function toggleLine(lineId: number) {
  const index = expandedLineIds.value.indexOf(lineId)
  if (index > -1) {
    expandedLineIds.value.splice(index, 1)
  } else {
    expandedLineIds.value.push(lineId)
  }
}

function isLineExpanded(lineId: number) {
  return expandedLineIds.value.includes(lineId)
}

function formatTime(time: string) {
  return time.replace('T', ' ').substring(0, 19)
}

function getTimelineIndex(index: number) {
  return transferTrace.value.length - index
}

onMounted(async () => {
  await lineStore.fetchLines()
  lineStore.lines.forEach((line) => expandedLineIds.value.push(line.id))
  fetchSprings()
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
    <div class="lg:col-span-2 space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Factory class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">按产线汇总弹簧档案</h2>
        </div>
        <div class="flex flex-wrap items-center gap-4 mb-4">
          <div class="flex items-center gap-2">
            <Factory class="w-4 h-4 text-primary-600" />
            <span class="text-sm font-medium text-industrial-700">产线筛选：</span>
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
              placeholder="搜索弹簧编号或型号..."
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

        <div v-if="!loading" class="space-y-4">
          <div
            v-for="line in lineStore.lines"
            :key="line.id"
            v-show="springsByLine.has(line.id)"
            class="border border-industrial-200 rounded-industrial overflow-hidden"
          >
            <div
              class="flex items-center justify-between px-4 py-3 bg-industrial-100 cursor-pointer hover:bg-industrial-200 transition-colors"
              @click="toggleLine(line.id)"
            >
              <div class="flex items-center gap-3">
                <component
                  :is="isLineExpanded(line.id) ? ChevronUp : ChevronDown"
                  class="w-5 h-5 text-industrial-500"
                />
                <div class="w-3 h-3 rounded-full bg-accent-500"></div>
                <div>
                  <span class="font-semibold text-industrial-800">{{ line.lineName }}</span>
                  <span class="ml-2 text-xs text-industrial-500 font-mono">{{ line.lineCode }}</span>
                </div>
              </div>
              <span class="px-3 py-1 bg-primary-800 text-white rounded-full text-sm font-mono font-bold">
                {{ springsByLine.get(line.id)?.length || 0 }}
              </span>
            </div>

            <div v-show="isLineExpanded(line.id)" class="animate-fade-in">
              <table class="table-industrial text-sm">
                <thead>
                  <tr>
                    <th class="py-2 pl-8">弹簧编号</th>
                    <th class="py-2">型号</th>
                    <th class="py-2">弹力系数</th>
                    <th class="py-2">外径尺寸</th>
                    <th class="py-2">初始产线</th>
                    <th class="py-2 text-center">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="spring in springsByLine.get(line.id)"
                    :key="spring.id"
                    class="transition-colors"
                    :class="{ 'bg-accent-50': selectedSpringId === spring.id }"
                  >
                    <td class="py-2 pl-8 font-mono text-xs font-medium text-primary-800">
                      <Cog class="w-4 h-4 inline mr-1 text-primary-500" />
                      {{ spring.springCode }}
                    </td>
                    <td class="py-2">{{ spring.model }}</td>
                    <td class="py-2 font-mono text-xs text-accent-700 font-medium">
                      {{ spring.elasticCoefficient }} N/mm
                    </td>
                    <td class="py-2 font-mono text-xs">{{ spring.outerDiameter }} mm</td>
                    <td class="py-2">
                      <span class="px-2 py-0.5 bg-industrial-100 text-industrial-600 rounded text-xs">
                        {{ spring.initialLineName || lineStore.getLineName(spring.initialLineId) }}
                      </span>
                    </td>
                    <td class="py-2 text-center">
                      <button
                        class="text-primary-600 hover:text-primary-800 text-sm font-medium transition-colors"
                        @click="fetchTrace(spring.id)"
                      >
                        <History class="w-4 h-4 inline mr-1" />
                        查看轨迹
                      </button>
                    </td>
                  </tr>
                  <tr v-if="springsByLine.get(line.id)?.length === 0">
                    <td colspan="6" class="text-center py-6 text-industrial-400 text-sm">
                      该产线暂无弹簧档案
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-else class="text-center py-12 text-industrial-400">
          <div class="animate-pulse">加载中...</div>
        </div>
      </div>
    </div>

    <div class="space-y-6">
      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <History class="w-5 h-5 text-accent-600" />
          <h2 class="text-lg font-bold text-industrial-800">划转轨迹查询</h2>
        </div>

        <div v-if="selectedSpring" class="mb-4 p-4 bg-primary-50 rounded-industrial border-2 border-primary-200">
          <div class="flex items-center gap-2 mb-2">
            <Cog class="w-5 h-5 text-primary-700" />
            <span class="font-mono font-bold text-primary-800">{{ selectedSpring.springCode }}</span>
          </div>
          <div class="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span class="text-industrial-500">型号：</span>
              <span class="font-medium">{{ selectedSpring.model }}</span>
            </div>
            <div>
              <span class="text-industrial-500">弹力系数：</span>
              <span class="font-mono">{{ selectedSpring.elasticCoefficient }}</span>
            </div>
            <div>
              <span class="text-industrial-500">外径：</span>
              <span class="font-mono">{{ selectedSpring.outerDiameter }} mm</span>
            </div>
            <div>
              <span class="text-industrial-500">当前产线：</span>
              <span class="font-medium text-primary-700">{{ selectedSpring.currentLineName || lineStore.getLineName(selectedSpring.currentLineId) }}</span>
            </div>
          </div>
        </div>

        <div v-else class="mb-4 p-4 bg-industrial-50 rounded-industrial text-center text-industrial-400 text-sm">
          <MapIcon class="w-8 h-8 mx-auto mb-2 opacity-50" />
          <p>请从左侧列表选择弹簧查看轨迹</p>
        </div>

        <div v-if="selectedSpringId" class="relative">
          <div v-if="traceLoading" class="text-center py-8 text-industrial-400">
            <div class="animate-pulse">加载轨迹中...</div>
          </div>

          <div v-else-if="transferTrace.length > 0" class="space-y-0">
            <div
              v-for="(record, index) in transferTrace"
              :key="record.id"
              class="relative pl-8 pb-6 last:pb-0"
            >
              <div
                v-if="index < transferTrace.length - 1"
                class="absolute left-3 top-6 bottom-0 w-0.5 bg-industrial-200"
              ></div>

              <div
                class="absolute left-0 top-1 w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white"
                :class="index === 0 ? 'bg-accent-500' : 'bg-primary-600'"
              >
                {{ getTimelineIndex(index) }}
              </div>

              <div class="card-industrial p-4 ml-2">
                <div class="flex items-center justify-between mb-3">
                  <span class="text-xs font-mono text-industrial-500">
                    第 {{ getTimelineIndex(index) }} 次划转
                  </span>
                  <span class="text-xs text-industrial-400 font-mono">
                    <Clock class="w-3 h-3 inline mr-1" />
                    {{ formatTime(record.operateTime) }}
                  </span>
                </div>

                <div class="flex items-center gap-3 mb-3">
                  <div class="flex-1">
                    <div class="text-xs text-industrial-500 mb-1">转出</div>
                    <span class="px-3 py-1 bg-primary-100 text-primary-800 rounded text-sm font-medium">
                      {{ record.fromLineName }}
                    </span>
                  </div>
                  <ArrowRight class="w-5 h-5 text-accent-500 flex-shrink-0" />
                  <div class="flex-1 text-right">
                    <div class="text-xs text-industrial-500 mb-1">转入</div>
                    <span class="px-3 py-1 bg-accent-100 text-accent-800 rounded text-sm font-medium">
                      {{ record.toLineName }}
                    </span>
                  </div>
                </div>

                <div class="flex items-center gap-4 text-xs text-industrial-500 pt-2 border-t border-industrial-100">
                  <span>
                    <Users class="w-3 h-3 inline mr-1" />
                    {{ record.operator }}
                  </span>
                  <span v-if="record.remark" class="flex-1 truncate">
                    <FileText class="w-3 h-3 inline mr-1" />
                    {{ record.remark }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="text-center py-8 text-industrial-400">
            <Calendar class="w-10 h-10 mx-auto mb-2 opacity-50" />
            <p class="text-sm">该弹簧暂无划转记录</p>
            <p class="text-xs mt-1">自建档后未发生过产线变更</p>
          </div>
        </div>
      </div>

      <div class="card-industrial p-4">
        <div class="flex items-center gap-2 mb-4">
          <Factory class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">产线统计</h2>
        </div>
        <div class="space-y-3">
          <div
            v-for="line in lineStore.lines"
            :key="line.id"
            class="flex items-center justify-between p-3 bg-industrial-50 rounded-industrial"
          >
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-industrial bg-primary-100 flex items-center justify-center">
                <span class="font-mono font-bold text-primary-700">{{ line.lineCode.split('-')[1] }}</span>
              </div>
              <div>
                <div class="font-medium text-industrial-800 text-sm">{{ line.lineName }}</div>
                <div class="text-xs text-industrial-400">{{ line.description }}</div>
              </div>
            </div>
            <div class="text-right">
              <div class="font-mono text-2xl font-bold text-primary-800">
                {{ springs.filter(s => s.currentLineId === line.id).length }}
              </div>
              <div class="text-xs text-industrial-400">件弹簧</div>
            </div>
          </div>
        </div>
        <div class="mt-4 pt-4 border-t border-industrial-200 flex justify-between items-center">
          <span class="text-industrial-600 font-medium">弹簧总数</span>
          <span class="font-mono text-3xl font-bold text-accent-600">{{ springs.length }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
