package org.bonitasoft.reporting.services

import com.bonitasoft.engine.api.PlatformAPIAccessor
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.transform.Canonical
import org.apache.commons.lang3.StringUtils
import org.bonitasoft.reporting.conf.Configuration
import org.bonitasoft.reporting.model.GetProcessFilters
import org.bonitasoft.reporting.model.Process
import org.bonitasoft.reporting.model.Report
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Canonical
class ReportingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportingService.class)

    private static final String timezone = "GMT"
    private static final SimpleDateFormat dateFormatFull = new SimpleDateFormat("dd MMM yyyy HH:mm:ss")
    private static final SimpleDateFormat dateFormatOnlyDay = new SimpleDateFormat("yyyy-MM-dd")
    private static final SimpleDateFormat dateFormatOnlyHour = new SimpleDateFormat("HH:mm:ss.SSS")
    private static caseVisuUrl
    private static exportedFileName

    Configuration configuration

    private static final Map<Configuration, ReportingService> INSTANCES = [:]

    static ReportingService getInstance(Configuration configuration) {
        checkSpFeatures()
        setTimezonesIfNotSet()
        if (!INSTANCES.containsKey(Configuration)) {
            INSTANCES.putIfAbsent(configuration, new ReportingService(configuration))
        }
        INSTANCES.get(configuration)
    }

    private static void setTimezonesIfNotSet() {
        if (dateFormatFull.getTimeZone() != TimeZone.getTimeZone(timezone)) {
            dateFormatFull.setTimeZone(TimeZone.getTimeZone(timezone))
        }
        if (dateFormatOnlyDay.getTimeZone() != TimeZone.getTimeZone(timezone)) {
            dateFormatOnlyDay.setTimeZone(TimeZone.getTimeZone(timezone))
        }
        if (dateFormatOnlyHour.getTimeZone() != TimeZone.getTimeZone(timezone)) {
            dateFormatOnlyHour.setTimeZone(TimeZone.getTimeZone(timezone))
        }
    }

    Report runCaseAvgReport(Long start, Long end, Long processDefinitionId, String filterBy) {
        def params = ["tenantId" : configuration.tenantId, "start": start, "end": end,
                      "process": processDefinitionId, "filterBy": filterBy]

        def apiCallStart = System.currentTimeMillis()
        def sqlQuery = ReportingServiceQueries.getCaseAvg(params)
        def bucketSizeInMillis = bucketSizeInMillis(start, end)
        LOGGER.debug("Start = $start, End = $end")
        LOGGER.debug("Detected bucket size is: ${Duration.ofMillis(bucketSizeInMillis)}")
        List<Long> labels = calculateLabels(start, end, bucketSizeInMillis)
        LOGGER.debug("Detected buckets are: ${labels}")

        List<LongSummaryStatistics> durationStatistics = labels.collect { new LongSummaryStatistics() }
        List<MedianOfLongStream> medianStatistics = labels.collect { new MedianOfLongStream() }

        final def sql = new Sql(configuration.dataSource)
        def hasLines = false
        def sqlSartQuery = System.currentTimeMillis()
        def sqlSartProcessing = -1L
        try {
            sql.query(sqlQuery) { rs ->
                sqlSartProcessing = System.currentTimeMillis()
                while (rs.next()) {
                    if(!hasLines) {
						hasLines = true
					}
                    long caseTimestamp = rs.getLong(filterBy == "startDate" ? "CS_START_DATE" : "CS_END_DATE")
                    def index = getBucketIndexForTimestamp(labels, caseTimestamp)
                    def duration = rs.getLong("CS_DURATION")
                    durationStatistics[index].accept(duration)
                    medianStatistics[index].add(duration)
                }
            }
        } finally {
            sql.close()
			
			if(!hasLines) {
				return new Report(null, null, null, null)
			}
        }
        def sqlEnd = System.currentTimeMillis()
        List<Double> average = durationStatistics.collect { it.getAverage() }
        List<Double> median = medianStatistics.collect { it.getMedian() }

        def dataUnitList = calculateDataUnit(average, median)
        def dataUnit = dataUnitList[0] as long
        average = average.collect {
            (it / dataUnit).round(2)
        }
        median = median.collect {
            (it / dataUnit).round(2)
        }
        def apiCallEnd = System.currentTimeMillis()
        LOGGER.debug("Ran Case average report: \n" +
                "Number of buckets: ${labels.size()}\n" +
                "Total report generation time: ${Duration.ofMillis(apiCallEnd - apiCallStart)}\n" +
                "Query execution: ${Duration.ofMillis(sqlSartProcessing - sqlSartQuery)}\n" +
                "Query processing: ${Duration.ofMillis(sqlEnd - sqlSartProcessing)}\n" +
                "Query total: ${Duration.ofMillis(sqlEnd - sqlSartQuery)}\n" +
                "")
        return new Report([average, median], labels, ["mean", "median"], dataUnitList[1] as String)
    }

    Report runTaskStatesReport(Long start, Long end, Long processDefinitionId) {
        def params = ["tenantId" : configuration.tenantId, "start": start, "end": end,
                      "process": processDefinitionId]

        def apiCallStart = System.currentTimeMillis()
        def sqlQuery = ReportingServiceQueries.getStatesOfHumanTasks(params)
        def bucketSizeInMillis = bucketSizeInMillis(start, end)
        LOGGER.debug("Start = $start, End = $end")
        LOGGER.debug("Detected bucket size is: ${Duration.ofMillis(bucketSizeInMillis)}")
        List<Long> labels = calculateLabels(start, end, bucketSizeInMillis)
        LOGGER.debug("Detected buckets are: ${labels}")

        List<Long> doneTasks = labels.collect { 0 }
        List<Long> openTasks = labels.collect { 0 }
        List<Long> omittedTasks = labels.collect { 0 }
        List<Long> failedTasks = labels.collect { 0 }

        final def sql = new Sql(configuration.dataSource)
        def sqlSartQuery = System.currentTimeMillis()
        def sqlSartProcessing = -1L
		def hasLines = false
        try {
            sql.query(sqlQuery) { rs ->
                sqlSartProcessing = System.currentTimeMillis()
                while (rs.next()) {
					if(!hasLines) {
						hasLines = true
					}
                    long caseTimestamp = rs.getLong("F_REACHEDSTATEDATE")
                    def index = getBucketIndexForTimestamp(labels, caseTimestamp)
                    def state = rs.getString("F_STATENAME")

                    switch (state){
                        case "ready":
                            openTasks[index]++
                            break
                        case "completed":
                            doneTasks[index]++
                            break
                        case "canceled":
                        case "aborted":
                        case "skipped":
                            omittedTasks[index]++
                            break
                        case "failed":
                            failedTasks[index]++
                            break
                    }
                }
            }
        } finally {
            sql.close()
			
			if(!hasLines) {
				return null
			}
        }
        def sqlEnd = System.currentTimeMillis()

        def apiCallEnd = System.currentTimeMillis()
        LOGGER.debug("Ran Case average report: \n" +
                "Number of buckets: ${labels.size()}\n" +
                "Total report generation time: ${Duration.ofMillis(apiCallEnd - apiCallStart)}\n" +
                "Query execution: ${Duration.ofMillis(sqlSartProcessing - sqlSartQuery)}\n" +
                "Query processing: ${Duration.ofMillis(sqlEnd - sqlSartProcessing)}\n" +
                "Query total: ${Duration.ofMillis(sqlEnd - sqlSartQuery)}\n" +
                "")
        return new Report([openTasks, doneTasks, omittedTasks, failedTasks], labels, ["opened", "done", "omitted", "failed"])
    }

    static int getBucketIndexForTimestamp(List<Long> labels, long caseTimestamp) {
        def f = 0
        def l = labels.size()
        int mid = (f + l) / 2
        while (f <= l) {
            if (labels[mid] <= caseTimestamp && labels[mid + 1] > caseTimestamp) {
                return mid
            } else if (labels[mid + 1] <= caseTimestamp) {
                f = mid + 1
            } else {
                l = mid - 1
            }
            mid = (f + l) / 2
        }
        if (f > l) {
            return -1L
        }
    }


    String exportCaseAvgReport(Long start, Long end, Long processDefinitionId, String filterBy, String caseVisuUrl, String exportedFileName) {
        def params = ["tenantId" : configuration.tenantId, "start": start, "end": end,
                      "process": processDefinitionId, "filterBy": filterBy]

        final def sql = new Sql(configuration.dataSource)
        this.caseVisuUrl = caseVisuUrl
        this.exportedFileName = exportedFileName
        String result

        try {
            sql.query(ReportingServiceQueries.exportCaseAvg(params)) { rs ->
                result = getCaseAverageCsvZipBase64(rs)
            }
        } finally {
            sql.close()
        }
        result
    }

    private String getCaseAverageCsvZipBase64(ResultSet rs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        ZipOutputStream zos = new ZipOutputStream(baos)
		def hasLines = false
        try {
            zos.putNextEntry(new ZipEntry(this.exportedFileName + ".csv"))

            //write headers
            String headers = "Process name,Process version,Case id (original),Archived case id,Process type,Start date ($timezone),Started by,Start type,Last update date ($timezone),Status,End date ($timezone),Formatted case duration,Case duration,Flow nodes number executed,Flow nodes number error,Flow nodes number cancelled,Diagram URL\n";
            byte[] lineBytes = headers.getBytes()
            zos.write(lineBytes, 0, lineBytes.length)

            //write data rows
            while (rs.next()) {
				if(!hasLines) {
					hasLines = true
				}

                StringBuilder csvLine = new StringBuilder()
                csvLine.append(caseAverageSqlRowToCsvLine(rs).stream().map({ escapeSpecialCharacters(it as String) }).collect(Collectors.joining(","))).append("\n")
                lineBytes = csvLine.toString().getBytes()
                zos.write(lineBytes, 0, lineBytes.length)
            }
            zos.closeEntry()
        }
        finally {
            zos.close()

			if(!hasLines) {
				return null
			}
        }
        byte[] bytes = baos.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private List<String> caseAverageSqlRowToCsvLine(ResultSet row) {
        def rowList = new ArrayList()
        // Process name
        rowList.add(row.getString("PD_NAME"))
        // Process version
        rowList.add(row.getString("PD_VERSION"))
        // Original case id
        def sourceObjectId = row.getLong("CS_SOURCE_OBJECT_ID")
        rowList.add(sourceObjectId)
        // Case id
        rowList.add(row.getString("CS_ID"))
        // Process type
        if (row.getLong("CS_CALLER_ID") > 0) {
            rowList.add("Called")
        } else {
            rowList.add("Main")
        }
        // Start date
        rowList.add(dateFormatFull.format(new Date(row.getLong("CS_START_DATE"))))
        // Started by, Start type
        def rootCaseId = row.getLong("CS_ROOT_PROCESS_INSTANCE_ID")
        def startedBy = row.getLong("CS_STARTED_BY")
        if (rootCaseId == sourceObjectId && startedBy > 0) {
            rowList.add(row.getString("CS_STARTED_BY_USERNAME"))
            rowList.add("Human")
        } else if (rootCaseId == sourceObjectId && startedBy == 0) {
            rowList.add("Timer or message or signal")
            rowList.add("Event")
        } else if (rootCaseId != sourceObjectId && startedBy == 0) {
            rowList.add(row.getString("CS_ROOT_PROCESS_INSTANCE_NAME") + "($rootCaseId)" + "-" + row.getString("CS_CALLING_ACTIVITY_NAME"))
            rowList.add("Call activity")
        } else {
            // should not happen
            rowList.add("Unknown started by")
            rowList.add("Unknown start type")
        }
        // Last update date
        rowList.add(dateFormatFull.format(new Date(row.getLong("CS_LAST_UPDATE_DATE"))))
        // Status
        def stateIdInt = row.getInt("CS_STATEID")
        rowList.add('stateId to string correspondence table'(stateIdInt))
        // End date
        rowList.add(dateFormatFull.format(new Date(row.getLong("CS_END_DATE"))))
        // Formatted case duration
        rowList.add(formatDuration(Duration.ofMillis(row.getLong("CS_DURATION"))))
        // Case duration
        rowList.add(row.getString("CS_DURATION"))
        // Flow nodes number executed
        rowList.add(row.getString("FN_COMPLETED"))
        // Flow nodes number error
        rowList.add(row.getString("FN_FAILED"))
        // Flow nodes number cancelled
        rowList.add(row.getString("FN_CANCELLED"))
        // Diagram URL
        rowList.add(caseVisuUrl + "/?id=" + row.getString("CS_PROCESS_DEFINITION_ID") + "-" + sourceObjectId)
        rowList
    }

    String exportTasks(Long start, Long end, Long processDefinitionId, String exportedFileName) {
        def params = ["tenantId" : configuration.tenantId, "start": start, "end": end,
                      "process": processDefinitionId]

        final def sql = new Sql(configuration.dataSource)
        this.exportedFileName = exportedFileName
        String result

        try {
            sql.query(ReportingServiceQueries.exportTasks(params)) { rs ->
                result = getTasksCsvZipBase64(rs)
            }
        } finally {
            sql.close()
        }
        result
    }

    private String getTasksCsvZipBase64(ResultSet rs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        ZipOutputStream zos = new ZipOutputStream(baos)
		def hasLines = false
        try {
            zos.putNextEntry(new ZipEntry(this.exportedFileName + ".csv"))

            //write headers
            String headers = "Task ID,Flownode name,Flownode display name,Creation date and time ($timezone),Creation date,Creation time,End date and time ($timezone),End date,End time,Case ID,Process name,Process version,Assigned user First name,Assigned user Last name,Assigned User name,Processed by First name,Processed by Last name,Processed by User name,Flownode state,Type,Parent flownode name,Due date and time ($timezone),Due date,Due time,Overdue\n";
            byte[] lineBytes = headers.getBytes()
            zos.write(lineBytes, 0, lineBytes.length)

            //write data rows
            while (rs.next()) {
				if(!hasLines) {
					hasLines = true
				}
				
                StringBuilder csvLine = new StringBuilder()
                csvLine.append(tasksSqlRowToCsvLine(rs).stream().map({ escapeSpecialCharacters(it as String) }).collect(Collectors.joining(","))).append("\n")
                lineBytes = csvLine.toString().getBytes()
                zos.write(lineBytes, 0, lineBytes.length)
            }
            zos.closeEntry()
        }
        finally {
            zos.close()
			
			if(!hasLines) {
				return null
			}
        }
        byte[] bytes = baos.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private List<String> tasksSqlRowToCsvLine(ResultSet row) {
        def rowList = new ArrayList()
        // Flownode ID
        rowList.add(row.getString("F_ID"))
        // Flownode name
        rowList.add(row.getString("F_NAME"))
        // Flownode display name
        rowList.add(row.getString("F_DISPLAYNAME"))
        // Date and time
        Date reachedStateDate = new Date(row.getLong("F_REACHEDSTATEDATE"))
        rowList.add(dateFormatFull.format(reachedStateDate))
        // Date
        rowList.add(dateFormatOnlyDay.format(reachedStateDate))
        // Time
        rowList.add(dateFormatOnlyHour.format(reachedStateDate))

        if (row.getLong("F_ENDDATE") == 0L) {
            rowList.add("")
            rowList.add("")
            rowList.add("")
        } else {
            // End date and time
            Date endDate = new Date(row.getLong("F_ENDDATE"))
            rowList.add(dateFormatFull.format(endDate))
            // End date
            rowList.add(dateFormatOnlyDay.format(endDate))
            // End time
            rowList.add(dateFormatOnlyHour.format(endDate))

        }
        // Case ID
        rowList.add(row.getString("F_CASEID"))
        // Process name
        rowList.add(row.getString("PD_NAME"))
        // Process version
        rowList.add(row.getString("PD_VERSION"))
        // Assigned user First name
        rowList.add(row.getString("AU_FIRSTNAME"))
        // Assigned user Last name
        rowList.add(row.getString("AU_LASTNAME"))
        // Assigned User name
        rowList.add(row.getString("AU_USERNAME"))
        // Processed by user First name
        rowList.add(row.getString("EU_FIRSTNAME"))
        // Processed by user Last name
        rowList.add(row.getString("EU_LASTNAME"))
        // Processed by User name
        rowList.add(row.getString("EU_USERNAME"))
        // Flownode state
        rowList.add(getStateFromStateName(row.getString("F_STATE")))
        // Flownode kind
        if (row.getString("F_KIND") == 'manual') {
            rowList.add("Subtask")
        } else {
            rowList.add("Task")
        }
        // Parent flownode name
        rowList.add(row.getString("PF_NAME"))
		if (row.getLong("F_DUEDATE") == 0L) {
			rowList.add("")
			rowList.add("")
			rowList.add("")
		} else {
			// Due date and time
            Date dueDate = new Date(row.getLong("F_DUEDATE"))
            rowList.add(dateFormatFull.format(dueDate))
            // Due date
            rowList.add(dateFormatOnlyDay.format(dueDate))
            // Due time
            rowList.add(dateFormatOnlyHour.format(dueDate))

        }
        // Overdue
        rowList.add(row.getString("F_OVERDUE_FLAG"))
        rowList
    }

    private static String getStateFromStateName(String stateName) {
        switch (stateName) {
            case "ready":
                return "Opened"
                break
            case "completed":
                return "Done"
                break
            case "canceled":
            case "aborted":
            case "skipped":
                return "Omitted"
                break
            case "failed":
                return "Failed"
                break
        }
    }

    private static String escapeSpecialCharacters(String data) {
        if (StringUtils.isBlank(data)) {
            return ""
        }
        String escapedData = data.replaceAll("\\R", " ")
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"")
            escapedData = "\"" + data + "\""
        }
        return escapedData
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        d = d.minusHours(hours);
        long minutes = d.toMinutes();
        d = d.minusMinutes(minutes);
        long seconds = d.getSeconds();

        (hours == 0 ? "" : hours + " hours ") + (minutes == 0 ? "" : minutes + " minutes ") + (seconds + " seconds")
    }

    private List<GroovyRowResult> execute(Sql sql, GString query, int page = 0, int maxRows = 0) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("executing query ${query.getStrings().join("?")}")
            LOGGER.debug("with parameters query ${query.getValues()}")
        }

        def queryResults = sql.rows(query, page * maxRows + 1, maxRows)
        queryResults
    }

    List<Process> getProcesses(GetProcessFilters getProcessFilters) {
        final def sql = new Sql(configuration.dataSource)
        try {
            // Use GString to ensure the use of prepared statements
            GString query = ReportingServiceQueries.getProcessesQuery(getProcessFilters.tap {
                tenantId = configuration.tenantId
            })
			
			def queryResults = execute(sql, query, getProcessFilters.getPage(), getProcessFilters.getCount())
			
			// Add a second page if we have it
			if (queryResults.size() == getProcessFilters.getCount()) {
				def nextPageQueryResults = execute(sql, query, (getProcessFilters.getPage() + 1) * getProcessFilters.getCount(), 1)
				if (nextPageQueryResults.size() > 0) {
					queryResults.add(nextPageQueryResults.get(0))
				}
			}
			
            return buildProcesses(queryResults)
        } finally {
            sql.close()
        }
    }

    def buildProcesses(def rows) {
        List<Process> processes = []
        for (def p : rows) {
            processes.add(new Process(p.get("processId") as Long, p.get("name") as String, p.get("version") as String, p.get("displayName") as String, p.get("activationState") as String, p.get("deploymentDate") as Long))
        }
		
		if(processes.size() == 0) {
			return null
		}
		
        return processes
    }


    def calculateLabels(def start, def end, def period) {
        LocalDateTime localDateStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneOffset.UTC)
        LocalDateTime localDateEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), ZoneOffset.UTC)
        def labels = []
        def iterDate = localDateStart
        if (period == 3600000) {
            while (iterDate.isBefore(localDateEnd)) {
                labels.add(iterDate.toInstant(ZoneOffset.UTC).toEpochMilli())
                iterDate = iterDate.plusHours(1)
            }
        } else {
            while (iterDate.isBefore(localDateEnd)) {
                labels.add(iterDate.toInstant(ZoneOffset.UTC).toEpochMilli())
                if (iterDate == iterDate.plusDays(1).toLocalDate().atStartOfDay()) {
                    iterDate = iterDate.plusDays(1)
                } else {
                    iterDate = iterDate.plusDays(1).toLocalDate().atStartOfDay()
                }
            }
        }
        labels.add(end)
        return labels
    }

    private long bucketSizeInMillis(long start, long end) {
        Duration.ofMillis(end - start) > Duration.ofDays(1) ? Duration.ofDays(1).toMillis() : Duration.ofHours(1).toMillis()
    }

    static calculateDataUnit(averages, medians) {
        // if all averages and medians are in months
        Long months = 1000L * 60L * 60L * 24L * 7L * 4L
        if (areDurationsInUnits(averages, medians, months)) {
            return [months, "months"] // 2419200000
        }
        // if all averages and medians are in weeks
        Long weeks = months / 4L
        if (areDurationsInUnits(averages, medians, weeks)) {
            return [weeks, "weeks"] // 604800000
        }
        // if all averages and medians are in days
        Long days = weeks / 7L
        if (areDurationsInUnits(averages, medians, days)) {
            return [days, "days"] // 86400000
        }
        // if all averages and medians are in hours
        Long hours = days / 24L
        if (areDurationsInUnits(averages, medians, hours)) {
            return [hours, "hours"] // 3600000
        }
        // if all averages and medians are in minutes
        Long minutes = hours / 60L
        if (areDurationsInUnits(averages, medians, minutes)) {
            return [minutes, "minutes"] // 60000
        }
        // if all averages and medians are in seconds
        Long seconds = minutes / 60L
        if (areDurationsInUnits(averages, medians, seconds)) {
            return [seconds, "seconds"] // 1000
        }
        return [1L, "milliseconds"]
    }

    static boolean areDurationsInUnits(averages, medians, Long unit) {
        averages.every {
            it == 0 || it.longValue().intdiv(unit) > 0
        } && medians.every { it == 0 || it.longValue().intdiv(unit) > 0 }
    }

    private static String 'stateId to string correspondence table'(int stateIdInt) {
        def stateIdString
        switch (stateIdInt) {
            case 0:
                stateIdString = "INITIALIZING"
                break
            case 1:
                stateIdString = "STARTED"
                break
            case 2:
                stateIdString = "SUSPENDED"
                break
            case 3:
                stateIdString = "CANCELLED"
                break
            case 4:
                stateIdString = "ABORTED"
                break
            case 5:
                stateIdString = "COMPLETING"
                break
            case 6:
                stateIdString = "COMPLETED"
                break
            case 7:
                stateIdString = "ERROR"
                break
            case 11:
                stateIdString = "ABORTING"
                break
        }
        stateIdString
    }

    static void checkSpFeatures() {
        boolean isFeatureActive = false;
        try{
            isFeatureActive = PlatformAPIAccessor.getNodeAPI().licenseInfo.getFeatures().contains("TRACEABILITY")
        }catch(Exception ex){

        }

        if(!isFeatureActive){
            throw new Exception("not permitted execution")
        }
    }
}
