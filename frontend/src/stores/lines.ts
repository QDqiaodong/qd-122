import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ProductionLine } from '@/types'
import { lineApi } from '@/api'

export const useLineStore = defineStore('line', () => {
  const lines = ref<ProductionLine[]>([])
  const loading = ref(false)

  const lineMap = computed(() => {
    const map = new Map<number, ProductionLine>()
    lines.value.forEach((line) => map.set(line.id, line))
    return map
  })

  const lineNameMap = computed(() => {
    const map = new Map<number, string>()
    lines.value.forEach((line) => map.set(line.id, line.lineName))
    return map
  })

  async function fetchLines() {
    if (lines.value.length > 0) return
    loading.value = true
    try {
      const response = await lineApi.list()
      lines.value = response.data
    } finally {
      loading.value = false
    }
  }

  function getLineName(id: number) {
    return lineNameMap.value.get(id) || '未知产线'
  }

  return {
    lines,
    loading,
    lineMap,
    lineNameMap,
    fetchLines,
    getLineName,
  }
})
