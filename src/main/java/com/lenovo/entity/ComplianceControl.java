package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * @TableName compliance_control
 */
@TableName(value = "compliance_control")
@Data
public class ComplianceControl implements Serializable {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     *
     */
    private String categoryId;

    /**
     *
     */
    private String category;

    /**
     *
     */
    private String complianceId;

    /**
     *
     */
    private String complianceControl;

    /**
     *
     */
    private Boolean controlStatus;

    /**
     *
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     *
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     *
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String lastModifier;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        ComplianceControl other = (ComplianceControl) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
                && (this.getCategoryId() == null ? other.getCategoryId() == null : this.getCategoryId().equals(other.getCategoryId()))
                && (this.getCategory() == null ? other.getCategory() == null : this.getCategory().equals(other.getCategory()))
                && (this.getComplianceId() == null ? other.getComplianceId() == null : this.getComplianceId().equals(other.getComplianceId()))
                && (this.getComplianceControl() == null ? other.getComplianceControl() == null : this.getComplianceControl().equals(other.getComplianceControl()))
                && (this.getControlStatus() == null ? other.getControlStatus() == null : this.getControlStatus().equals(other.getControlStatus()))
                && (this.getUpdateTime() == null ? other.getUpdateTime() == null : this.getUpdateTime().equals(other.getUpdateTime()))
                && (this.getCreateTime() == null ? other.getCreateTime() == null : this.getCreateTime().equals(other.getCreateTime()))
                && (this.getLastModifier() == null ? other.getLastModifier() == null : this.getLastModifier().equals(other.getLastModifier()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getCategoryId() == null) ? 0 : getCategoryId().hashCode());
        result = prime * result + ((getCategory() == null) ? 0 : getCategory().hashCode());
        result = prime * result + ((getComplianceId() == null) ? 0 : getComplianceId().hashCode());
        result = prime * result + ((getComplianceControl() == null) ? 0 : getComplianceControl().hashCode());
        result = prime * result + ((getControlStatus() == null) ? 0 : getControlStatus().hashCode());
        result = prime * result + ((getUpdateTime() == null) ? 0 : getUpdateTime().hashCode());
        result = prime * result + ((getCreateTime() == null) ? 0 : getCreateTime().hashCode());
        result = prime * result + ((getLastModifier() == null) ? 0 : getLastModifier().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", categoryId=").append(categoryId);
        sb.append(", category=").append(category);
        sb.append(", complianceId=").append(complianceId);
        sb.append(", complianceControl=").append(complianceControl);
        sb.append(", controlStatus=").append(controlStatus);
        sb.append(", updateTime=").append(updateTime);
        sb.append(", createTime=").append(createTime);
        sb.append(", lastModifier=").append(lastModifier);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}