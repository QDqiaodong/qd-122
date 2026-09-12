package com.spring.transfer.dto;

import lombok.Data;
import java.util.List;

/**
 * 接班看板门禁：接班人提交新划转前必须先确认昨夜复核单。
 * blocked=true 时仍存在待确认复核单，划转提交被拦截；pendingReviews 给出待确认单摘要。
 */
@Data
public class ReviewGuardResponse {
    /** 是否放行（无待确认复核单） */
    private boolean allowed;
    /** 待确认复核单数 */
    private int pendingCount;
    /** 待确认复核单（按签发时间倒序），用于看板提示与跳转核对 */
    private List<NightLoadReviewResponse> pendingReviews;
}
