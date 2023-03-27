package org.bonitasoft.reporting.services

import org.bonitasoft.reporting.conf.Configuration
import org.bonitasoft.reporting.model.GetProcessFilters

class ReportingServiceQueries {

    static getCaseAvg(Map<String, Object> filters) {
        def id = filters["process"] as Long
        def start = filters["start"]
        def end = filters["end"]
        def tenantId = filters["tenantId"] as Long
        GString sql = GString.EMPTY + """
                       SELECT CS.PROCESSDEFINITIONID AS CS_PROCESS_DEFINITION_ID,
                            CS.STARTDATE AS CS_START_DATE,
                            CS.ENDDATE AS CS_END_DATE,
                            ( CS.ENDDATE - CS.STARTDATE ) AS CS_DURATION
                       FROM arch_process_instance CS
                       WHERE CS.ENDDATE > 0
                            AND CS.TENANTID = ${tenantId}
                            AND CS.PROCESSDEFINITIONID = ${id}"""
        if (filters["filterBy"] == "startDate") {
            sql += " AND CS.STARTDATE BETWEEN ${start} AND ${end} "
        } else {
            sql += " AND CS.ENDDATE BETWEEN ${start} AND ${end} "
        }
        sql += "ORDER BY CS.STARTDATE ASC"
        return sql
    }

    static getStatesOfHumanTasks(Map<String, Object> filters) {
        def id = filters["process"] as Long
        def start = filters["start"]
        def end = filters["end"]
        def tenantId = filters["tenantId"] as Long

        GString sql = GString.EMPTY + """
                        SELECT *
                        FROM (
                            SELECT 
                                AFI.SOURCEOBJECTID AS F_ID,
                                MIN(FIRST_AFI.REACHEDSTATEDATE) AS F_REACHEDSTATEDATE,
                                AFI.STATENAME AS F_STATENAME
                            FROM arch_process_instance API
                            INNER JOIN arch_flownode_instance AFI
                                ON AFI.LOGICALGROUP4 = API.SOURCEOBJECTID
                                AND AFI.TENANTID = API.TENANTID 
                                AND AFI.KIND IN ('user','manual') 
                                AND LOWER(CAST(AFI.TERMINAL AS CHAR)) IN ('t','true','y','1')
                                AND AFI.REACHEDSTATEDATE BETWEEN ${start} AND ${end}
                            INNER JOIN arch_flownode_instance FIRST_AFI
                                ON AFI.SOURCEOBJECTID = FIRST_AFI.SOURCEOBJECTID
                                AND AFI.TENANTID = FIRST_AFI.TENANTID 
                                AND FIRST_AFI.STATENAME = 'initializing'
                            WHERE API.TENANTID = ${tenantId}
                                AND AFI.LOGICALGROUP1 = ${id}
                            GROUP BY AFI.SOURCEOBJECTID,AFI.STATENAME
                            UNION
                            SELECT 
                                FI.ID AS F_ID,
                                MIN(FIRST_AFI.REACHEDSTATEDATE) AS F_REACHEDSTATEDATE,
                                FI.STATENAME AS F_STATENAME
                            FROM arch_process_instance API
                            INNER JOIN flownode_instance FI
                                ON FI.LOGICALGROUP4 = API.SOURCEOBJECTID 
                                    AND FI.TENANTID = API.TENANTID 
                                    AND FI.KIND IN ('user','manual')
                                    AND FI.STATENAME IN ('ready', 'failed') 
                                    AND FI.REACHEDSTATEDATE BETWEEN ${start} AND ${end}
                            INNER JOIN arch_flownode_instance FIRST_AFI
                                ON FI.ID = FIRST_AFI.SOURCEOBJECTID
                                AND FI.TENANTID = FIRST_AFI.TENANTID 
                                AND FIRST_AFI.STATENAME = 'initializing'
                            WHERE API.TENANTID = ${tenantId}
                                AND FI.LOGICALGROUP1 = ${id}
                            GROUP BY FI.ID,FI.STATENAME
                        ) FLOWNODES
                        ORDER BY F_REACHEDSTATEDATE ASC
                    """
        return sql
    }

    static exportCaseAvg(Map<String, Object> filters) {
        def id = filters["process"] as Long
        def start = filters["start"]
        def end = filters["end"]
        def tenantId = filters["tenantId"] as Long
        GString sql = GString.EMPTY + """
                        SELECT distinct 
                              PD.NAME AS PD_NAME, 
                              PD.VERSION AS PD_VERSION,
                              CS.ID AS CS_ID,
                              CS.SOURCEOBJECTID AS CS_SOURCE_OBJECT_ID,
                              CS.CALLERID AS CS_CALLER_ID,
                              CS.STARTDATE AS CS_START_DATE,
                              CS.STARTEDBY AS CS_STARTED_BY,
                              coalesce(U.USERNAME,'') AS CS_STARTED_BY_USERNAME,
                              CS.ROOTPROCESSINSTANCEID AS CS_ROOT_PROCESS_INSTANCE_ID,
                              CS.LASTUPDATE AS CS_LAST_UPDATE_DATE,
                              CS.ENDDATE AS CS_END_DATE,
                              CS.STATEID AS CS_STATEID,
                              ( CS.ENDDATE - CS.STARTDATE ) AS CS_DURATION,
                              CS.PROCESSDEFINITIONID AS CS_PROCESS_DEFINITION_ID,
                              coalesce(AFI.NAME,'Unknown') AS CS_CALLING_ACTIVITY_NAME,
                              coalesce(ROOT_CS.NAME,'Unknown') as CS_ROOT_PROCESS_INSTANCE_NAME,
                              coalesce(COMPLETED.NBFLOWNODE,0) as FN_COMPLETED,
                              coalesce(FAILED.NBFLOWNODE,0) as FN_FAILED,
                              coalesce(CANCELLED.NBFLOWNODE,0) as FN_CANCELLED
                       FROM arch_process_instance CS
                       JOIN process_definition PD
                             ON CS.PROCESSDEFINITIONID = PD.PROCESSID and PD.TENANTID = ${tenantId} 
                       LEFT JOIN user_ U
                            ON U.ID = CS.STARTEDBY AND U.TENANTID = ${tenantId} 
                       LEFT JOIN arch_flownode_instance AFI
                            ON AFI.SOURCEOBJECTID = CS.CALLERID AND AFI.TENANTID = ${tenantId} 
                       LEFT JOIN arch_process_instance ROOT_CS
                            ON ROOT_CS.sourceobjectid = CS.rootprocessinstanceid AND ROOT_CS.TENANTID = ${tenantId} 
                       LEFT OUTER JOIN (
                             SELECT AFIC.logicalgroup4 AS CASEID, count(*) AS NBFLOWNODE
                             FROM arch_flownode_instance AFIC
                             WHERE AFIC.statename = 'completed' AND AFIC.TENANTID = ${tenantId} 
                             GROUP BY AFIC.logicalgroup4
                       ) COMPLETED 
                       ON COMPLETED.CASEID  = CS.SOURCEOBJECTID 
                       LEFT OUTER JOIN (
                            SELECT AFIF.logicalgroup4 AS CASEID, count(*) AS NBFLOWNODE
                             FROM arch_flownode_instance AFIF
                             WHERE AFIF.statename = 'failed' AND AFIF.TENANTID = ${tenantId} 
                             GROUP BY AFIF.logicalgroup4
                       ) FAILED 
                       ON FAILED.CASEID  = CS.SOURCEOBJECTID
                       LEFT OUTER JOIN (
                             SELECT AFICA.logicalgroup4 AS CASEID, count(*) AS NBFLOWNODE
                             FROM arch_flownode_instance AFICA
                             WHERE AFICA.statename = 'cancelled' AND AFICA.TENANTID = ${tenantId} 
                             GROUP BY AFICA.logicalgroup4
                       ) CANCELLED 
                       ON CANCELLED.CASEID  = CS.SOURCEOBJECTID
                       WHERE CS.ENDDATE > 0
                             AND CS.TENANTID = ${tenantId}
                             AND CS.PROCESSDEFINITIONID = ${id}"""
        if (filters["filterBy"] == "startDate") {
            sql += " AND CS.STARTDATE BETWEEN ${start} AND ${end} "
        } else {
            sql += " AND CS.ENDDATE BETWEEN ${start} AND ${end} "
        }
        sql += "ORDER BY CS.STARTDATE ASC"
        return sql
    }

    static exportTasks(Map<String, Object> filters) {
        def id = filters["process"] as Long
        def start = filters["start"]
        def end = filters["end"]
        def now = System.currentTimeMillis();
        def tenantId = filters["tenantId"] as Long
        GString sql = GString.EMPTY + """
                        SELECT *
                        FROM (
                            SELECT 
                                AFI.SOURCEOBJECTID AS F_ID,
                                AFI.NAME AS F_NAME,
                                AFI.DISPLAYNAME AS F_DISPLAYNAME,
                                MIN(FIRST_AFI.REACHEDSTATEDATE) AS F_REACHEDSTATEDATE,
                                AFI.ARCHIVEDATE AS F_ENDDATE,
                                AFI.LOGICALGROUP4 AS F_CASEID,
                                coalesce(PD.NAME, '') AS PD_NAME,
                                coalesce(PD.VERSION, '') AS PD_VERSION,
                                coalesce(AU.FIRSTNAME, '') AS AU_FIRSTNAME,
                                coalesce(AU.LASTNAME, '') AS AU_LASTNAME,
                                coalesce(AU.USERNAME, '') AS AU_USERNAME,
                                coalesce(EU.FIRSTNAME, '') AS EU_FIRSTNAME,
                                coalesce(EU.LASTNAME, '') AS EU_LASTNAME,
                                coalesce(EU.USERNAME, '') AS EU_USERNAME,
                                AFI.STATENAME AS F_STATE,
                                AFI.KIND AS F_KIND,
                                API.SOURCEOBJECTID AS P_ID,
                                coalesce(PAFI.NAME, '') AS PF_NAME,
                                coalesce(AFI.EXPECTEDENDDATE, 0) AS F_DUEDATE, 
                                (CASE
                                    WHEN (AFI.EXPECTEDENDDATE >= AFI.ARCHIVEDATE) THEN 'false'
                                    WHEN (AFI.EXPECTEDENDDATE < AFI.ARCHIVEDATE) THEN 'true'
                                    ELSE ''
                                END) AS F_OVERDUE_FLAG
                            FROM arch_process_instance API
                            INNER JOIN arch_flownode_instance AFI
                                ON AFI.LOGICALGROUP4 = API.SOURCEOBJECTID
                                AND AFI.TENANTID = API.TENANTID 
                                AND AFI.KIND IN ('user','manual') 
                                AND LOWER(CAST(AFI.TERMINAL AS CHAR)) IN ('t','true','y','1')
                                AND AFI.REACHEDSTATEDATE BETWEEN ${start} AND ${end}
                            INNER JOIN arch_flownode_instance FIRST_AFI
                                ON AFI.SOURCEOBJECTID = FIRST_AFI.SOURCEOBJECTID
                                AND AFI.TENANTID = FIRST_AFI.TENANTID 
                                AND FIRST_AFI.STATENAME = 'initializing'
                            LEFT JOIN process_definition PD
                                ON AFI.LOGICALGROUP1 = PD.PROCESSID AND PD.TENANTID = ${tenantId}
                            LEFT JOIN user_ AU
                                ON AFI.ASSIGNEEID = AU.ID AND AU.TENANTID = ${tenantId}
                            LEFT JOIN user_ EU
                                ON AFI.EXECUTEDBY = EU.ID AND EU.TENANTID = ${tenantId}
                            LEFT JOIN arch_flownode_instance PAFI
                                ON AFI.KIND = 'manual'
                                AND AFI.PARENTCONTAINERID = PAFI.SOURCEOBJECTID  AND PAFI.TENANTID = ${tenantId}
                            WHERE API.TENANTID = ${tenantId}
                                AND AFI.LOGICALGROUP1 = ${id}
                            GROUP BY AFI.SOURCEOBJECTID,AFI.NAME,AFI.DISPLAYNAME,AFI.ARCHIVEDATE,AFI.LOGICALGROUP4,PD.NAME,PD.VERSION,AU.FIRSTNAME,AU.LASTNAME,AU.USERNAME,EU.FIRSTNAME,EU.LASTNAME,EU.USERNAME,AFI.STATENAME,AFI.KIND,API.SOURCEOBJECTID,PAFI.NAME,AFI.EXPECTEDENDDATE
                            UNION
                            SELECT 
                                FI.ID AS F_ID,
                                FI.NAME AS F_NAME,
                                FI.DISPLAYNAME AS F_DISPLAYNAME,
                                MIN(FIRST_AFI.REACHEDSTATEDATE) AS F_REACHEDSTATEDATE,
                                0 AS F_ENDDATE,
                                FI.LOGICALGROUP4 AS F_CASEID,
                                coalesce(PD.NAME, '') AS PD_NAME,
                                coalesce(PD.VERSION, '') AS PD_VERSION,
                                coalesce(AU.FIRSTNAME, '') AS AU_FIRSTNAME,
                                coalesce(AU.LASTNAME, '') AS AU_LASTNAME,
                                coalesce(AU.USERNAME, '') AS AU_USERNAME,
                                coalesce(EU.FIRSTNAME, '') AS EU_FIRSTNAME,
                                coalesce(EU.LASTNAME, '') AS EU_LASTNAME,
                                coalesce(EU.USERNAME, '') AS EU_USERNAME,
                                FI.STATENAME AS F_STATE,
                                FI.KIND AS F_KIND,
                                API.SOURCEOBJECTID AS P_ID,
                                coalesce(PFI.NAME, '') AS PF_NAME,
                                coalesce(FI.EXPECTEDENDDATE, 0) AS F_DUEDATE,
                                (CASE
                                    WHEN (FI.EXPECTEDENDDATE >= ${now}) THEN 'false'
                                    WHEN (FI.EXPECTEDENDDATE < ${now}) THEN 'true'
                                    ELSE ''
                                END) AS F_OVERDUE_FLAG
                            FROM arch_process_instance API
                            INNER JOIN flownode_instance FI
                                ON FI.LOGICALGROUP4 = API.SOURCEOBJECTID 
                                    AND FI.TENANTID = API.TENANTID 
                                    AND FI.KIND IN ('user','manual')
                                    AND FI.STATENAME IN ('ready', 'failed') 
                                    AND FI.REACHEDSTATEDATE BETWEEN ${start} AND ${end}
                            INNER JOIN arch_flownode_instance FIRST_AFI
                                ON FI.ID = FIRST_AFI.SOURCEOBJECTID
                                AND FI.TENANTID = FIRST_AFI.TENANTID 
                                AND FIRST_AFI.STATENAME = 'initializing'
                            LEFT JOIN process_definition PD
                                ON FI.LOGICALGROUP1 = PD.PROCESSID AND PD.TENANTID = ${tenantId}
                            LEFT JOIN user_ AU
                                ON FI.ASSIGNEEID = AU.ID AND AU.TENANTID = ${tenantId}
                            LEFT JOIN user_ EU
                                ON FI.EXECUTEDBY = EU.ID AND EU.TENANTID = ${tenantId}
                            LEFT JOIN flownode_instance PFI
                                ON FI.KIND = 'manual'
                                AND FI.PARENTCONTAINERID = PFI.LOGICALGROUP4 AND PFI.TENANTID = ${tenantId}
                            WHERE API.TENANTID = ${tenantId}
                                AND FI.LOGICALGROUP1 = ${id}
                            GROUP BY FI.ID,FI.NAME,FI.DISPLAYNAME,FI.LOGICALGROUP4,PD.NAME,PD.VERSION,AU.FIRSTNAME,AU.LASTNAME,AU.USERNAME,EU.FIRSTNAME,EU.LASTNAME,EU.USERNAME,FI.STATENAME,FI.KIND,API.SOURCEOBJECTID,PFI.NAME,FI.EXPECTEDENDDATE
                        ) FLOWNODES
                        ORDER BY F_REACHEDSTATEDATE ASC, F_NAME ASC
        """
        return sql
    }


    static getProcessesQuery(GetProcessFilters filter) {
        if (!filter.tenantId) {
            throw new IllegalArgumentException("'tenantId' must be set")
        }
        GString query = GString.EMPTY + """SELECT pd.processId , pd.name , pd.version , pd.displayName , pd.activationState, pd.deploymentDate 
                           FROM process_definition pd"""

        if (filter.isLatest) {
            query += """ INNER JOIN (
                                    SELECT name, MAX(deploymentDate) latest
                                    FROM process_definition
                                    WHERE tenantid = ${filter.tenantId}
                                    GROUP BY name
                                ) ij ON pd.name = ij.name AND pd.deploymentDate = ij.latest
                            """
        }
        query += " WHERE pd.tenantid = ${filter.tenantId} AND "
        if (filter.version) {
            query += "pd.version = ${filter.version} AND "
        }
        if (filter.isEnabled != null) {
            def activationStateVal = filter.isEnabled ? "ENABLED" : "DISABLED"
            query += "pd.activationState = ${activationStateVal} AND "
        }
        if (filter.search) {
            def searchCriteria = "%${filter.search}%" as String
            query += "(pd.name LIKE ${searchCriteria} OR pd.displayName LIKE ${searchCriteria}) AND "
        }
        if (filter.name) {
            def name = "${filter.name}" as String
            query += "(pd.name = ${name}) AND "
        }
        query += "1=1 ORDER BY pd.name ASC, pd.version DESC"
        return query
    }
}

