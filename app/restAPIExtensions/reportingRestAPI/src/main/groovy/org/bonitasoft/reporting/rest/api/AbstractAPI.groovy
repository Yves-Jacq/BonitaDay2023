package org.bonitasoft.reporting.rest.api

import groovy.json.JsonSlurper
import groovy.transform.Canonical
import org.apache.commons.lang3.StringUtils
import org.bonitasoft.reporting.conf.Configuration
import org.bonitasoft.reporting.services.ReportingService
import org.bonitasoft.web.extension.ResourceProvider
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiController
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Canonical
abstract class AbstractAPI implements RestApiController {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.class)
    protected ReportingService reporting;

    protected ReportingService getReportingService(RestAPIContext context) {
        ReportingService.getInstance(Configuration.getInstance(context.getApiSession().getTenantId()))
    }

    Map<String, String> getQueryParameterValues(HttpServletRequest request, String... paramNames) {
        def paramValues = new HashMap<String, String>()
        paramNames.each { paramName ->
            def paramValue = request.getParameter(paramName)
            if (paramValue != null && !paramValue.isBlank()) {
                paramValues.put(paramName, paramValue)
            }
        }
        return paramValues
    }

    Map<String, String> getBodyParameterValues(HttpServletRequest request, String... paramNames) {
        def paramValues = new HashMap<String, String>()
        def jsonBody = new JsonSlurper().parse(request.getReader())

        paramNames.each { paramName ->
            def paramValue = jsonBody[paramName]
            if (paramValue != null && !((String) paramValue).isBlank()) {
                paramValues.put(paramName, paramValue)
            }
        }

        return paramValues
    }

    /**
     * Build an HTTP response.
     *
     * @param responseBuilder the Rest API response builder
     * @param httpStatus the status of the response
     * @param body the response body
     * @return a RestAPIResponse
     */
    RestApiResponse buildResponse(RestApiResponseBuilder responseBuilder, int httpStatus, Serializable body) {
        if (httpStatus != HttpServletResponse.SC_OK) {
            LOGGER.error("Error:" + body)
        }

        return responseBuilder.with {
            withResponseStatus(httpStatus)
            withResponse(body)
            build()
        }
    }

    protected static String getPagination(count, page, result) {
        // There is no result
        if (result === null) {
            return "0-0/0"
        }

        int maxItems = count * page + result.size()
        // If we have more than the required elements, we have a second page, remove it since the user didn't request it
        if (result.size() > count) {
            result.removeLast()
        }
        return "$page-$count/$maxItems"
    }
}
