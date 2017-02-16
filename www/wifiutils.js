var WifiUtils = (function () {
    function WifiUtils() {
    }
    WifiUtils.prototype.init = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'AsaLivestream', 'start', []);
    };
    WifiUtils.prototype.getInfos = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'AsaLivestream', 'start', []);
    };
    return WifiUtils;
}());
module.exports = new WifiUtils();
