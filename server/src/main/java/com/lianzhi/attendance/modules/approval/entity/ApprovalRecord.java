package com.lianzhi.attendance.modules.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人员工ID */
    private Long empId;

    /** leave事假/sick病假/annual年假/overtime加班/compensate调休/makeup补卡/resign离职 */
    private String type;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 时长（小时） */
    private BigDecimal durationHours;

    /** 事由 */
    private String reason;

    /** 附件URL，多个逗号分隔 */
    private String attachments;

    /** 直属主管ID */
    private Long leaderId;

    /** pending/approved/rejected/cancelled */
    private String status;

    /** 驳回意见 */
    private String rejectReason;

    /** 审批人ID */
    private Long approverId;

    private LocalDateTime approveTime;

    /** 离职原因（仅离职类型） */
    private String resignReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ---- 非数据库字段 ----

    @TableField(exist = false)
    private String empName;

    @TableField(exist = false)
    private String empNo;

    @TableField(exist = false)
    private String empDept;

    @TableField(exist = false)
    private String empPhone;

    @TableField(exist = false)
    private String leaderName;

    @TableField(exist = false)
    private String approverName;

    /** 申请类型中文 */
    @TableField(exist = false)
    private String typeText;

    /** 状态中文 */
    @TableField(exist = false)
    private String statusText;
}
