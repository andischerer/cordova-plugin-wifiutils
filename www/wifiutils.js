var exec = cordova.require('cordova/exec');

module.exports = {
    getInfos: function (successCb, errorCb) {
        exec(successCb, // success
             errorCb, // failure
             'WifiUtils',
             'getInfos',
             []
        );
    }
};
