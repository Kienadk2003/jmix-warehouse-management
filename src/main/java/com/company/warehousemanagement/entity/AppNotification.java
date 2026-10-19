package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistent website notification addressed to exactly one user.
 *
 * <p>The text shown in the UI is NOT stored. Only the type and the parameters are
 * stored, so the title/body are rendered from the message bundle in the language
 * the user currently uses (EN/VI).</p>
 *
 * <p>The recipient is stored as the unique username instead of a foreign key so the
 * notification history survives user maintenance and the table has no coupling to
 * USER_.</p>
 */
@JmixEntity
@Table(name = "APP_NOTIFICATION", indexes = {
        @Index(name = "IDX_APP_NOTIF_RECIPIENT_READ", columnList = "RECIPIENT_USERNAME, READ_FLAG"),
        @Index(name = "IDX_APP_NOTIF_RECIPIENT_CREATED", columnList = "RECIPIENT_USERNAME, CREATED_AT")
})
@Entity
public class AppNotification extends BaseUuidEntity {

    @Column(name = "RECIPIENT_USERNAME", nullable = false)
    private String recipientUsername;

    @Column(name = "TYPE", nullable = false, length = 40)
    private String type;

    /** Document kind: IMPORT, EXPORT, TRANSFER, ADJUSTMENT or STOCKTAKE. */
    @Column(name = "SUBJECT_TYPE", length = 20)
    private String subjectType;

    @InstanceName
    @Column(name = "SUBJECT_NO", length = 50)
    private String subjectNo;

    @Column(name = "ACTOR_NAME")
    private String actorName;

    @Column(name = "DETAIL", length = 500)
    private String detail;

    @Column(name = "TARGET_VIEW_ID", length = 100)
    private String targetViewId;

    @Column(name = "TARGET_ROUTE", length = 255)
    private String targetRoute;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "READ_FLAG", nullable = false)
    private Boolean readFlag = false;

    @Column(name = "READ_AT")
    private OffsetDateTime readAt;

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public NotificationType getType() {
        return type == null ? null : NotificationType.fromId(type);
    }

    public void setType(NotificationType type) {
        this.type = type == null ? null : type.getId();
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectNo() {
        return subjectNo;
    }

    public void setSubjectNo(String subjectNo) {
        this.subjectNo = subjectNo;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getTargetViewId() {
        return targetViewId;
    }

    public void setTargetViewId(String targetViewId) {
        this.targetViewId = targetViewId;
    }

    public String getTargetRoute() {
        return targetRoute;
    }

    public void setTargetRoute(String targetRoute) {
        this.targetRoute = targetRoute;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getReadFlag() {
        return readFlag;
    }

    public void setReadFlag(Boolean readFlag) {
        this.readFlag = readFlag;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(OffsetDateTime readAt) {
        this.readAt = readAt;
    }
}
