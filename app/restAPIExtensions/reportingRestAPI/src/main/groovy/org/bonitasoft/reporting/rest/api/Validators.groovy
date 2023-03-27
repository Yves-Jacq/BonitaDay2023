package org.bonitasoft.reporting.rest.api

import org.apache.commons.lang3.StringUtils

class Validators {

    static void validateContainsRequiredParams(Map<String, String> parameterValues, String... requiredParams) throws ValidationBadRequestError {
        for (String requiredParam : requiredParams) {
            if (!parameterValues.containsKey(requiredParam)) {
                throw new ValidationBadRequestError("""{"error" : "Missing required param '$requiredParam' in the request body"}""")
            }
        }
    }

    static void validateRequestBodyApiParams(Map<String, String> parameterValues) throws ValidationBadRequestError {
        for (def key : parameterValues.keySet()) {
            def value = parameterValues[key]
            switch (key) {
                case "start":
                    if (value == null || value == 0) {
                        throw new ValidationBadRequestError("""{"error" : "Expected a non-zero start date parameter in the request body"}""")
                    } else if (!(value instanceof Number)) {
                        throw new ValidationBadRequestError("""{"error" : "Start date request body parameter should be a number"}""")
                    }
                    break
                case "end":
                    if (value == null || value == 0) {
                        throw new ValidationBadRequestError("""{"error" : "Expected a non-zero end date parameter in the request body"}""")
                    } else if (!(value instanceof Number)) {
                        throw new ValidationBadRequestError("""{"error" : "End date request body parameter should be a number"}""")
                    }
                    break
                case "process":
                    if (!(value instanceof String) || !StringUtils.isNumeric(value)) {
                        throw new ValidationBadRequestError("""{"error" : "Process request body parameter should be a number"}""")
                    }
                    break
                case "filterByDate":
                    if (value != "startDate" && value != "endDate") {
                        throw new ValidationBadRequestError("""{"error" : "Value of body parameter 'filterByDate' cannot be different than 'startDate' or 'endDate'"}""")
                    }
                    break
                case "currentPageUrl":
                    try {
                        new URL(value)
                    } catch (MalformedURLException ignored) {
                        throw new ValidationBadRequestError("""{"error" : "Value of body parameter 'currentPageUrl' should be a valid URL"}""")
                    }
                    break
            }
        }
    }

    static void validateProcessApiParams(Map<String, String> parameterValues) throws ValidationBadRequestError {
        for (def key : parameterValues.keySet()) {
            def value = parameterValues[key]
            switch (key) {
                case "start":
                    if (value == null || !(value instanceof String) || !StringUtils.isNumeric(value) || value == 0) {
                        throw new ValidationBadRequestError("""{"error" : "Expected a non-zero number start date parameter in the request query parameter"}""")
                    }
                    break
                case "end":
                    if (value == null || !(value instanceof String) || !StringUtils.isNumeric(value) || value == 0) {
                        throw new ValidationBadRequestError("""{"error" : "Expected a non-zero number end date parameter in the request query parameter"}""")
                    }
                    break
                case "process":
                    if (value == null || !(value instanceof String) || !StringUtils.isNumeric(value)) {
                        throw new ValidationBadRequestError("""{"error" : "Expected a non-zero number process id parameter in the request query parameter"}""")
                    }
                    break
                case "enabled":
                    if (value != null && value != "true" && value != "false") {
                        throw new ValidationBadRequestError("""{"error" : "Enabled should have a boolean value"}""")
                    }
                    break
                case "latest":
                    if (value != null && value != "true" && value != "false") {
                        throw new ValidationBadRequestError("""{"error" : "Latest should have a boolean value"}""")
                    }
                    break
                case "p":
                    if (value == null || !(value instanceof String) || !StringUtils.isNumeric(value)) {
                        throw new ValidationBadRequestError("""{"error" : "Expected a non-zero number p parameter in the request query parameter"}""")
                    }
                    break
                case "c":
                    if (value == null || !(value instanceof String) || !StringUtils.isNumeric(value)) {
                        throw new ValidationBadRequestError("""{"error" : "Expected a non-zero number c parameter in the request query parameter"}""")
                    }
                    break
            }
        }
        if (parameterValues.latest != null && parameterValues.version != null) {
            throw new ValidationBadRequestError("""{"error" : "Version and latest can't have a value in the same time"}""")
        }
        if (parameterValues.name != null && parameterValues.search != null) {
            throw new ValidationBadRequestError("""{"error" : "Name and search can't have a value at the same time"}""")
        }
    }

}
