export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface ProductionLine {
  id: number
  lineCode: string
  lineName: string
  description: string
  dailyCapacityThreshold?: number | null
  elasticMin?: number | null
  elasticMax?: number | null
  createTime: string
}

export type LineLoadStatus = 'NORMAL' | 'WARNING' | 'OVERLOAD'

export interface LineLoadStats {
  lineId: number
  lineCode: string
  lineName: string
  description?: string
  status: LineLoadStatus
  dailyCapacityThreshold?: number | null
  springCount: number
  loadRate?: number | null
  elasticMin?: number | null
  elasticMax?: number | null
  outOfRangeCount: number
  trendDays: number
  recentInCount: number
  recentOutCount: number
  recentNetIn: number
  reasons: string[]
}

export interface LineLoadBoard {
  totalLines: number
  totalSprings: number
  normalCount: number
  warningCount: number
  overloadCount: number
  lines: LineLoadStats[]
  normalLines: LineLoadStats[]
  warningLines: LineLoadStats[]
  overloadLines: LineLoadStats[]
}

export interface LineLoadDetail {
  stats: LineLoadStats
  springs: SpringArchive[]
  recentTransfers: TransferRecord[]
}

export interface LineThresholdUpdateRequest {
  dailyCapacityThreshold: number
  elasticMin?: number | null
  elasticMax?: number | null
}

export interface SpringArchive {
  id: number
  springCode: string
  model: string
  elasticCoefficient: number
  outerDiameter: number
  currentLineId: number
  currentLineName: string
  initialLineId: number
  initialLineName?: string
  createTime: string
  updateTime: string
}

export interface TransferRecord {
  id: number
  springId: number
  springCode: string
  fromLineId: number
  fromLineName: string
  toLineId: number
  toLineName: string
  operator: string
  operateTime: string
  remark: string
}

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'PARTIAL'
export type ItemStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface TransferApplication {
  id: number
  applicationNo: string
  applicant: string
  applyTime: string
  toLineId: number
  toLineName: string
  reason: string
  status: ApplicationStatus
  totalCount?: number
  pendingCount?: number
  approvedCount?: number
  rejectedCount?: number
}

export interface TransferApplicationItem {
  id: number
  applicationId: number
  springId: number
  springCode: string
  model: string
  elasticCoefficient: number
  outerDiameter: number
  fromLineId: number
  fromLineName: string
  toLineId: number
  toLineName: string
  status: ItemStatus
  approver?: string
  approveTime?: string
  rejectReason?: string
  transferRecordId?: number
}

export interface TransferApplicationLog {
  id: number
  applicationId: number
  action: 'SUBMIT' | 'APPROVE' | 'REJECT'
  operator: string
  operateTime: string
  detail: string
}

export interface ApplicationDetail {
  application: TransferApplication
  items: TransferApplicationItem[]
  logs: TransferApplicationLog[]
}

export interface SubmitApplicationRequest {
  springIds: number[]
  toLineId: number
  applicant: string
  reason: string
}

export interface ApprovalRequest {
  itemIds: number[]
  approver: string
  reason?: string
}

export interface ItemProcessResult {
  itemId: number
  springCode: string
  success: boolean
  message: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
