package org.bonitasoft.reporting.model

class Report {
    private List data
    private List labels
    private List setLabels
    private String dataUnit

    Report(List data, List labels) {
        super()
        this.data = data
        this.labels = labels
    }

    Report(List data, List labels, List setLabels) {
        this(data,labels)
        this.setLabels = setLabels
    }

    Report(List data, List labels, List setLabels, String dataUnit) {
        this(data,labels,setLabels)
        this.dataUnit = dataUnit
    }

    List getData() {
        return data
    }

    void setData(List data) {
        this.data = data
    }

    List getLabels() {
        return labels
    }

    void setLabels(List labels) {
        this.labels = labels
    }

    List getSetLabels() {
        return setLabels
    }

    void setSetLabels(List setLabels) {
        this.setLabels = setLabels
    }

    String getDataUnit() {
        return dataUnit;
    }

    void setDataUnit(String dataUnit) {
        this.dataUnit = dataUnit
    }
}
