package org.bonitasoft.reporting.model

import groovy.transform.Canonical

@Canonical
class GetProcessFilters {
    long tenantId = 0L
    Boolean isLatest = false
    Boolean isEnabled
    String version
    String search
	String name
    int count
    int page
}
