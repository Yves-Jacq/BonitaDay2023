/*******************************************************************************
 * Copyright (C) 2021 BonitaSoft S.A.
 * BonitaSoft is a trademark of BonitaSoft SA.
 * This software file is BONITASOFT CONFIDENTIAL. Not For Distribution.
 * For commercial licensing information, contact:
 * BonitaSoft, 32 rue Gustave Eiffel � 38000 Grenoble
 * or BonitaSoft US, 51 Federal Street, Suite 305, San Francisco, CA 94107
 *******************************************************************************/
package org.bonitasoft.reporting.rest.api.processes

import groovy.json.JsonBuilder
import org.bonitasoft.reporting.model.GetProcessFilters
import org.bonitasoft.reporting.rest.api.AbstractAPI
import org.bonitasoft.reporting.rest.api.ValidationBadRequestError
import org.bonitasoft.reporting.rest.api.Validators
import org.bonitasoft.reporting.services.ReportingService
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GetProcessesAPI extends AbstractAPI {

    @Override
    RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {

        def parameterValues = getQueryParameterValues(request, "enabled", "version", "latest", "search", "name", "c", "p")
        try {
            Validators.validateProcessApiParams(parameterValues)
        } catch (ValidationBadRequestError e) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST, e.body)
        }

        ReportingService reporting = getReportingService(context)
        def count = parameterValues.c !== null ? parameterValues.c as int : 10
        def page = parameterValues.p !== null ? parameterValues.p as int : 0
		def data = reporting.getProcesses(new GetProcessFilters(
                search: parameterValues.search,
                isLatest: parameterValues.latest != null ? Boolean.valueOf(parameterValues.latest) : null,
                isEnabled: parameterValues.enabled != null ? Boolean.valueOf(parameterValues.enabled) : null,
                version: parameterValues.version,
                name: parameterValues.name,
				count: count,
				page: page
        ))
		
        def output = ["timestamp": System.currentTimeMillis(), "data": data, "pagination": getPagination(count, page, data)]

        return buildResponse(responseBuilder, HttpServletResponse.SC_OK, new JsonBuilder(output).toString())
    }

}
