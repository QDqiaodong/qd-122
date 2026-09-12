<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useLineStore } from '@/stores/lines'
import { elasticSampleApi } from '@/api'
import type { ElasticSample, SampleStatus } from '@/types'
import SampleHandleModal from '@/components/SampleHandleModal.vue'
import {
  FlaskConical,
  Search,
  Factory,
  ClipboardPlus,
  ClipboardCheck,
  Ruler,
  Lock,
  TriangleAlert,
  Calendar,
  Cog,
  UserRound,
} from 'lucide-vue-next'

const router = useRouter()
const lineStore = useLineStore()
const loading = ref(false)
const samples = ref<ElasticSample[]>([])

/** 闭环状态筛选持久化：刷新后筛选条件仍在（待闭环/已闭环） */
const STATUS_FILTER_KEY = 'elastic-sample-status-filter'

const searchForm = reactive({
  status: (localStorage.getItem(STATUS_FILTER_KEY) || 'OPEN') as SampleStatus | '',
  lineId: null as number | null,
  deviated: '' as '' | 'true' | 'false',
  keyword: '',
})

const pagination = reactive({ page: 0, size: 20, total: 0 })

const stats = computed(() => ({
  open: samples.value.filter((s) => s.status === 'OPEN').length,
  openDeviation: samples.value.filter((s) => s.status === 'OPEN' && s.deviated).length,
  closed: samples.value.filter((s) => s.status === 'CLOSED').length,
}))

// ---------- 处置弹窗（改实测系数 / 闭环） ----------
const handleVisible = ref(false)
const handleMode = ref<'measured' | 'close'>('close')
const handleTarget = ref<ElasticSample | null>(null)

async function fetchSamples() {
  loading.value = true
  try {
    const response = await elasticSampleApi.list({
      status: searchForm.status || undefined,
      lineId: searchForm.lineId ?? undefined,
      deviated: searchForm.deviated === '' ? undefined : searchForm.deviated === 'true',
      keyword: searchForm.keyword || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    samples.value = response.data.content
    pagination.total = response.data.totalElements
  } finally {
    loading.value = false
  }
}

/** 留样按弹簧登记：跳转档案页，在弹簧行点「留样登记」带入弹簧 */
function goRegister() {
  router.push({ name: 'SpringArchive' })
}

function handleSearch() {
  pagination.page = 0
  localStorage.setItem(STATUS_FILTER_KEY, searchForm.status)
  fetchSamples()
}

function handleReset() {
  searchForm.status = ''
  searchForm.lineId = null
  searchForm.deviated = ''
  searchForm.keyword = ''
  pagination.page = 0
  localStorage.setItem(STATUS_FILTER_KEY, '')
  fetchSamples()
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  fetchSamples()
}

function openClose(sample: ElasticSample) {
  handleTarget.value = sample
  handleMode.value = 'close'
  handleVisible.value = true
}

function openMeasured(sample: ElasticSample) {
  handleTarget.value = sample
  handleMode.value = 'measured'
  handleVisible.value = true
}

function handleSaved() {
  fetchSamples()
}

function rangeText(sample: ElasticSample) {
  return `${sample.lineElasticMin ?? '不限'} ~ ${sample.lineElasticMax ?? '不限'}`
}

function formatTime(time?: string | null) {
  return time ? time.replace('T', ' ').substring(0, 19) : '-'
}

onMounted(async () => {
  await lineStore.fetchLines()
  fetchSamples()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 顶部说明 + 登记入口 -->
    <div class="card-industrial p-4 border-l-4 border-amber-500">
      <div class="flex items-start gap-3">
        <TriangleAlert class="w-6 h-6 text-amber-600 flex-shrink-0 mt-0.5" />
        <div class="flex-1">
          <div class="font-bold text-industrial-800">弹力抽检留样管理（质量员按产线登记）</div>
          <p class="text-sm text-industrial-600 mt-1">
            登记留样编号与实测弹力系数，系统自动比对该线适用区间；偏离件在弹簧档案挂
            <span class="px-1 bg-amber-100 text-amber-700 rounded font-medium">黄标</span>，
            未闭环前不能勾进划转申请；处置结论填写后方可闭环，已闭环单实测系数不可修改。
            偏离留样闭环后黄标不自动摘除，须质量主管在弹簧档案页点「摘标加签」（填工号与加签说明），
            加签完成后才恢复可勾选划转；加签记录持久化，刷新后仍在。
          </p>
        </div>
        <button class="btn-industrial-accent whitespace-nowrap" @click="goRegister">
          <ClipboardPlus class="w-4 h-4 inline mr-1" />
          登记留样（选弹簧）
        </button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="card-industrial p-4">
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <ClipboardCheck class="w-5 h-5 text-primary-600" />
          <span class="text-sm font-medium text-industrial-700">闭环状态：</span>
          <select v-model="searchForm.status" class="input-industrial w-36" @change="handleSearch">
            <option value="">全部</option>
            <option value="OPEN">待闭环</option>
            <option value="CLOSED">已闭环</option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <Factory class="w-4 h-4 text-primary-600" />
          <span class="text-sm font-medium text-industrial-700">登记产线：</span>
          <select v-model="searchForm.lineId" class="input-industrial w-44" @change="handleSearch">
            <option :value="null">全部产线</option>
            <option v-for="line in lineStore.lines" :key="line.id" :value="line.id">
              {{ line.lineName }}
            </option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <TriangleAlert class="w-4 h-4 text-amber-500" />
          <span class="text-sm font-medium text-industrial-700">是否偏离：</span>
          <select v-model="searchForm.deviated" class="input-industrial w-32" @change="handleSearch">
            <option value="">全部</option>
            <option value="true">偏离</option>
            <option value="false">在区间内</option>
          </select>
        </div>
        <div class="flex items-center gap-2 flex-1 max-w-md">
          <Search class="w-5 h-5 text-industrial-400" />
          <input
            v-model="searchForm.keyword"
            type="text"
            class="input-industrial flex-1"
            placeholder="搜索留样编号 / 弹簧编号..."
            @keyup.enter="handleSearch"
          />
          <button class="btn-industrial" @click="handleSearch">搜索</button>
          <button class="btn-industrial-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 统计卡 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
      <div class="card-industrial p-4">
        <div class="text-xs text-industrial-500 mb-1">本页待闭环留样</div>
        <div class="text-2xl font-bold font-mono text-primary-800">{{ stats.open }}</div>
      </div>
      <div class="card-industrial p-4 border-l-4 border-amber-400">
        <div class="text-xs text-amber-600 mb-1">其中偏离待闭环（黄标件）</div>
        <div class="text-2xl font-bold font-mono text-amber-600">{{ stats.openDeviation }}</div>
      </div>
      <div class="card-industrial p-4">
        <div class="text-xs text-industrial-500 mb-1">本页已闭环</div>
        <div class="text-2xl font-bold font-mono text-green-700">{{ stats.closed }}</div>
      </div>
    </div>

    <!-- 留样单列表 -->
    <div class="card-industrial overflow-hidden">
      <div class="overflow-x-auto">
        <table class="table-industrial">
          <thead>
            <tr>
              <th>留样编号</th>
              <th>弹簧编号</th>
              <th>登记产线（快照）</th>
              <th>实测系数 (N/mm)</th>
              <th>适用区间</th>
              <th>是否偏离</th>
              <th>状态</th>
              <th>登记信息</th>
              <th>闭环信息</th>
              <th class="w-40">操作</th>
            </tr>
          </thead>
          <tbody v-if="!loading && samples.length > 0">
            <tr
              v-for="(sample, index) in samples"
              :key="sample.id"
              class="animate-stagger"
              :class="{ 'bg-amber-50/60': sample.status === 'OPEN' && sample.deviated }"
              :style="{ animationDelay: `${index * 30}ms` }"
            >
              <td class="font-mono text-xs font-medium text-primary-800">
                {{ sample.sampleNo }}
              </td>
              <td class="font-mono text-sm">
                <Cog class="w-4 h-4 inline mr-1 text-primary-500" />
                {{ sample.springCode }}
                <div class="text-xs text-industrial-400">{{ sample.model }}</div>
              </td>
              <td>
                <span class="px-2 py-1 bg-primary-100 text-primary-800 rounded text-xs font-medium">
                  {{ sample.lineName }}
                </span>
                <div class="text-xs text-industrial-400 font-mono mt-1">{{ sample.lineCode }}</div>
              </td>
              <td class="font-mono text-sm">
                <Ruler class="w-4 h-4 inline mr-1" :class="sample.deviated ? 'text-amber-500' : 'text-accent-500'" />
                <span :class="sample.deviated ? 'text-amber-700 font-semibold' : 'text-industrial-700'">
                  {{ sample.measuredCoefficient }}
                </span>
              </td>
              <td class="font-mono text-xs text-industrial-500">{{ rangeText(sample) }}</td>
              <td>
                <span
                  v-if="sample.deviated"
                  class="px-2 py-1 bg-amber-100 text-amber-700 rounded text-xs font-medium cursor-help"
                  :title="`实测 ${sample.measuredCoefficient} 偏离 ${sample.lineName} 适用区间 ${rangeText(sample)}`"
                >
                  <TriangleAlert class="w-3 h-3 inline mr-1" />
                  偏离
                </span>
                <span v-else class="px-2 py-1 bg-green-100 text-green-700 rounded text-xs">在区间内</span>
              </td>
              <td>
                <span
                  v-if="sample.status === 'OPEN'"
                  class="px-2 py-1 rounded text-xs font-medium"
                  :class="sample.deviated ? 'bg-amber-100 text-amber-700' : 'bg-blue-100 text-blue-700'"
                >
                  {{ sample.deviated ? '待闭环 · 黄标' : '待闭环' }}
                </span>
                <span v-else class="px-2 py-1 bg-green-100 text-green-700 rounded text-xs">已闭环</span>
              </td>
              <td class="text-xs text-industrial-500">
                <div class="flex items-center gap-1"><UserRound class="w-3 h-3" />{{ sample.operator }}</div>
                <div class="flex items-center gap-1 mt-1 font-mono">
                  <Calendar class="w-3 h-3" />{{ formatTime(sample.createTime) }}
                </div>
              </td>
              <td class="text-xs text-industrial-500">
                <template v-if="sample.status === 'CLOSED'">
                  <div class="flex items-center gap-1"><UserRound class="w-3 h-3" />{{ sample.closeOperator }}</div>
                  <div class="font-mono mt-1">{{ formatTime(sample.closeTime) }}</div>
                  <div
                    class="mt-1 text-industrial-600 max-w-48 truncate cursor-help"
                    :title="sample.conclusion || ''"
                  >
                    {{ sample.conclusion }}
                  </div>
                </template>
                <span v-else class="text-industrial-400">-</span>
              </td>
              <td>
                <div class="flex flex-col gap-1">
                  <button
                    v-if="sample.status === 'OPEN'"
                    class="px-2 py-1 text-xs rounded border border-amber-300 text-amber-700 hover:bg-amber-50 transition-colors whitespace-nowrap"
                    @click="openClose(sample)"
                  >
                    <Lock class="w-3 h-3 inline mr-1" />
                    闭环
                  </button>
                  <button
                    v-if="sample.status === 'OPEN'"
                    class="px-2 py-1 text-xs rounded border border-primary-300 text-primary-700 hover:bg-primary-50 transition-colors whitespace-nowrap"
                    @click="openMeasured(sample)"
                  >
                    <Ruler class="w-3 h-3 inline mr-1" />
                    改实测系数
                  </button>
                  <span
                    v-else
                    class="px-2 py-1 text-xs text-industrial-400 cursor-help whitespace-nowrap"
                    title="已闭环单不能修改实测系数"
                  >
                    <Lock class="w-3 h-3 inline mr-1" />
                    系数已锁定
                  </span>
                </div>
              </td>
            </tr>
          </tbody>
          <tbody v-else-if="!loading">
            <tr>
              <td colspan="10" class="text-center py-16 text-industrial-400">
                <FlaskConical class="w-16 h-16 mx-auto mb-4 opacity-30" />
                <p class="text-lg">暂无留样单</p>
                <p class="text-sm mt-2">请前往「弹簧档案管理」，在弹簧行点击「留样登记」按产线登记抽检结果</p>
              </td>
            </tr>
          </tbody>
          <tbody v-else>
            <tr>
              <td colspan="10" class="text-center py-16 text-industrial-400">
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
            <span class="font-mono font-medium">{{ Math.max(1, Math.ceil(pagination.total / pagination.size)) }}</span> 页
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

    <SampleHandleModal
      v-if="handleVisible"
      v-model:visible="handleVisible"
      :sample="handleTarget"
      :mode="handleMode"
      @saved="handleSaved"
    />
  </div>
</template>
