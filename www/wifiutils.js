var exec = cordova.require('cordova/exec');

module.exports = {
    getInfos: function (callback) {
        callback = callback || function () { };
        exec(callback.bind(null), // success
             callback.bind(null), // failure
             'WifiUtils',
             'getInfos',
             []
        );
    }
};