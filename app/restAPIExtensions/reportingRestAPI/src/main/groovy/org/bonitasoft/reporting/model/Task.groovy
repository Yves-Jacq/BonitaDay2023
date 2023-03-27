package org.bonitasoft.reporting.model

class Task {
    private Long sourceObjectId
    private String name
    private String type
    private Long caseId
    private Long creationDate
    private Long archivedDate
    private String state

    Task(Long sourceObjectId, String name, String type, Long caseId, Long creationDate, Long archivedDate, String state) {
        this.sourceObjectId = sourceObjectId
        this.name = name
        this.type = type
        this.caseId = caseId
        this.creationDate = creationDate
        this.archivedDate = archivedDate
        this.state = state
    }

    String getSourceObjectId() {
        return sourceObjectId
    }

    void setSourceObjectId(String sourceObjectId) {
        this.sourceObjectId = sourceObjectId
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getType() {
        return type
    }

    void setType(String type) {
        this.type = type
    }

    String getCaseId() {
        return caseId
    }

    void setCaseId(String caseId) {
        this.caseId = caseId
    }

    String getCreationDate() {
        return creationDate
    }

    void setCreationDate(String creationDate) {
        this.creationDate = creationDate
    }

    String getArchivedDate() {
        return archivedDate
    }

    void setArchivedDate(String archivedDate) {
        this.archivedDate = archivedDate
    }

    String getState() {
        return state
    }

    void setState(String state) {
        this.state = state
    }
}
