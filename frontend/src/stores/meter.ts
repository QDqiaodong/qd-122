import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MeterReading } from '@/types'
import { meterApi } from '@/api'

/**
 * 各产线电表抄录缓存。
 * - 最新一条抄录：列表展示最近读数与是否异常；
 * - 当天白班抄录：夜班复核签发前判定该线当天是否已抄白班（未抄不能交）。
 */
export const useMeterStore = defineStore('meter', () => {
  /** key 为产线ID 的最近一条抄录 */
  const latestMap = ref<Map<number, MeterReading>>(new Map())
  /** key 为产线ID 的当天白班抄录 */
  const dayShiftMap = ref<Map<number, MeterReading>>(new Map())
  const latestLoaded = ref(false)

  async function fetchLatest(force = false) {
    if (!force && latestLoaded.value) return
    const response = await meterApi.latest()
    const map = new Map<number, MeterReading>()
    Object.entries(response.data ?? {}).forEach(([lineId, reading]) => {
      map.set(Number(lineId), reading)
    })
    latestMap.value = map
    latestLoaded.value = true
  }

  async function fetchDayShiftToday() {
    const response = await meterApi.dayShiftToday()
    const map = new Map<number, MeterReading>()
    Object.entries(response.data ?? {}).forEach(([lineId, reading]) => {
      map.set(Number(lineId), reading)
    })
    dayShiftMap.value = map
  }

  function latestOf(lineId: number) {
    return latestMap.value.get(lineId) ?? null
  }

  /** 该产线当天是否已有白班抄录（夜班复核提交前置条件） */
  function hasDayShiftToday(lineId: number) {
    return dayShiftMap.value.has(lineId)
  }

  /** 该产线当天白班抄录 */
  function dayShiftOf(lineId: number) {
    return dayShiftMap.value.get(lineId) ?? null
  }

  return {
    latestMap,
    dayShiftMap,
    latestLoaded,
    fetchLatest,
    fetchDayShiftToday,
    latestOf,
    hasDayShiftToday,
    dayShiftOf,
  }
})
