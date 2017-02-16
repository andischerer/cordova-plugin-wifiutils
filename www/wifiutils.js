var WifiUtils = (function () {
    function WifiUtils() {
    }
    WifiUtils.prototype.init = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'WifiUtils', 'init', []);
    };
    WifiUtils.prototype.getInfos = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'WifiUtils', 'getInfos', []);
    };
    return WifiUtils;
}());
module.exports = new WifiUtils();
