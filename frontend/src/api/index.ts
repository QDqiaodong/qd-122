import axios from 'axios'
import type {
  ApiResponse,
  ProductionLine,
  SpringArchive,
  SealRequest,
  UnsealRequest,
  SealStatus,
  LineHaltRequest,
  LineResumeRequest,
  LineHaltGuard,
  TransferRecord,
  TransferApplication,
  ApplicationDetail,
  ApplicationStatus,
  SubmitApplicationRequest,
  ApprovalRequest,
  ItemProcessResult,
  UrgentRequest,
  PageResponse,
  LineLoadBoard,
  LineLoadDetail,
  LineThresholdUpdateRequest,
  LoadAlertEvent,
  AlertHandleStatus,
  AlertDispositionRequest,
  SimulationStatus,
  SimulationEstimate,
  SimulationDetail,
  TransferSimulation,
  SaveSimulationRequest,
  AdoptSimulationRequest,
  ReviewStatus,
  NightLoadReviewDetail,
  CreateReviewRequest,
  ConfirmReviewRequest,
  ReviewGuard,
  SampleStatus,
  ElasticSample,
  RegisterSampleRequest,
  UpdateMeasuredRequest,
  CloseSampleRequest,
} from '@/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse<unknown>
    if (res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res as any
  },
  (error) => {
    // 优先提取后端返回的明确业务原因，避免页面上只出现 "Request failed with status code 500"
    const backendMessage = error?.response?.data?.message
    if (backendMessage) {
      return Promise.reject(new Error(backendMessage))
    }
    if (error?.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请稍后重试'))
    }
    if (error?.response) {
      return Promise.reject(new Error(`服务异常（${error.response.status}），请稍后重试`))
    }
    return Promise.reject(new Error('网络异常，无法连接服务器，请检查网络后重试'))
  }
)

export const lineApi = {
  list: () => request.get<unknown, ApiResponse<ProductionLine[]>>('/lines'),
  get: (id: number) => request.get<unknown, ApiResponse<ProductionLine>>(`/lines/${id}`),
  create: (data: Omit<ProductionLine, 'id' | 'createTime' | 'updateTime'>) =>
    request.post<unknown, ApiResponse<ProductionLine>>('/lines', data),
  haltGuard: (id: number) =>
    request.get<unknown, ApiResponse<LineHaltGuard>>(`/lines/${id}/halt-guard`),
  halt: (id: number, data: LineHaltRequest) =>
    request.post<unknown, ApiResponse<ProductionLine>>(`/lines/${id}/halt`, data),
  resume: (id: number, data: LineResumeRequest) =>
    request.post<unknown, ApiResponse<ProductionLine>>(`/lines/${id}/resume`, data),
}

export const springApi = {
  list: (params?: { lineId?: number; sealStatus?: SealStatus; keyword?: string; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<SpringArchive>>>('/springs', { params }),
  groupByLine: () => request.get<unknown, ApiResponse<Record<number, SpringArchive[]>>>('/springs/group-by-line'),
  get: (id: number) => request.get<unknown, ApiResponse<SpringArchive>>(`/springs/${id}`),
  create: (data: Omit<SpringArchive, 'id' | 'createTime' | 'updateTime' | 'currentLineName' | 'initialLineName'>) =>
    request.post<unknown, ApiResponse<SpringArchive>>('/springs', data),
  getTrace: (id: number) => request.get<unknown, ApiResponse<TransferRecord[]>>(`/springs/${id}/trace`),
  seal: (id: number, data: SealRequest) =>
    request.post<unknown, ApiResponse<SpringArchive>>(`/springs/${id}/seal`, data),
  unseal: (id: number, data: UnsealRequest) =>
    request.post<unknown, ApiResponse<SpringArchive>>(`/springs/${id}/unseal`, data),
}

export const transferApi = {
  list: (params?: { springId?: number; lineId?: number; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<TransferRecord>>>('/transfers', { params }),
}

export const applicationApi = {
  list: (params?: { status?: ApplicationStatus; keyword?: string; halted?: boolean; urgent?: boolean; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<TransferApplication>>>('/transfer-applications', { params }),
  detail: (id: number) =>
    request.get<unknown, ApiResponse<ApplicationDetail>>(`/transfer-applications/${id}`),
  submit: (data: SubmitApplicationRequest) =>
    request.post<unknown, ApiResponse<TransferApplication>>('/transfer-applications', data),
  approve: (data: ApprovalRequest) =>
    request.post<unknown, ApiResponse<ItemProcessResult[]>>('/transfer-applications/approve', data),
  reject: (data: ApprovalRequest) =>
    request.post<unknown, ApiResponse<ItemProcessResult[]>>('/transfer-applications/reject', data),
  urgent: (id: number, data: UrgentRequest) =>
    request.post<unknown, ApiResponse<TransferApplication>>(`/transfer-applications/${id}/urgent`, data),
  cancelUrgent: (id: number, data: UrgentRequest) =>
    request.post<unknown, ApiResponse<TransferApplication>>(`/transfer-applications/${id}/cancel-urgent`, data),
}

export const specApi = {
  getElasticForce: (params?: { min?: number; max?: number }) =>
    request.get<unknown, ApiResponse<number[]>>('/specs/elastic-force', { params }),
}

export const lineLoadApi = {
  board: () => request.get<unknown, ApiResponse<LineLoadBoard>>('/line-load/board'),
  detail: (lineId: number) =>
    request.get<unknown, ApiResponse<LineLoadDetail>>(`/line-load/lines/${lineId}`),
  updateThreshold: (lineId: number, data: LineThresholdUpdateRequest) =>
    request.put<unknown, ApiResponse<ProductionLine>>(`/line-load/lines/${lineId}/threshold`, data),
}

export const loadAlertApi = {
  list: (params?: { status?: AlertHandleStatus; lineId?: number }) =>
    request.get<unknown, ApiResponse<LoadAlertEvent[]>>('/line-load/alerts', { params }),
  detail: (eventId: number) =>
    request.get<unknown, ApiResponse<LoadAlertEvent>>(`/line-load/alerts/${eventId}`),
  confirm: (eventId: number, data: AlertDispositionRequest) =>
    request.post<unknown, ApiResponse<LoadAlertEvent>>(`/line-load/alerts/${eventId}/confirm`, data),
  resolve: (eventId: number, data: AlertDispositionRequest) =>
    request.post<unknown, ApiResponse<LoadAlertEvent>>(`/line-load/alerts/${eventId}/resolve`, data),
}

export const simulationApi = {
  preview: (data: { springIds: number[]; toLineId: number }) =>
    request.post<unknown, ApiResponse<SimulationEstimate>>('/simulations/preview', data),
  save: (data: SaveSimulationRequest) =>
    request.post<unknown, ApiResponse<TransferSimulation>>('/simulations', data),
  list: (params?: { status?: SimulationStatus; keyword?: string; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<TransferSimulation>>>('/simulations', { params }),
  detail: (id: number) =>
    request.get<unknown, ApiResponse<SimulationDetail>>(`/simulations/${id}`),
  adopt: (id: number, data: AdoptSimulationRequest) =>
    request.post<unknown, ApiResponse<TransferSimulation>>(`/simulations/${id}/adopt`, data),
  discard: (id: number) =>
    request.post<unknown, ApiResponse<TransferSimulation>>(`/simulations/${id}/discard`),
}

export const nightReviewApi = {
  /** 复核单列表，可按 PENDING-待确认 / CONFIRMED-已确认 筛选 */
  list: (params?: { status?: ReviewStatus; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<NightLoadReviewDetail>>>('/night-reviews', { params }),
  detail: (id: number) =>
    request.get<unknown, ApiResponse<NightLoadReviewDetail>>(`/night-reviews/${id}`),
  /** 接班门禁：是否仍有待确认复核单 */
  guard: () => request.get<unknown, ApiResponse<ReviewGuard>>('/night-reviews/guard'),
  /** 交班调度员按产线签发复核单 */
  issue: (data: CreateReviewRequest) =>
    request.post<unknown, ApiResponse<NightLoadReviewDetail>>('/night-reviews', data),
  /** 接班调度员确认（跟进说明必填） */
  confirm: (id: number, data: ConfirmReviewRequest) =>
    request.post<unknown, ApiResponse<NightLoadReviewDetail>>(`/night-reviews/${id}/confirm`, data),
}

export const elasticSampleApi = {
  /** 留样单列表，可按 待闭环/已闭环、产线、是否偏离、关键词筛选（默认待闭环优先） */
  list: (params?: {
    status?: SampleStatus
    lineId?: number
    deviated?: boolean
    keyword?: string
    page?: number
    size?: number
  }) => request.get<unknown, ApiResponse<PageResponse<ElasticSample>>>('/elastic-samples', { params }),
  detail: (id: number) =>
    request.get<unknown, ApiResponse<ElasticSample>>(`/elastic-samples/${id}`),
  /** 质量员按产线登记留样 */
  register: (data: RegisterSampleRequest) =>
    request.post<unknown, ApiResponse<ElasticSample>>('/elastic-samples', data),
  /** 修改实测系数（仅待闭环可改，偏离标记按登记产线适用区间重算） */
  updateMeasured: (id: number, data: UpdateMeasuredRequest) =>
    request.put<unknown, ApiResponse<ElasticSample>>(`/elastic-samples/${id}/measured`, data),
  /** 闭环（处置结论必填） */
  close: (id: number, data: CloseSampleRequest) =>
    request.post<unknown, ApiResponse<ElasticSample>>(`/elastic-samples/${id}/close`, data),
}
