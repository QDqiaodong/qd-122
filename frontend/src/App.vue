<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLineStore } from '@/stores/lines'
import {
  Gauge,
  Cog,
  GitBranch,
  FlaskConical,
  ClipboardCheck,
  History,
  Menu,
  X,
  Factory,
  OctagonPause,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const lineStore = useLineStore()
const sidebarCollapsed = ref(false)

const navItems = [
  { path: '/', name: '产线负载预警', icon: Gauge },
  { path: '/springs', name: '弹簧档案管理', icon: Cog },
  { path: '/transfer', name: '产线划转申请', icon: GitBranch },
  { path: '/simulation', name: '工序调拨模拟', icon: FlaskConical },
  { path: '/approval', name: '划转申请审批', icon: ClipboardCheck },
  { path: '/trace', name: '划转轨迹查询', icon: History },
]

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function navigateTo(path: string) {
  router.push(path)
}

onMounted(async () => {
  await lineStore.fetchLines()
})
</script>

<template>
  <div class="flex h-screen bg-industrial-50 overflow-hidden">
    <aside
      class="flex flex-col bg-primary-900 text-white transition-all duration-300"
      :class="sidebarCollapsed ? 'w-16' : 'w-60'"
    >
      <div class="flex items-center justify-between p-4 border-b border-primary-800">
        <div class="flex items-center gap-3 overflow-hidden">
          <Factory class="w-8 h-8 flex-shrink-0 text-accent-400" />
          <span
            v-if="!sidebarCollapsed"
            class="font-bold text-lg whitespace-nowrap animate-fade-in"
          >
            弹簧产线划转
          </span>
        </div>
        <button
          class="p-1 hover:bg-primary-800 rounded transition-colors"
          @click="toggleSidebar"
        >
          <Menu v-if="sidebarCollapsed" class="w-5 h-5" />
          <X v-else class="w-5 h-5" />
        </button>
      </div>

      <nav class="flex-1 py-4 overflow-y-auto">
        <div v-if="!sidebarCollapsed" class="px-4 mb-2 text-xs font-semibold text-primary-400 uppercase tracking-wider">
          功能导航
        </div>
        <ul class="space-y-1 px-2">
          <li v-for="item in navItems" :key="item.path">
            <button
              class="w-full flex items-center gap-3 px-3 py-3 rounded-industrial transition-all duration-200"
              :class="[
                route.path === item.path
                  ? 'bg-primary-800 text-accent-400 shadow-industrial'
                  : 'hover:bg-primary-800/50 text-primary-100 hover:text-white',
              ]"
              @click="navigateTo(item.path)"
            >
              <component
                :is="item.icon"
                class="w-5 h-5 flex-shrink-0"
                :class="route.path === item.path ? 'text-accent-400' : ''"
              />
              <span
                v-if="!sidebarCollapsed"
                class="font-medium whitespace-nowrap animate-fade-in"
              >
                {{ item.name }}
              </span>
            </button>
          </li>
        </ul>

        <div
          v-if="!sidebarCollapsed && lineStore.lines.length > 0"
          class="mt-6 border-t border-primary-800 pt-4"
        >
          <div class="px-4 mb-2 text-xs font-semibold text-primary-400 uppercase tracking-wider">
            生产产线
          </div>
          <ul class="space-y-1 px-2">
            <li
              v-for="line in lineStore.lines"
              :key="line.id"
              class="px-3 py-2 text-sm text-primary-200 animate-slide-in-left"
              :style="{ animationDelay: `${line.id * 50}ms` }"
            >
              <div class="flex items-center gap-2">
                <span
                  class="w-2 h-2 rounded-full"
                  :class="line.haltStatus === 'HALTED' ? 'bg-red-400' : 'bg-accent-500'"
                ></span>
                <span class="font-mono text-xs">{{ line.lineCode }}</span>
                <OctagonPause
                  v-if="line.haltStatus === 'HALTED'"
                  class="w-3.5 h-3.5 text-red-400 ml-auto cursor-help"
                  :title="`临时停台中：${line.haltReason || '原因未登记'}${line.haltExpectedResumeTime ? '，预计复台：' + String(line.haltExpectedResumeTime).replace('T', ' ').substring(0, 16) : ''}`"
                />
              </div>
              <div class="ml-4 text-xs text-primary-400">{{ line.lineName }}</div>
            </li>
          </ul>
        </div>
      </nav>

      <div
        v-if="!sidebarCollapsed"
        class="p-4 border-t border-primary-800 text-xs text-primary-400"
      >
        <div class="font-mono">v1.0.0</div>
        <div class="mt-1">装配机标准弹簧产线管理</div>
      </div>
    </aside>

    <main class="flex-1 flex flex-col overflow-hidden">
      <header class="h-14 bg-white border-b border-industrial-200 flex items-center justify-between px-6 shadow-sm">
        <div class="flex items-center gap-4">
          <h1 class="text-xl font-bold text-industrial-800">
            {{ route.meta.title as string }}
          </h1>
          <div class="h-6 w-px bg-industrial-200"></div>
          <span class="text-sm text-industrial-500">
            共 {{ lineStore.lines.length }} 条产线
          </span>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-sm text-industrial-500 font-mono">
            {{ new Date().toLocaleDateString('zh-CN') }}
          </span>
          <div class="w-8 h-8 rounded-full bg-primary-800 flex items-center justify-center text-white font-medium text-sm">
            管
          </div>
        </div>
      </header>

      <div class="flex-1 overflow-auto p-6">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
