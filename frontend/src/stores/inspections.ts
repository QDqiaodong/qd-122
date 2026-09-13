import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LineInspection } from '@/types'
import { inspectionApi } from '@/api'

function isTodayText(time?: string | null) {
  if (!time) return false
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
    now.getDate()
  ).padStart(2, '0')}`
  return time.substring(0, 10) === today
}

/**
 * 各产线最近一次开班点检缓存。
 * 判定口径：当日有点检记录为「已点检」；当日点检且结论通过才可作为调拨模拟接收方。
 */
export const useInspectionStore = defineStore('inspection', () => {
  /** key 为产线ID 的最近一次点检记录 */
  const latestMap = ref<Map<number, LineInspection>>(new Map())
  const loaded = ref(false)

  async function fetchLatest(force = false) {
    if (!force && loaded.value) return
    const response = await inspectionApi.latest()
    const map = new Map<number, LineInspection>()
    Object.entries(response.data ?? {}).forEach(([lineId, inspection]) => {
      map.set(Number(lineId), inspection)
    })
    latestMap.value = map
    loaded.value = true
  }

  function latestOf(lineId: number) {
    return latestMap.value.get(lineId) ?? null
  }

  /** 当日是否已开班点检 */
  function isInspectedToday(lineId: number) {
    const latest = latestOf(lineId)
    return !!latest && isTodayText(latest.inspectTime)
  }

  /** 是否可作为调拨模拟接收方：当日已点检且点检通过 */
  function isReady(lineId: number) {
    const latest = latestOf(lineId)
    return !!latest && isTodayText(latest.inspectTime) && latest.passed
  }

  /**
   * 产线点检拦截标记：未点检返回「未开班点检」，点检未通过返回「点检未通过」，
   * 当日点检通过返回 null。档案页勾划转与模拟页目标产线展示用。
   */
  function blockLabel(lineId: number): string | null {
    const latest = latestOf(lineId)
    if (!latest || !isTodayText(latest.inspectTime)) return '未开班点检'
    if (!latest.passed) return '点检未通过'
    return null
  }

  return {
    latestMap,
    loaded,
    fetchLatest,
    latestOf,
    isInspectedToday,
    isReady,
    blockLabel,
  }
})
