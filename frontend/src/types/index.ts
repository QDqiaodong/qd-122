export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 产线停台状态：正常 / 临时停台中 */
export type LineHaltStatusType = 'NORMAL' | 'HALTED'

/** 停台登记：调度员登记停台原因与预计复台时间 */
export interface LineHaltRequest {
  operator: string
  reason: string
  /** 预计复台时间，格式 yyyy-MM-dd HH:mm:ss */
  expectedResumeTime: string
}

/** 复台：必须填写结论 */
export interface LineResumeRequest {
  operator: string
  conclusion: string
}

/** 登记停台前的影响提示（待审批申请量） */
export interface LineHaltGuard {
  lineId: number
  lineCode: string
  lineName: string
  halted: boolean
  pendingItemCount: number
  pendingApplicationCount: number
}

export interface ProductionLine {
  id: number
  lineCode: string
  lineName: string
  description: string
  dailyCapacityThreshold?: number | null
  elasticMin?: number | null
  elasticMax?: number | null
  /** 停台状态：NORMAL-正常 HALTED-停台中（停台期间不能作为划转接收方） */
  haltStatus?: LineHaltStatusType
  haltReason?: string | null
  haltExpectedResumeTime?: string | null
  haltOperator?: string | null
  haltTime?: string | null
  resumeOperator?: string | null
  resumeTime?: string | null
  resumeConclusion?: string | null
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
  /** 当前未关闭告警事件ID，无则 null */
  openAlertEventId?: number | null
  /** 未关闭告警事件处置状态：PENDING / PROCESSING */
  openAlertStatus?: AlertHandleStatus | null
  /** 产线当前是否临时停台（停台期间不能作为划转接收方） */
  halted?: boolean
  haltReason?: string | null
  haltExpectedResumeTime?: string | null
  haltOperator?: string | null
  haltTime?: string | null
  resumeOperator?: string | null
  resumeTime?: string | null
  resumeConclusion?: string | null
}

export interface LineLoadBoard {
  totalLines: number
  totalSprings: number
  normalCount: number
  warningCount: number
  overloadCount: number
  /** 待处理（未确认责任人）的告警事件数 */
  pendingAlertCount: number
  /** 未关闭（待处理 + 处置中）的告警事件数 */
  openAlertCount: number
  /** 加急且仍待审批（待审批/部分处理）的划转申请单数 */
  urgentPendingCount: number
  lines: LineLoadStats[]
  normalLines: LineLoadStats[]
  warningLines: LineLoadStats[]
  overloadLines: LineLoadStats[]
}

/** 告警处置状态：待处理 / 处置中 / 已关闭 */
export type AlertHandleStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED'

/** 告警事件处置记录 */
export interface AlertHandleLog {
  id: number
  action: 'CONFIRM' | 'PLAN' | 'REMARK' | 'RESOLVE' | 'AUTO_RESOLVE'
  operator: string
  operateTime: string
  resultStatus: AlertHandleStatus
  detail?: string
}

/** 负载告警事件（含触发快照与处置记录） */
export interface LoadAlertEvent {
  id: number
  eventNo: string
  lineId: number
  lineCode: string
  lineName: string
  /** 触发时级别：WARNING / OVERLOAD */
  alertLevel: LineLoadStatus
  /** 触发时负载快照 */
  snapshot?: LineLoadStats | null
  status: AlertHandleStatus
  responsiblePerson?: string | null
  handlePlan?: string | null
  remark?: string | null
  /** 关闭方式：MANUAL-手动 / AUTO-系统自动 */
  closeType?: 'MANUAL' | 'AUTO' | null
  closeRemark?: string | null
  closedBy?: string | null
  triggerTime: string
  confirmTime?: string | null
  closeTime?: string | null
  createTime: string
  updateTime: string
  /** 产线当前实时负载状态 */
  currentLineStatus?: LineLoadStatus | null
  logs: AlertHandleLog[]
}

export interface LineLoadDetail {
  stats: LineLoadStats
  springs: SpringArchive[]
  recentTransfers: TransferRecord[]
  /** 当前未关闭告警事件（无则 null） */
  openAlertEvent?: LoadAlertEvent | null
  /** 该产线全部历史告警事件（按触发时间倒序） */
  alertEvents?: LoadAlertEvent[]
}

export interface AlertDispositionRequest {
  /** CONFIRM-确认责任人与处置计划 / RESOLVE-处理完成关闭 */
  action: 'CONFIRM' | 'RESOLVE'
  operator: string
  responsiblePerson?: string
  handlePlan?: string
  remark?: string
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
  /** 封存状态：NONE-正常 SEALED-封存中（封存期间禁止划转申请与调拨模拟） */
  sealStatus?: SealStatus
  sealReason?: string
  sealExpectedUnsealDate?: string
  sealOperator?: string
  sealTime?: string
  unsealOperator?: string
  unsealTime?: string
  unsealConclusion?: string
  createTime: string
  updateTime: string
}

/** 封存状态：正常 / 封存中 */
export type SealStatus = 'NONE' | 'SEALED'

/** 封存登记：质量员登记封存原因与预计解封日 */
export interface SealRequest {
  operator: string
  reason: string
  expectedUnsealDate: string
}

/** 解封：必须填写结论 */
export interface UnsealRequest {
  operator: string
  conclusion: string
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
  /** 加急标记：调度员标记后审批台优先展示；结案（全部通过/驳回）后自动解除 */
  urgent?: boolean
  urgentReason?: string | null
  urgentOperator?: string | null
  urgentTime?: string | null
  totalCount?: number
  pendingCount?: number
  approvedCount?: number
  rejectedCount?: number
  /** 目标产线当前是否临时停台（列表/详情实时挂接） */
  toLineHalted?: boolean
  toLineHaltReason?: string | null
  toLineExpectedResumeTime?: string | null
}

/** 标记加急 / 取消加急请求：加急填原因，取消填说明，均记录操作人（调度员） */
export interface UrgentRequest {
  operator: string
  reason: string
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
  action: 'SUBMIT' | 'APPROVE' | 'REJECT' | 'URGENT' | 'URGENT_CANCEL'
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

export type SimulationStatus = 'DRAFT' | 'ADOPTED' | 'DISCARDED'

export interface LineSimulationEstimate {
  lineId: number
  lineCode: string
  lineName: string
  direction: 'IN' | 'OUT'
  moveInCount: number
  moveOutCount: number
  currentCount: number
  simulatedCount: number
  dailyCapacityThreshold?: number | null
  currentLoadRate?: number | null
  simulatedLoadRate?: number | null
  currentOutOfRangeCount: number
  simulatedOutOfRangeCount: number
  currentStatus: LineLoadStatus
  simulatedStatus: LineLoadStatus
  reasons: string[]
}

export interface SimulationEstimate {
  toLineId: number
  toLineName: string
  springCount: number
  lines: LineSimulationEstimate[]
}

export interface TransferSimulation {
  id: number
  simulationNo: string
  operator: string
  toLineId: number
  toLineName: string
  remark?: string
  status: SimulationStatus
  applicationId?: number
  applicationNo?: string
  applicationStatus?: ApplicationStatus
  itemCount?: number
  createTime: string
  updateTime: string
}

export interface TransferSimulationItem {
  id: number
  simulationId: number
  springId: number
  springCode: string
  model: string
  elasticCoefficient: number
  fromLineId: number
  fromLineName: string
}

export interface SimulationDetail {
  simulation: TransferSimulation
  items: TransferSimulationItem[]
  estimate: SimulationEstimate | null
}

export interface SaveSimulationRequest {
  springIds: number[]
  toLineId: number
  operator: string
  remark?: string
}

export interface AdoptSimulationRequest {
  applicant: string
  reason?: string
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
