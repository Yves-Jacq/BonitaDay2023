package org.bonitasoft.reporting.conf

import groovy.transform.Canonical
import org.bonitasoft.web.extension.rest.RestAPIContext

import javax.naming.InitialContext
import javax.sql.DataSource

@Canonical
class Configuration {

    DataSource dataSource
    Long tenantId

    private static final Map<Long, Configuration> INSTANCES = [:]

    static Configuration getInstance(Long tenantId) {
        if (!INSTANCES.containsKey(tenantId)) {
            def dataSourceName = System.getProperty("sysprop.bonita.database.sequence.manager.datasource.name", "java:comp/env/bonitaSequenceManagerDS")
            DataSource datasource = (DataSource) new InitialContext().lookup(dataSourceName)
            INSTANCES.putIfAbsent(tenantId, new Configuration(datasource, tenantId))
        }
        INSTANCES.get(tenantId)
    }

}
