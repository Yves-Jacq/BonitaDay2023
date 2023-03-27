package org.bonitasoft.reporting.rest.api.cases

import groovy.json.JsonBuilder
import org.bonitasoft.reporting.rest.api.AbstractAPI
import org.bonitasoft.reporting.rest.api.ValidationBadRequestError
import org.bonitasoft.reporting.rest.api.Validators
import org.bonitasoft.reporting.services.ReportingExecutor
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ExportCasesAPI extends AbstractAPI {

    @Override
    RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {

        String[] requiredParams = ["start", "end", "process", "filterByDate", "currentPageUrl"]
        def parameterValues = getBodyParameterValues(request, requiredParams)
        try {
            Validators.validateContainsRequiredParams(parameterValues, requiredParams)
            Validators.validateRequestBodyApiParams(parameterValues)
        } catch (ValidationBadRequestError e) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST, e.body)
        }

        def reportingService = getReportingService(context)
        def exportedFileName = "Bonita_Report_Archived_Cases_" + System.currentTimeMillis()
        def report
        ReportingExecutor.getInstance().submit {
            report = reportingService.exportCaseAvgReport(parameterValues.start as Long, parameterValues.end as Long, parameterValues.process as Long, parameterValues.filterByDate, getCaseVisuUrl(parameterValues.currentPageUrl), exportedFileName)
        }.get()
        exportedFileName = report == null ? null : exportedFileName
        def output = ["timestamp": System.currentTimeMillis(), "data": report, "exportedFileName": exportedFileName]

        return buildResponse(responseBuilder, HttpServletResponse.SC_OK, new JsonBuilder(output).toString())
    }

    private static String getCaseVisuUrl(String currentPageUrl) {
        def currentUrl = currentPageUrl
        currentUrl = currentUrl.substring(0, currentUrl.lastIndexOf("/"))
        return currentUrl.substring(0, currentUrl.lastIndexOf("/")) + "/case-visu"
    }
}
