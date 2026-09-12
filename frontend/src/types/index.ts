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
  /**
   * 加急待批数：加急申请单下剩余待审批明细行之和（与审批台逐行口径一致）。
   * 部分处理单的已处理行不再占用加急名额
   */
  urgentPendingCount: number
  /** 仍含剩余待批行的加急申请单数（审批台「仅加急」列表可见单量） */
  urgentPendingApplicationCount: number
  /** 仍带加急标记但已无剩余待批行的残留申请单数 */
  urgentStaleCount: number
  /** 看板计数与审批台剩余待批口径是否一致；false 时以 urgentPendingMismatchReasons 说明原因 */
  urgentPendingAligned: boolean
  /** 加急待批计数对不齐的明确原因 */
  urgentPendingMismatchReasons: string[]
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
  /** 黄标：存在偏离且未闭环的弹力抽检留样（闭环处置前不能勾进划转申请，标记实时计算刷新后仍在） */
  yellowFlag?: boolean
  /** 未闭环偏离留样条数 */
  openDeviationCount?: number
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

// ---------------------------------------------------------------------
// 弹力抽检留样
// ---------------------------------------------------------------------

/** 留样单状态：OPEN-待闭环 CLOSED-已闭环（已闭环实测系数不可改） */
export type SampleStatus = 'OPEN' | 'CLOSED'

/** 弹力抽检留样单 */
export interface ElasticSample {
  id: number
  sampleNo: string
  springId: number
  springCode: string
  model: string
  /** 登记时所在产线（快照） */
  lineId: number
  lineCode: string
  lineName: string
  measuredCoefficient: number
  lineElasticMin?: number | null
  lineElasticMax?: number | null
  /** 是否偏离该线适用区间 */
  deviated: boolean
  status: SampleStatus
  operator: string
  /** 处置结论：不写不能闭环 */
  conclusion?: string | null
  closeOperator?: string | null
  closeTime?: string | null
  createTime: string
  updateTime: string
  /** 弹簧当前所在产线名称（留样产线为登记时快照） */
  springCurrentLineName?: string | null
}

/** 登记留样：质量员按产线登记（lineId 缺省取弹簧当前产线） */
export interface RegisterSampleRequest {
  springId: number
  lineId?: number | null
  measuredCoefficient: number
  operator: string
}

/** 修改实测系数（仅待闭环可改） */
export interface UpdateMeasuredRequest {
  measuredCoefficient: number
}

/** 闭环：处置结论必填 */
export interface CloseSampleRequest {
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

// ---------------------------------------------------------------------
// 夜班承载复核单
// ---------------------------------------------------------------------

/** 复核单状态：PENDING-待确认 CONFIRMED-已确认（确认后数字锁定不可改） */
export type ReviewStatus = 'PENDING' | 'CONFIRMED'

/** 复核单上「次日必须跟进的待批划转」单条明细（签发时快照） */
export interface PendingTransferItem {
  applicationId: number
  applicationNo: string
  applicant: string
  springCode: string
  model: string
  elasticCoefficient: number
  fromLineName: string
  reason: string
  applyTime?: string | null
  urgent: boolean
}

/** 夜班承载复核单（数字均为签发时点快照） */
export interface NightLoadReview {
  id: number
  reviewNo: string
  reviewDate: string
  lineId: number
  lineCode: string
  lineName: string
  /** 当前归属弹簧数（快照） */
  springCount: number
  dailyCapacityThreshold?: number | null
  loadRate?: number | null
  /** 是否压到日承载：当前数 ≥ 阈值 */
  capacityReached: boolean
  /** 是否超过日承载：当前数 > 阈值 */
  overCapacity: boolean
  /** 系数越界条数（快照） */
  outOfRangeCount: number
  /** 次日须跟进待批划转明细条数（快照） */
  pendingTransferCount: number
  /** 次日须跟进待批划转申请单数（快照） */
  pendingApplicationCount: number
  pendingTransferSnapshot?: string | null
  handoverRemark?: string | null
  operator: string
  issueTime: string
  status: ReviewStatus
  followUpNote?: string | null
  confirmer?: string | null
  confirmTime?: string | null
  createTime: string
  updateTime: string
}

/** 复核单详情：本体 + 反序列化后的待批划转快照明细 */
export interface NightLoadReviewDetail {
  review: NightLoadReview
  pendingTransfers: PendingTransferItem[]
}

/** 签发复核单请求 */
export interface CreateReviewRequest {
  lineId: number
  operator: string
  handoverRemark?: string
}

/** 确认复核单请求：跟进说明必填，不写不能确认 */
export interface ConfirmReviewRequest {
  confirmer: string
  followUpNote: string
}

/** 接班门禁：无待确认复核单才放行新划转 */
export interface ReviewGuard {
  allowed: boolean
  pendingCount: number
  pendingReviews: NightLoadReviewDetail[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
