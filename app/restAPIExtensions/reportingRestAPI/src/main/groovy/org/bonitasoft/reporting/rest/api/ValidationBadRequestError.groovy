package org.bonitasoft.reporting.rest.api

import groovy.transform.Canonical

@Canonical
class ValidationBadRequestError extends Exception {
    String body

    ValidationBadRequestError(String body) {
        super()
        this.body = body
    }
}
