<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { lineLoadApi } from '@/api'
import type { LineLoadBoard, LineLoadStats, LineLoadDetail, SpringArchive, TransferRecord } from '@/types'
import LineThresholdModal from '@/components/LineThresholdModal.vue'
import {
  Gauge,
  RefreshCw,
  AlertTriangle,
  OctagonAlert,
  CheckCircle2,
  Factory,
  Cog,
  Ruler,
  ArrowRightToLine,
  ArrowLeftFromLine,
  TrendingUp,
  Settings2,
  X,
  History,
} from 'lucide-vue-next'

const loading = ref(false)
const board = ref<LineLoadBoard | null>(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<LineLoadDetail | null>(null)
const selectedLine = ref<LineLoadStats | null>(null)

const thresholdVisible = ref(false)

const totalLines = computed(() => board.value?.totalLines ?? 0)
const totalSprings = computed(() => board.value?.totalSprings ?? 0)

// 抽屉内状态：明细加载后以最新计算结果为准，加载中回退到看板卡片状态
const drawerStatus = computed<LineLoadStats['status']>(
  () => detail.value?.stats.status ?? selectedLine.value?.status ?? 'NORMAL'
)

async function fetchBoard() {
  loading.value = true
  try {
    const response = await lineLoadApi.board()
    // 统计数字与下方分组来自后端同一份响应，保证刷新后一致
    board.value = response.data
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '看板加载失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(line: LineLoadStats) {
  selectedLine.value = line
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response = await lineLoadApi.detail(line.lineId)
    detail.value = response.data
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '明细加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openThreshold(line: LineLoadStats) {
  selectedLine.value = line
  thresholdVisible.value = true
}

async function handleThresholdSaved() {
  await fetchBoard()
  // 若明细抽屉处于打开状态，同步刷新抽屉内的统计与原因
  if (detailVisible.value && selectedLine.value) {
    const lineId = selectedLine.value.lineId
    const refreshed = board.value?.lines.find((l) => l.lineId === lineId)
    if (refreshed) {
      selectedLine.value = refreshed
      openDetail(refreshed)
    }
  }
}

function isOutOfRange(spring: SpringArchive, line: LineLoadStats): boolean {
  const k = Number(spring.elasticCoefficient)
  const below = line.elasticMin != null && k < Number(line.elasticMin)
  const above = line.elasticMax != null && k > Number(line.elasticMax)
  return below || above
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

function trendDirection(record: TransferRecord, lineId: number) {
  if (record.toLineId === lineId) return 'in'
  if (record.fromLineId === lineId) return 'out'
  return 'other'
}

onMounted(fetchBoard)
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 顶部统计：与下方分组同源 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-industrial-600">
        <Gauge class="w-5 h-5 text-primary-700" />
        <span class="text-sm">按归属数量、弹力系数区间与近 7 天划转趋势实时评估</span>
      </div>
      <button class="btn-industrial" :disabled="loading" @click="fetchBoard">
        <RefreshCw class="w-4 h-4 inline mr-1" :class="{ 'animate-spin': loading }" />
        刷新
      </button>
    </div>

    <div class="grid grid-cols-2 lg:grid-cols-5 gap-4">
      <div class="card-industrial p-4">
        <div class="flex items-center justify-between">
          <span class="text-sm text-industrial-500">产线总数</span>
          <Factory class="w-5 h-5 text-primary-600" />
        </div>
        <div class="text-3xl font-bold font-mono text-primary-800 mt-2">{{ totalLines }}</div>
        <div class="text-xs text-industrial-400 mt-1">在管弹簧 {{ totalSprings }} 件</div>
      </div>

      <div class="card-industrial p-4 border-l-4 border-l-green-500">
        <div class="flex items-center justify-between">
          <span class="text-sm text-industrial-500">正常</span>
          <CheckCircle2 class="w-5 h-5 text-green-500" />
        </div>
        <div class="text-3xl font-bold font-mono text-green-600 mt-2">
          {{ board?.normalCount ?? '-' }}
        </div>
        <div class="text-xs text-industrial-400 mt-1">负载在安全范围</div>
      </div>

      <div class="card-industrial p-4 border-l-4 border-l-accent-500">
        <div class="flex items-center justify-between">
          <span class="text-sm text-industrial-500">预警</span>
          <AlertTriangle class="w-5 h-5 text-accent-500" />
        </div>
        <div class="text-3xl font-bold font-mono text-accent-600 mt-2">
          {{ board?.warningCount ?? '-' }}
        </div>
        <div class="text-xs text-industrial-400 mt-1">接近阈值或存在异常</div>
      </div>

      <div class="card-industrial p-4 border-l-4 border-l-red-500">
        <div class="flex items-center justify-between">
          <span class="text-sm text-industrial-500">超载</span>
          <OctagonAlert class="w-5 h-5 text-red-500" />
        </div>
        <div class="text-3xl font-bold font-mono text-red-600 mt-2">
          {{ board?.overloadCount ?? '-' }}
        </div>
        <div class="text-xs text-industrial-400 mt-1">已超过日承载阈值</div>
      </div>

      <div class="card-industrial p-4 col-span-2 lg:col-span-1">
        <div class="flex items-center justify-between">
          <span class="text-sm text-industrial-500">预警/超载占比</span>
          <TrendingUp class="w-5 h-5 text-industrial-400" />
        </div>
        <div class="text-3xl font-bold font-mono text-industrial-800 mt-2">
          {{
            totalLines > 0
              ? Math.round(((board?.warningCount ?? 0) + (board?.overloadCount ?? 0)) / totalLines * 100)
              : 0
          }}%
        </div>
        <div class="text-xs text-industrial-400 mt-1">需重点关注产线比例</div>
      </div>
    </div>

    <!-- 加载/空态 -->
    <div v-if="loading && !board" class="card-industrial p-16 text-center text-industrial-400">
      <RefreshCw class="w-8 h-8 mx-auto mb-3 animate-spin" />
      正在计算各产线负载状态...
    </div>
    <div v-else-if="board && totalLines === 0" class="card-industrial p-16 text-center text-industrial-400">
      <Factory class="w-16 h-16 mx-auto mb-4 opacity-30" />
      <p class="text-lg">暂无产线数据</p>
    </div>

    <!-- 状态分组 -->
    <template v-else-if="board">
      <section
        v-for="group in [
          { key: 'OVERLOAD', title: '超载产线', icon: OctagonAlert, list: board.overloadLines, tone: 'red' },
          { key: 'WARNING', title: '预警产线', icon: AlertTriangle, list: board.warningLines, tone: 'amber' },
          { key: 'NORMAL', title: '正常产线', icon: CheckCircle2, list: board.normalLines, tone: 'green' },
        ]"
        :key="group.key"
        class="space-y-3"
      >
        <div class="flex items-center gap-2">
          <component
            :is="group.icon"
            class="w-5 h-5"
            :class="{
              'text-red-500': group.tone === 'red',
              'text-accent-500': group.tone === 'amber',
              'text-green-500': group.tone === 'green',
            }"
          />
          <h2 class="font-bold text-industrial-800">{{ group.title }}</h2>
          <span
            class="px-2 py-0.5 rounded-full text-xs font-mono font-medium"
            :class="{
              'bg-red-100 text-red-700': group.tone === 'red',
              'bg-accent-100 text-accent-700': group.tone === 'amber',
              'bg-green-100 text-green-700': group.tone === 'green',
            }"
          >
            {{ group.list.length }}
          </span>
          <div class="flex-1 h-px bg-industrial-200 ml-2"></div>
        </div>

        <div v-if="group.list.length === 0" class="card-industrial p-4 text-sm text-industrial-400">
          暂无{{ group.title }}
        </div>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          <div
            v-for="line in group.list"
            :key="line.lineId"
            class="card-industrial p-4 transition-all duration-200 hover:shadow-industrial-hover cursor-pointer flex flex-col"
            :class="{
              'border-l-4 border-l-red-500': group.tone === 'red',
              'border-l-4 border-l-accent-500': group.tone === 'amber',
              'border-l-4 border-l-green-500': group.tone === 'green',
            }"
            @click="openDetail(line)"
          >
            <div class="flex items-start justify-between">
              <div>
                <div class="font-mono text-xs text-industrial-400">{{ line.lineCode }}</div>
                <div class="font-semibold text-industrial-800 mt-0.5">{{ line.lineName }}</div>
              </div>
              <button
                class="p-1.5 text-industrial-400 hover:text-primary-700 hover:bg-primary-50 rounded-industrial"
                title="维护阈值"
                @click.stop="openThreshold(line)"
              >
                <Settings2 class="w-4 h-4" />
              </button>
            </div>

            <!-- 负载率进度条 -->
            <div class="mt-3">
              <div class="flex items-baseline justify-between text-sm">
                <span class="text-industrial-600">
                  当前 <span class="font-mono font-semibold text-industrial-800">{{ line.springCount }}</span> /
                  阈值 <span class="font-mono">{{ line.dailyCapacityThreshold ?? '未配置' }}</span>
                </span>
                <span
                  class="font-mono font-semibold"
                  :class="{
                    'text-red-600': group.tone === 'red',
                    'text-accent-600': group.tone === 'amber',
                    'text-green-600': group.tone === 'green',
                  }"
                >
                  {{ line.loadRate != null ? line.loadRate + '%' : '-' }}
                </span>
              </div>
              <div class="mt-1 h-2 bg-industrial-100 rounded-full overflow-hidden">
                <div
                  class="h-full rounded-full transition-all duration-500"
                  :class="{
                    'bg-red-500': group.tone === 'red',
                    'bg-accent-500': group.tone === 'amber',
                    'bg-green-500': group.tone === 'green',
                  }"
                  :style="{ width: Math.min(line.loadRate ?? 0, 100) + '%' }"
                ></div>
              </div>
            </div>

            <!-- 趋势与系数指标 -->
            <div class="mt-3 flex items-center gap-3 text-xs text-industrial-500">
              <span class="flex items-center gap-1">
                <ArrowRightToLine class="w-3.5 h-3.5 text-green-500" />
                近{{ line.trendDays }}天划入 {{ line.recentInCount }}
              </span>
              <span class="flex items-center gap-1">
                <ArrowLeftFromLine class="w-3.5 h-3.5 text-industrial-400" />
                划出 {{ line.recentOutCount }}
              </span>
              <span
                v-if="line.outOfRangeCount > 0"
                class="ml-auto px-1.5 py-0.5 bg-accent-100 text-accent-700 rounded font-medium"
              >
                系数超区间 {{ line.outOfRangeCount }}
              </span>
            </div>

            <!-- 触发原因预览 -->
            <div v-if="line.reasons.length > 0" class="mt-3 pt-3 border-t border-industrial-200 space-y-1">
              <div
                v-for="(reason, idx) in line.reasons.slice(0, 2)"
                :key="idx"
                class="flex items-start gap-1.5 text-xs text-industrial-600"
              >
                <AlertTriangle
                  class="w-3.5 h-3.5 flex-shrink-0 mt-0.5"
                  :class="group.tone === 'red' ? 'text-red-500' : 'text-accent-500'"
                />
                <span>{{ reason }}</span>
              </div>
              <div v-if="line.reasons.length > 2" class="text-xs text-primary-700 pl-5">
                等 {{ line.reasons.length }} 条原因，点击查看全部
              </div>
            </div>
            <div v-else class="mt-3 pt-3 border-t border-industrial-200 text-xs text-green-600 flex items-center gap-1.5">
              <CheckCircle2 class="w-3.5 h-3.5" />
              各项指标正常
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- 产线明细抽屉 -->
    <el-drawer v-model="detailVisible" size="60%" :with-header="false">
      <div v-if="selectedLine" class="h-full flex flex-col">
        <div class="flex items-start justify-between px-6 py-4 border-b-2 border-primary-800 bg-white">
          <div>
            <div class="flex items-center gap-3">
              <span class="font-mono text-xs text-industrial-400">{{ selectedLine.lineCode }}</span>
              <h2 class="text-lg font-bold text-primary-800">{{ selectedLine.lineName }}</h2>
              <span
                class="px-2 py-0.5 rounded-full text-xs font-medium"
                :class="{
                  'bg-red-100 text-red-700': drawerStatus === 'OVERLOAD',
                  'bg-accent-100 text-accent-700': drawerStatus === 'WARNING',
                  'bg-green-100 text-green-700': drawerStatus === 'NORMAL',
                }"
              >
                {{ drawerStatus === 'OVERLOAD' ? '超载' : drawerStatus === 'WARNING' ? '预警' : '正常' }}
              </span>
            </div>
            <p class="text-sm text-industrial-500 mt-1">{{ selectedLine.description || '暂无描述' }}</p>
          </div>
          <div class="flex items-center gap-2">
            <button class="btn-industrial-outline" @click="openThreshold(selectedLine)">
              <Settings2 class="w-4 h-4 inline mr-1" />
              维护阈值
            </button>
            <button class="p-2 hover:bg-industrial-100 rounded-industrial" @click="detailVisible = false">
              <X class="w-5 h-5 text-industrial-500" />
            </button>
          </div>
        </div>

        <div class="flex-1 overflow-auto p-6 bg-industrial-50 space-y-6">
          <div v-if="detailLoading" class="text-center py-16 text-industrial-400">
            <RefreshCw class="w-8 h-8 mx-auto mb-3 animate-spin" />
            加载产线明细...
          </div>

          <template v-else-if="detail">
            <!-- 指标概览 -->
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
              <div class="card-industrial p-3">
                <div class="text-xs text-industrial-500">当前归属 / 日阈值</div>
                <div class="font-mono text-xl font-bold text-industrial-800 mt-1">
                  {{ detail.stats.springCount }} / {{ detail.stats.dailyCapacityThreshold ?? '未配置' }}
                </div>
              </div>
              <div class="card-industrial p-3">
                <div class="text-xs text-industrial-500">数量负载率</div>
                <div
                  class="font-mono text-xl font-bold mt-1"
                  :class="{
                    'text-red-600': detail.stats.loadRate != null && detail.stats.loadRate > 100,
                    'text-accent-600': detail.stats.loadRate != null && detail.stats.loadRate >= 80 && detail.stats.loadRate <= 100,
                    'text-green-600': detail.stats.loadRate != null && detail.stats.loadRate < 80,
                  }"
                >
                  {{ detail.stats.loadRate != null ? detail.stats.loadRate + '%' : '-' }}
                </div>
              </div>
              <div class="card-industrial p-3">
                <div class="text-xs text-industrial-500">系数超区间件数</div>
                <div
                  class="font-mono text-xl font-bold mt-1"
                  :class="detail.stats.outOfRangeCount > 0 ? 'text-accent-600' : 'text-green-600'"
                >
                  {{ detail.stats.outOfRangeCount }}
                </div>
                <div class="text-xs text-industrial-400">
                  适用区间 {{ detail.stats.elasticMin ?? 0 }} ~ {{ detail.stats.elasticMax ?? '∞' }}
                </div>
              </div>
              <div class="card-industrial p-3">
                <div class="text-xs text-industrial-500">近{{ detail.stats.trendDays }}天净流入</div>
                <div
                  class="font-mono text-xl font-bold mt-1"
                  :class="detail.stats.recentNetIn > 0 ? 'text-accent-600' : 'text-green-600'"
                >
                  {{ detail.stats.recentNetIn > 0 ? '+' : '' }}{{ detail.stats.recentNetIn }}
                </div>
                <div class="text-xs text-industrial-400">
                  划入 {{ detail.stats.recentInCount }} / 划出 {{ detail.stats.recentOutCount }}
                </div>
              </div>
            </div>

            <!-- 触发预警原因 -->
            <div class="card-industrial p-4">
              <div class="flex items-center gap-2 mb-3">
                <AlertTriangle class="w-4 h-4 text-accent-500" />
                <h3 class="font-semibold text-industrial-800">触发预警原因</h3>
              </div>
              <ul v-if="detail.stats.reasons.length > 0" class="space-y-2">
                <li
                  v-for="(reason, idx) in detail.stats.reasons"
                  :key="idx"
                  class="flex items-start gap-2 text-sm text-industrial-700 bg-accent-50 border border-accent-200 rounded-industrial px-3 py-2"
                >
                  <span class="font-mono text-xs text-accent-600 mt-0.5">{{ idx + 1 }}.</span>
                  <span>{{ reason }}</span>
                </li>
              </ul>
              <div v-else class="flex items-center gap-2 text-sm text-green-600">
                <CheckCircle2 class="w-4 h-4" />
                暂无触发原因，该产线负载状态正常
              </div>
            </div>

            <!-- 当前归属弹簧明细 -->
            <div class="card-industrial overflow-hidden">
              <div class="flex items-center gap-2 px-4 py-3 border-b border-industrial-200">
                <Cog class="w-4 h-4 text-primary-700" />
                <h3 class="font-semibold text-industrial-800">当前归属弹簧明细</h3>
                <span class="ml-auto text-xs text-industrial-400 font-mono">
                  共 {{ detail.springs.length }} 件
                </span>
              </div>
              <div class="overflow-x-auto">
                <table class="table-industrial">
                  <thead>
                    <tr>
                      <th>弹簧编号</th>
                      <th>型号</th>
                      <th>弹力系数 (N/mm)</th>
                      <th>外径 (mm)</th>
                      <th>系数校验</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="spring in detail.springs" :key="spring.id">
                      <td class="font-mono text-sm font-medium text-primary-800">{{ spring.springCode }}</td>
                      <td class="text-industrial-700">{{ spring.model }}</td>
                      <td class="font-mono text-sm">
                        <Ruler class="w-4 h-4 inline mr-1 text-accent-500" />
                        {{ spring.elasticCoefficient }}
                      </td>
                      <td class="font-mono text-sm text-industrial-600">{{ spring.outerDiameter }}</td>
                      <td>
                        <span
                          v-if="isOutOfRange(spring, detail.stats)"
                          class="px-2 py-0.5 bg-accent-100 text-accent-700 rounded text-xs font-medium"
                        >
                          超出适用区间
                        </span>
                        <span v-else class="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs">
                          区间内
                        </span>
                      </td>
                    </tr>
                    <tr v-if="detail.springs.length === 0">
                      <td colspan="5" class="text-center py-10 text-industrial-400">
                        该产线当前无归属弹簧
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 近期划转趋势 -->
            <div class="card-industrial p-4">
              <div class="flex items-center gap-2 mb-3">
                <History class="w-4 h-4 text-primary-700" />
                <h3 class="font-semibold text-industrial-800">
                  近{{ detail.stats.trendDays }}天划转趋势
                </h3>
              </div>
              <ul v-if="detail.recentTransfers.length > 0" class="space-y-2">
                <li
                  v-for="record in detail.recentTransfers.slice(0, 10)"
                  :key="record.id"
                  class="flex items-center gap-3 text-sm bg-industrial-50 rounded-industrial px-3 py-2"
                >
                  <span
                    class="flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium flex-shrink-0"
                    :class="trendDirection(record, detail.stats.lineId) === 'in'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-industrial-200 text-industrial-700'"
                  >
                    <component
                      :is="trendDirection(record, detail.stats.lineId) === 'in' ? ArrowRightToLine : ArrowLeftFromLine"
                      class="w-3.5 h-3.5"
                    />
                    {{ trendDirection(record, detail.stats.lineId) === 'in' ? '划入' : '划出' }}
                  </span>
                  <span class="font-mono text-primary-800 font-medium">{{ record.springCode }}</span>
                  <span class="text-industrial-500 text-xs">
                    {{ record.fromLineName }} → {{ record.toLineName }}
                  </span>
                  <span class="ml-auto text-xs text-industrial-400 font-mono">
                    {{ formatTime(record.operateTime) }}
                  </span>
                </li>
              </ul>
              <p v-else class="text-sm text-industrial-400">近{{ detail.stats.trendDays }}天无划转记录</p>
            </div>
          </template>
        </div>
      </div>
    </el-drawer>

    <LineThresholdModal
      v-model:visible="thresholdVisible"
      :line="selectedLine"
      @saved="handleThresholdSaved"
    />
  </div>
</template>
