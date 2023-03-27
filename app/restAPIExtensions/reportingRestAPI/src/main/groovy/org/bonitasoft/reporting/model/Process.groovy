package org.bonitasoft.reporting.model

class Process {
    private String processDefId
    private String name
    private String version
    private String displayName
    private Boolean enabled
	private String deployedDate

    Process(Long processDefId, String name, String version, String displayName, String enabled, Long deployedDate) {
        super()
        this.processDefId = processDefId.toString()
        this.name = name
        this.version = version
        this.displayName = displayName
        this.enabled = ('ENABLED' == enabled)
		this.deployedDate = deployedDate
    }

    String getProcessDefId() {
        return processDefId
    }

    void setProcessDefId(Long processDefId) {
        this.processDefId = processDefId.toString()
    }

    void setProcessDefId(String processDefId) {
        this.processDefId = processDefId
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getVersion() {
        return version
    }

    void setVersion(String version) {
        this.version = version
    }

    String getDisplayName() {
        return displayName
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName
    }

    Boolean getEnabled() {
        return enabled
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled
    }

    void setEnabled(String enabled) {
        this.enabled = ('ENABLED' == enabled)
    }
	
	String getDeployedDate() {
		return deployedDate
	}
	
	void setDeployedDate(Long deployedDate) {
		this.deployedDate = deployedDate.toString()
	}

}
