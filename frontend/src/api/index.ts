import axios from 'axios'
import type {
  ApiResponse,
  ProductionLine,
  SpringArchive,
  TransferRecord,
  TransferApplication,
  ApplicationDetail,
  ApplicationStatus,
  SubmitApplicationRequest,
  ApprovalRequest,
  ItemProcessResult,
  PageResponse,
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
    return Promise.reject(error)
  }
)

export const lineApi = {
  list: () => request.get<unknown, ApiResponse<ProductionLine[]>>('/lines'),
  get: (id: number) => request.get<unknown, ApiResponse<ProductionLine>>(`/lines/${id}`),
  create: (data: Omit<ProductionLine, 'id' | 'createTime' | 'updateTime'>) =>
    request.post<unknown, ApiResponse<ProductionLine>>('/lines', data),
}

export const springApi = {
  list: (params?: { lineId?: number; keyword?: string; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<SpringArchive>>>('/springs', { params }),
  groupByLine: () => request.get<unknown, ApiResponse<Record<number, SpringArchive[]>>>('/springs/group-by-line'),
  get: (id: number) => request.get<unknown, ApiResponse<SpringArchive>>(`/springs/${id}`),
  create: (data: Omit<SpringArchive, 'id' | 'createTime' | 'updateTime' | 'currentLineName' | 'initialLineName'>) =>
    request.post<unknown, ApiResponse<SpringArchive>>('/springs', data),
  getTrace: (id: number) => request.get<unknown, ApiResponse<TransferRecord[]>>(`/springs/${id}/trace`),
}

export const transferApi = {
  list: (params?: { springId?: number; lineId?: number; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<TransferRecord>>>('/transfers', { params }),
}

export const applicationApi = {
  list: (params?: { status?: ApplicationStatus; keyword?: string; page?: number; size?: number }) =>
    request.get<unknown, ApiResponse<PageResponse<TransferApplication>>>('/transfer-applications', { params }),
  detail: (id: number) =>
    request.get<unknown, ApiResponse<ApplicationDetail>>(`/transfer-applications/${id}`),
  submit: (data: SubmitApplicationRequest) =>
    request.post<unknown, ApiResponse<TransferApplication>>('/transfer-applications', data),
  approve: (data: ApprovalRequest) =>
    request.post<unknown, ApiResponse<ItemProcessResult[]>>('/transfer-applications/approve', data),
  reject: (data: ApprovalRequest) =>
    request.post<unknown, ApiResponse<ItemProcessResult[]>>('/transfer-applications/reject', data),
}

export const specApi = {
  getElasticForce: (params?: { min?: number; max?: number }) =>
    request.get<unknown, ApiResponse<number[]>>('/specs/elastic-force', { params }),
}
