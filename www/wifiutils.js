var WifiUtils = (function () {
    function WifiUtils() {
    }
    WifiUtils.prototype.init = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'WifiUtils', 'init', []);
    };
    WifiUtils.prototype.getInfos = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'WifiUtils', 'getInfos', []);
    };
    WifiUtils.prototype.aquireWifiLock = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'WifiUtils', 'aquireWifiLock', []);
    };
    WifiUtils.prototype.releaseWifiLock = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'WifiUtils', 'releaseWifiLock', []);
    };
    return WifiUtils;
}());
module.exports = new WifiUtils();
