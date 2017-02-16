package org.apache.cordova.wifiutils;

import org.json.JSONObject;

/**
 * Created by ascherer on 25.01.2017.
 */

public interface WifiChangeListener {
    void onWifiChanged(JSONObject adapterInfos);
}
