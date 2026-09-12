<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { nightReviewApi } from '@/api'
import { useLineStore } from '@/stores/lines'
import type {
  NightLoadReviewDetail,
  ReviewStatus,
} from '@/types'
import ReviewIssueModal from '@/components/ReviewIssueModal.vue'
import ReviewConfirmModal from '@/components/ReviewConfirmModal.vue'
import {
  MoonStar,
  RefreshCw,
  Plus,
  ClipboardCheck,
  Cog,
  Gauge,
  AlertTriangle,
  ListChecks,
  Lock,
  ChevronRight,
  Flame,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const lineStore = useLineStore()

// 筛选条件随 URL 持久化，刷新（F5）后待确认/已确认筛选状态仍在
function parseStatus(value: unknown): ReviewStatus | null {
  return value === 'PENDING' || value === 'CONFIRMED' ? value : null
}
const statusFilter = ref<ReviewStatus | null>(parseStatus(route.query.status))

const loading = ref(false)
const reviews = ref<NightLoadReviewDetail[]>([])
const pagination = reactive({ page: 0, size: 10, total: 0, totalPages: 0 })

// 接班门禁
const guardAllowed = ref(true)
const guardPendingCount = ref(0)

const issueVisible = ref(false)
const confirmVisible = ref(false)
const confirmTarget = ref<NightLoadReviewDetail | null>(null)

// 详情抽屉
const detailVisible = ref(false)
const detail = ref<NightLoadReviewDetail | null>(null)

function syncQuery() {
  const query: Record<string, string> = {}
  if (statusFilter.value) query.status = statusFilter.value
  if (pagination.page > 0) query.page = String(pagination.page + 1)
  router.replace({ name: 'NightReview', query })
}

async function fetchList() {
  loading.value = true
  try {
    const response = await nightReviewApi.list({
      status: statusFilter.value ?? undefined,
      page: pagination.page,
      size: pagination.size,
    })
    reviews.value = response.data.content
    pagination.total = response.data.totalElements
    pagination.totalPages = response.data.totalPages
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '复核单加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchGuard() {
  try {
    const response = await nightReviewApi.guard()
    guardAllowed.value = response.data.allowed
    guardPendingCount.value = response.data.pendingCount
  } catch {
    // 门禁加载失败不阻断列表，接班人提交划转时后端仍会强校验
  }
}

function changeFilter(status: ReviewStatus | null) {
  statusFilter.value = status
  pagination.page = 0
  syncQuery()
  fetchList()
}

function goPage(page: number) {
  pagination.page = page
  syncQuery()
  fetchList()
}

function refresh() {
  fetchList()
  fetchGuard()
}

/** 今夜已签发复核单的产线ID（用于签发弹窗排重），取当前列表当日数据 */
const todayStr = new Date().toISOString().slice(0, 10)
const issuedLineIds = computed(() => {
  const ids = new Set<number>()
  reviews.value.forEach((d) => {
    if (String(d.review.reviewDate).slice(0, 10) === todayStr) {
      ids.add(d.review.lineId)
    }
  })
  return ids
})

function openDetail(d: NightLoadReviewDetail) {
  detail.value = d
  detailVisible.value = true
}

function openConfirm(d: NightLoadReviewDetail) {
  confirmTarget.value = d
  confirmVisible.value = true
}

function onIssued() {
  pagination.page = 0
  refresh()
}

function onConfirmed() {
  refresh()
  if (detail.value && detail.value.review.status === 'PENDING') {
    detailVisible.value = false
  }
}

function statusMeta(status: ReviewStatus) {
  return status === 'PENDING'
    ? { text: '待确认', class: 'bg-amber-100 text-amber-700' }
    : { text: '已确认', class: 'bg-green-100 text-green-700' }
}

function formatTime(time?: string | null) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 16)
}

function goTransfer() {
  router.push({ name: 'TransferOperation' })
}

onMounted(async () => {
  pagination.page = Math.max(0, (Number(route.query.page) || 1) - 1)
  await lineStore.fetchLines()
  fetchList()
  fetchGuard()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 接班门禁横幅：有待确认复核单时强提示，全部确认后才可提交新划转 -->
    <div
      v-if="!guardAllowed"
      class="card-industrial p-4 border-l-4 border-amber-500 bg-amber-50"
    >
      <div class="flex items-start gap-3">
        <AlertTriangle class="w-6 h-6 text-amber-600 flex-shrink-0 mt-0.5" />
        <div class="flex-1">
          <div class="font-bold text-amber-800">
            您还有 {{ guardPendingCount }} 张夜班承载复核单未确认
          </div>
          <p class="text-sm text-amber-700 mt-1">
            接班人须先确认昨夜全部复核单后，才能提交新的划转申请。请在下方待确认列表逐条核对并填写跟进说明。
          </p>
        </div>
        <button
          class="px-3 py-1.5 rounded-industrial bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 transition-colors"
          @click="changeFilter('PENDING')"
        >
          去确认
        </button>
      </div>
    </div>
    <div
      v-else
      class="card-industrial p-4 border-l-4 border-green-500 bg-green-50 flex items-center gap-3"
    >
      <ClipboardCheck class="w-6 h-6 text-green-600 flex-shrink-0" />
      <div class="flex-1">
        <div class="font-bold text-green-800">夜班复核单均已确认</div>
        <p class="text-sm text-green-700 mt-0.5">接班交接已完成，可以正常提交新的划转申请。</p>
      </div>
      <button
        class="px-3 py-1.5 rounded-industrial bg-green-600 text-white text-sm font-medium hover:bg-green-700 transition-colors"
        @click="goTransfer"
      >
        提交新划转
      </button>
    </div>

    <div class="card-industrial p-4">
      <div class="flex flex-wrap items-center gap-3 mb-4">
        <div class="flex items-center gap-2">
          <MoonStar class="w-5 h-5 text-primary-600" />
          <h2 class="text-lg font-bold text-industrial-800">夜班承载复核单</h2>
        </div>

        <!-- 状态筛选：全部 / 待确认 / 已确认，刷新后保持 -->
        <div class="ml-2 flex items-center gap-1 text-sm">
          <button
            class="px-3 py-1 rounded-industrial transition-colors"
            :class="statusFilter === null ? 'bg-primary-600 text-white' : 'bg-industrial-100 text-industrial-600 hover:bg-industrial-200'"
            @click="changeFilter(null)"
          >
            全部
          </button>
          <button
            class="px-3 py-1 rounded-industrial transition-colors"
            :class="statusFilter === 'PENDING' ? 'bg-amber-500 text-white' : 'bg-industrial-100 text-industrial-600 hover:bg-industrial-200'"
            @click="changeFilter('PENDING')"
          >
            待确认
          </button>
          <button
            class="px-3 py-1 rounded-industrial transition-colors"
            :class="statusFilter === 'CONFIRMED' ? 'bg-green-600 text-white' : 'bg-industrial-100 text-industrial-600 hover:bg-industrial-200'"
            @click="changeFilter('CONFIRMED')"
          >
            已确认
          </button>
        </div>

        <div class="ml-auto flex items-center gap-2">
          <button class="btn-industrial-outline text-sm" @click="refresh">
            <RefreshCw class="w-4 h-4 inline mr-1" :class="{ 'animate-spin': loading }" />
            刷新
          </button>
          <button class="btn-industrial text-sm" @click="issueVisible = true">
            <Plus class="w-4 h-4 inline mr-1" />
            签发复核单
          </button>
        </div>
      </div>

      <div class="overflow-x-auto border border-industrial-200 rounded-industrial">
        <table class="table-industrial text-sm">
          <thead>
            <tr>
              <th class="py-2">复核单号</th>
              <th class="py-2">产线</th>
              <th class="py-2">当前归属弹簧数</th>
              <th class="py-2">是否压到日承载</th>
              <th class="py-2">系数越界条数</th>
              <th class="py-2">待批划转</th>
              <th class="py-2">状态</th>
              <th class="py-2">签发 / 确认</th>
              <th class="py-2">操作</th>
            </tr>
          </thead>
          <tbody v-if="!loading && reviews.length > 0">
            <tr
              v-for="d in reviews"
              :key="d.review.id"
              class="cursor-pointer"
              @click="openDetail(d)"
            >
              <td class="py-2 font-mono text-xs text-primary-800">{{ d.review.reviewNo }}</td>
              <td class="py-2">
                <div class="font-medium">{{ d.review.lineName }}</div>
                <div class="text-xs text-industrial-400 font-mono">{{ d.review.lineCode }}</div>
              </td>
              <td class="py-2">
                <span class="inline-flex items-center gap-1">
                  <Cog class="w-3.5 h-3.5 text-industrial-400" />
                  <span class="font-mono font-bold">{{ d.review.springCount }}</span> 件
                  <span class="text-xs text-industrial-400">
                    （{{ d.review.loadRate == null ? '阈值未配置' : Number(d.review.loadRate).toFixed(2) + '%' }}）
                  </span>
                </span>
              </td>
              <td class="py-2">
                <span
                  class="px-2 py-0.5 rounded text-xs font-medium"
                  :class="d.review.overCapacity
                    ? 'bg-red-100 text-red-700'
                    : d.review.capacityReached
                      ? 'bg-amber-100 text-amber-700'
                      : 'bg-green-100 text-green-700'"
                >
                  {{ d.review.overCapacity
                    ? '已超载'
                    : d.review.capacityReached
                      ? '已压满'
                      : '未压到' }}
                </span>
              </td>
              <td class="py-2">
                <span
                  class="font-mono font-bold"
                  :class="d.review.outOfRangeCount > 0 ? 'text-amber-600' : 'text-industrial-700'"
                >
                  <Gauge class="w-3.5 h-3.5 inline mr-0.5" />
                  {{ d.review.outOfRangeCount }}
                </span>
              </td>
              <td class="py-2">
                <span
                  class="font-mono font-bold"
                  :class="d.review.pendingTransferCount > 0 ? 'text-accent-700' : 'text-industrial-700'"
                >
                  <ListChecks class="w-3.5 h-3.5 inline mr-0.5" />
                  {{ d.review.pendingTransferCount }} 条 / {{ d.review.pendingApplicationCount }} 单
                </span>
              </td>
              <td class="py-2">
                <span class="px-2 py-0.5 rounded text-xs font-medium" :class="statusMeta(d.review.status).class">
                  {{ statusMeta(d.review.status).text }}
                </span>
              </td>
              <td class="py-2 text-xs text-industrial-500">
                <div>{{ d.review.operator }} 签于 {{ formatTime(d.review.issueTime) }}</div>
                <div v-if="d.review.status === 'CONFIRMED'" class="text-green-600">
                  {{ d.review.confirmer }} 确认于 {{ formatTime(d.review.confirmTime) }}
                </div>
              </td>
              <td class="py-2" @click.stop>
                <div class="flex items-center gap-2">
                  <button
                    v-if="d.review.status === 'PENDING'"
                    class="px-2 py-1 rounded bg-accent-600 text-white text-xs hover:bg-accent-700 transition-colors"
                    @click="openConfirm(d)"
                  >
                    <ClipboardCheck class="w-3.5 h-3.5 inline mr-0.5" />
                    确认
                  </button>
                  <span v-else class="inline-flex items-center gap-1 text-xs text-industrial-400">
                    <Lock class="w-3.5 h-3.5" />
                    已锁定
                  </span>
                  <ChevronRight class="w-4 h-4 text-industrial-400" />
                </div>
              </td>
            </tr>
          </tbody>
          <tbody v-else-if="!loading">
            <tr>
              <td colspan="9" class="text-center py-12 text-industrial-400">
                <MoonStar class="w-12 h-12 mx-auto mb-3 opacity-30" />
                <p>暂无夜班承载复核单</p>
              </td>
            </tr>
          </tbody>
          <tbody v-else>
            <tr>
              <td colspan="9" class="text-center py-12 text-industrial-400">
                <div class="animate-pulse">加载中...</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div v-if="pagination.totalPages > 1" class="flex items-center justify-end gap-2 mt-3 text-sm">
        <button
          class="px-3 py-1 rounded-industrial border border-industrial-300 disabled:opacity-40"
          :disabled="pagination.page === 0"
          @click="goPage(pagination.page - 1)"
        >
          上一页
        </button>
        <span class="text-industrial-500">{{ pagination.page + 1 }} / {{ pagination.totalPages }}</span>
        <button
          class="px-3 py-1 rounded-industrial border border-industrial-300 disabled:opacity-40"
          :disabled="pagination.page + 1 >= pagination.totalPages"
          @click="goPage(pagination.page + 1)"
        >
          下一页
        </button>
      </div>
    </div>

    <!-- 签发弹窗 -->
    <ReviewIssueModal
      v-model:visible="issueVisible"
      :lines="lineStore.lines"
      :issued-line-ids="issuedLineIds"
      @issued="onIssued"
    />

    <!-- 确认弹窗 -->
    <ReviewConfirmModal
      v-model:visible="confirmVisible"
      :detail="confirmTarget"
      @confirmed="onConfirmed"
    />

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="复核单详情" size="520px">
      <div v-if="detail" class="space-y-4">
        <div class="flex items-center justify-between">
          <div>
            <div class="font-mono text-xs text-industrial-500">{{ detail.review.reviewNo }}</div>
            <div class="text-lg font-bold text-industrial-800">{{ detail.review.lineName }}</div>
          </div>
          <span class="px-2 py-0.5 rounded text-xs font-medium" :class="statusMeta(detail.review.status).class">
            {{ statusMeta(detail.review.status).text }}
          </span>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="p-3 bg-industrial-50 rounded-industrial">
            <div class="text-xs text-industrial-500 flex items-center gap-1"><Cog class="w-3.5 h-3.5" />当前归属弹簧数</div>
            <div class="text-2xl font-bold font-mono text-primary-800 mt-1">{{ detail.review.springCount }}</div>
            <div class="text-xs text-industrial-400 mt-0.5">
              阈值 {{ detail.review.dailyCapacityThreshold ?? '未配置' }}
              （{{ detail.review.loadRate == null ? '无法计算承载率' : Number(detail.review.loadRate).toFixed(2) + '%' }}）
            </div>
          </div>
          <div class="p-3 bg-industrial-50 rounded-industrial">
            <div class="text-xs text-industrial-500 flex items-center gap-1"><Gauge class="w-3.5 h-3.5" />系数越界条数</div>
            <div
              class="text-2xl font-bold font-mono mt-1"
              :class="detail.review.outOfRangeCount > 0 ? 'text-amber-600' : 'text-industrial-800'"
            >
              {{ detail.review.outOfRangeCount }}
            </div>
          </div>
        </div>

        <div class="p-3 rounded-industrial border" :class="detail.review.overCapacity ? 'border-red-200 bg-red-50' : detail.review.capacityReached ? 'border-amber-200 bg-amber-50' : 'border-green-200 bg-green-50'">
          <div class="text-sm font-medium flex items-center gap-1.5"
               :class="detail.review.overCapacity ? 'text-red-700' : detail.review.capacityReached ? 'text-amber-700' : 'text-green-700'">
            <AlertTriangle class="w-4 h-4" />
            {{ detail.review.overCapacity
              ? '已超过日承载（超载）'
              : detail.review.capacityReached
                ? '已压到日承载（满载，无余量）'
                : '未压到日承载，仍有余量' }}
          </div>
        </div>

        <!-- 次日必须跟进的待批划转 -->
        <div>
          <div class="flex items-center gap-1.5 font-medium text-industrial-800 mb-2">
            <ListChecks class="w-4 h-4 text-accent-600" />
            次日必须跟进的待批划转
            <span class="text-xs text-industrial-400">
              （{{ detail.review.pendingTransferCount }} 条 / {{ detail.review.pendingApplicationCount }} 单，为签发时快照）
            </span>
          </div>
          <div v-if="detail.pendingTransfers.length > 0" class="space-y-2">
            <div
              v-for="(item, idx) in detail.pendingTransfers"
              :key="idx"
              class="p-2.5 rounded-industrial border border-industrial-200 text-sm"
            >
              <div class="flex items-center justify-between">
                <span class="font-mono text-xs text-primary-700">{{ item.applicationNo }}</span>
                <span v-if="item.urgent" class="px-1.5 py-0.5 bg-red-100 text-red-700 rounded text-[10px] flex items-center gap-0.5">
                  <Flame class="w-3 h-3" />加急
                </span>
              </div>
              <div class="mt-1 flex items-center justify-between">
                <span class="font-mono text-xs">{{ item.springCode }}（{{ item.model }}）</span>
                <span class="text-xs text-industrial-500">由 {{ item.fromLineName }} 划入</span>
              </div>
              <div class="mt-1 text-xs text-industrial-500">
                申请人 {{ item.applicant }} · {{ item.applyTime }}
              </div>
              <div v-if="item.reason" class="mt-1 text-xs text-industrial-600">原因：{{ item.reason }}</div>
            </div>
          </div>
          <div v-else class="text-sm text-industrial-400 p-3 bg-industrial-50 rounded-industrial text-center">
            签发时无流向该产线的待批划转
          </div>
        </div>

        <div v-if="detail.review.handoverRemark" class="p-3 bg-industrial-50 rounded-industrial text-sm">
          <div class="text-xs text-industrial-500 mb-1">交班备注</div>
          <div class="text-industrial-700">{{ detail.review.handoverRemark }}</div>
        </div>

        <!-- 跟进说明：已确认只读展示 -->
        <div v-if="detail.review.status === 'CONFIRMED'" class="p-3 rounded-industrial border border-green-200 bg-green-50">
          <div class="flex items-center gap-1.5 text-sm font-medium text-green-800 mb-1">
            <Lock class="w-4 h-4" />跟进说明（{{ detail.review.confirmer }} · {{ formatTime(detail.review.confirmTime) }}）
          </div>
          <div class="text-sm text-green-800 whitespace-pre-wrap">{{ detail.review.followUpNote }}</div>
        </div>

        <button
          v-if="detail.review.status === 'PENDING'"
          class="w-full btn-industrial-accent"
          @click="openConfirm(detail)"
        >
          <ClipboardCheck class="w-4 h-4 inline mr-1" />
          确认此复核单
        </button>
      </div>
    </el-drawer>
  </div>
</template>
