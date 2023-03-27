/*******************************************************************************
 * Copyright (C) 2021 BonitaSoft S.A.
 * BonitaSoft is a trademark of BonitaSoft SA.
 * This software file is BONITASOFT CONFIDENTIAL. Not For Distribution.
 * For commercial licensing information, contact:
 * BonitaSoft, 32 rue Gustave Eiffel � 38000 Grenoble
 * or BonitaSoft US, 51 Federal Street, Suite 305, San Francisco, CA 94107
 *******************************************************************************/
package org.bonitasoft.reporting.rest.api.tasks

import groovy.json.JsonBuilder
import org.bonitasoft.reporting.rest.api.AbstractAPI
import org.bonitasoft.reporting.rest.api.ValidationBadRequestError
import org.bonitasoft.reporting.rest.api.Validators
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GetTaskGraphAPI extends AbstractAPI {

    @Override
    RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {

        String[] requiredParams = ["start", "end", "process"]
        def parameterValues = getBodyParameterValues(request, "start", "end", "process")
        try {
            Validators.validateContainsRequiredParams(parameterValues, requiredParams)
            Validators.validateRequestBodyApiParams(parameterValues)
        } catch (ValidationBadRequestError e) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST, e.body)
        }

        def reporting = getReportingService(context)
        def output = ["timestamp": System.currentTimeMillis(), "data": reporting.runTaskStatesReport(parameterValues.start as Long, parameterValues.end as Long, parameterValues.process as Long)]

        return buildResponse(responseBuilder, HttpServletResponse.SC_OK, new JsonBuilder(output).toString())
    }
}
