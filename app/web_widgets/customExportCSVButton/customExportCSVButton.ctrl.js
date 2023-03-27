function ExportCSVButtonCtrl($scope, $http, $window) {

    'use strict';

    var vm = this;

    this.doRequest = function doRequest() {
        var req = {
            method: 'POST',
            url: $scope.properties.url,
            data: angular.copy($scope.properties.dataToSend)
        };

        return $http(req)
            .success(function (data, status) {
                $scope.properties.dataFromSuccess = data;
                $scope.properties.dataFromError = undefined;
                $scope.properties.responseStatusCode = status;
                downloadFile(data.data, data.exportedFileName);
                notifyParentFrame({
                    message: 'success',
                    status: status,
                    dataFromSuccess: data,
                    dataFromError: undefined,
                    responseStatusCode: status
                });
            })
            .error(function (data, status) {
                $scope.properties.dataFromSuccess = undefined;
                $scope.properties.dataFromError = data;
                $scope.properties.responseStatusCode = status;
                notifyParentFrame({
                    message: 'error',
                    status: status,
                    dataFromSuccess: undefined,
                    dataFromError: data,
                    responseStatusCode: status
                });
            })
    };

    function notifyParentFrame(additionalProperties) {
        if ($window.parent !== $window.self) {
            var dataToSend = angular.extend({}, $scope.properties, additionalProperties);
            $window.parent.postMessage(JSON.stringify(dataToSend), '*');
        }
    }

    function downloadFile(data, exportedFileName) {
        if (!data) { 
            return;
        }

        var csvContent = "data:application/x-zip;base64," + data;
        var downloadLink = document.createElement('a');
        downloadLink.setAttribute('class', 'downloadCSVFileLink');
        downloadLink.setAttribute('href', csvContent);
        downloadLink.setAttribute('download', exportedFileName + ".zip");
        document.body.appendChild(downloadLink);
        downloadLink.click();
        data = undefined;
    }
}
