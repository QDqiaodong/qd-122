<script setup lang="ts">
import type { SimulationEstimate, LineLoadStatus } from '@/types'
import {
  ArrowRight,
  ArrowDownToLine,
  ArrowUpFromLine,
  AlertTriangle,
  OctagonAlert,
  CheckCircle2,
} from 'lucide-vue-next'

defineProps<{
  estimate: SimulationEstimate
}>()

const loadStatusMap: Record<LineLoadStatus, { text: string; class: string }> = {
  NORMAL: { text: '正常', class: 'bg-green-100 text-green-700' },
  WARNING: { text: '预警', class: 'bg-amber-100 text-amber-700' },
  OVERLOAD: { text: '超载', class: 'bg-red-100 text-red-700' },
}

function formatRate(rate?: number | null) {
  return rate === null || rate === undefined ? '-' : `${rate}%`
}

function statusIcon(status: LineLoadStatus) {
  if (status === 'OVERLOAD') return OctagonAlert
  if (status === 'WARNING') return AlertTriangle
  return CheckCircle2
}

function reasonClass(status: LineLoadStatus) {
  if (status === 'OVERLOAD') return 'text-red-600'
  if (status === 'WARNING') return 'text-amber-600'
  return 'text-green-600'
}
</script>

<template>
  <div class="space-y-3">
    <div
      v-for="line in estimate.lines"
      :key="line.lineId"
      class="border rounded-industrial p-4 transition-colors"
      :class="line.direction === 'IN' ? 'border-accent-300 bg-accent-50/50' : 'border-industrial-200 bg-industrial-50/50'"
    >
      <div class="flex items-center gap-2 mb-3">
        <component
          :is="line.direction === 'IN' ? ArrowDownToLine : ArrowUpFromLine"
          class="w-4 h-4"
          :class="line.direction === 'IN' ? 'text-accent-600' : 'text-primary-600'"
        />
        <span class="font-semibold text-industrial-800">{{ line.lineName }}</span>
        <span class="font-mono text-xs text-industrial-400">{{ line.lineCode }}</span>
        <span
          class="px-2 py-0.5 rounded text-xs font-medium"
          :class="line.direction === 'IN' ? 'bg-accent-100 text-accent-700' : 'bg-primary-100 text-primary-700'"
        >
          {{ line.direction === 'IN' ? `划入 +${line.moveInCount}` : `划出 -${line.moveOutCount}` }}
        </span>
        <span
          class="ml-auto px-2 py-0.5 rounded text-xs font-medium"
          :class="loadStatusMap[line.simulatedStatus].class"
        >
          模拟后：{{ loadStatusMap[line.simulatedStatus].text }}
        </span>
      </div>

      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
        <div class="bg-white rounded-industrial p-2 border border-industrial-200">
          <div class="text-xs text-industrial-500 mb-1">归属数量</div>
          <div class="flex items-center gap-1 font-mono font-medium">
            {{ line.currentCount }}
            <ArrowRight class="w-3 h-3 text-industrial-400" />
            <span class="text-primary-700">{{ line.simulatedCount }}</span>
            <span class="text-xs text-industrial-400 font-normal">
              / 阈值 {{ line.dailyCapacityThreshold ?? '未配置' }}
            </span>
          </div>
        </div>
        <div class="bg-white rounded-industrial p-2 border border-industrial-200">
          <div class="text-xs text-industrial-500 mb-1">承载率</div>
          <div class="flex items-center gap-1 font-mono font-medium">
            {{ formatRate(line.currentLoadRate) }}
            <ArrowRight class="w-3 h-3 text-industrial-400" />
            <span
              :class="{
                'text-red-600': line.simulatedStatus === 'OVERLOAD',
                'text-amber-600': line.simulatedStatus === 'WARNING',
                'text-green-600': line.simulatedStatus === 'NORMAL',
              }"
            >
              {{ formatRate(line.simulatedLoadRate) }}
            </span>
          </div>
        </div>
        <div class="bg-white rounded-industrial p-2 border border-industrial-200">
          <div class="text-xs text-industrial-500 mb-1">系数越界</div>
          <div class="flex items-center gap-1 font-mono font-medium">
            {{ line.currentOutOfRangeCount }}
            <ArrowRight class="w-3 h-3 text-industrial-400" />
            <span :class="line.simulatedOutOfRangeCount > 0 ? 'text-amber-600' : 'text-green-600'">
              {{ line.simulatedOutOfRangeCount }}
            </span>
          </div>
        </div>
        <div class="bg-white rounded-industrial p-2 border border-industrial-200">
          <div class="text-xs text-industrial-500 mb-1">负载状态</div>
          <div class="flex items-center gap-1">
            <span
              class="px-1.5 py-0.5 rounded text-xs font-medium"
              :class="loadStatusMap[line.currentStatus].class"
            >
              {{ loadStatusMap[line.currentStatus].text }}
            </span>
            <ArrowRight class="w-3 h-3 text-industrial-400" />
            <span
              class="px-1.5 py-0.5 rounded text-xs font-medium"
              :class="loadStatusMap[line.simulatedStatus].class"
            >
              {{ loadStatusMap[line.simulatedStatus].text }}
            </span>
          </div>
        </div>
      </div>

      <div v-if="line.reasons.length > 0" class="mt-3 space-y-1">
        <div
          v-for="(reason, idx) in line.reasons"
          :key="idx"
          class="flex items-start gap-1.5 text-xs"
          :class="reasonClass(line.simulatedStatus)"
        >
          <component :is="statusIcon(line.simulatedStatus)" class="w-3.5 h-3.5 mt-0.5 flex-shrink-0" />
          <span>{{ reason }}</span>
        </div>
      </div>
      <div v-else class="mt-3 flex items-center gap-1.5 text-xs text-green-600">
        <CheckCircle2 class="w-3.5 h-3.5" />
        <span>模拟后无预警，负载正常</span>
      </div>
    </div>
  </div>
</template>
