package org.apache.cordova.wifiutils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class WifiUtils extends CordovaPlugin {
    private static final String TAG = WifiUtils.class.getSimpleName();

    private static final String WIFI_AP_CHANGED_INTENT_FILTER = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    private static final String EXTRA_WIFI_AP_STATE = "wifi_state";
    private static final int WIFI_AP_STATE_DISABLING = 10;
    private static final int WIFI_AP_STATE_DISABLED = 11;
    private static final int WIFI_AP_STATE_ENABLING = 12;
    private static final int WIFI_AP_STATE_ENABLED = 13;
    private static final int WIFI_AP_STATE_FAILED = 14;

    private static final String WIFI_CONNECTION_CHANGED_INTENT_FILTER = "android.net.wifi.STATE_CHANGE";

    private WifiManager wifiManager;
    private WifiManager.WifiLock wifiLock;
    private CallbackContext connectionStateChangeCallback = null;
    private ConnectivityManager connManager;
    private JSONObject adapterInfos;
    private WifiChangeListener wifiChangeListener;
    private static WifiUtils currentInstance;
    private NetworkInfo.State lastNetworkState = NetworkInfo.State.UNKNOWN;
    private String currentWifiStateText = "UNKNOWN";

    public WifiUtils() {
        super();
        WifiUtils.currentInstance = this;
    }

    public static WifiUtils getInstance(){
        return WifiUtils.currentInstance;
    }

    private BroadcastReceiver wifiConnectionChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Bundle extras = intent.getExtras();
            NetworkInfo info = extras.getParcelable(WifiManager.EXTRA_NETWORK_INFO);

            if (info != null) {
                NetworkInfo.State newNetworkState = info.getState();
                if (newNetworkState != lastNetworkState) {
                    currentWifiStateText = newNetworkState.toString();
                    sendConnectionStateChangeCallback(currentWifiStateText);
                    if (newNetworkState == NetworkInfo.State.CONNECTED) {
                        notifyWifiChangeListener();
                    }
                    lastNetworkState = newNetworkState;
                }
            }
        }
    };

    private BroadcastReceiver wifiApChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            int apState = extras.getInt(EXTRA_WIFI_AP_STATE);
            switch (apState) {
                case WIFI_AP_STATE_DISABLED:
                    currentWifiStateText = "WIFI_AP_STATE_DISABLED";
                    break;
                case WIFI_AP_STATE_DISABLING:
                    currentWifiStateText = "WIFI_AP_STATE_DISABLING";
                    break;
                case WIFI_AP_STATE_ENABLED:
                    currentWifiStateText = "WIFI_AP_STATE_ENABLED";
                    break;
                case WIFI_AP_STATE_ENABLING:
                    currentWifiStateText = "WIFI_AP_STATE_ENABLING";
                    break;
                case WIFI_AP_STATE_FAILED:
                    currentWifiStateText = "WIFI_AP_STATE_FAILED";
                    break;
                default:
                    currentWifiStateText = "WIFI_AP_STATE_UNKNOWN";
                    break;
            }

            sendConnectionStateChangeCallback(currentWifiStateText);

            if (apState == WIFI_AP_STATE_ENABLED) {
                notifyWifiChangeListener();
            }
        }
    };

    private void sendConnectionStateChangeCallback(String connectionState) {
        Log.d(TAG, connectionState);
        if (connectionStateChangeCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, connectionState);
            result.setKeepCallback(true);
            connectionStateChangeCallback.sendPluginResult(result);
        }
    }

    private void notifyWifiChangeListener() {
        try {
            adapterInfos = getAdapterInfos();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        if (adapterInfos == null) {
            adapterInfos = new JSONObject();
        }
        if (wifiChangeListener != null) {
            wifiChangeListener.onWifiChanged(adapterInfos);
        }
    }

    public void setWifiChangeListener(WifiChangeListener wifiChangeListener) {
        this.wifiChangeListener = wifiChangeListener;
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        wifiManager = (WifiManager) cordova.getActivity().getSystemService(Context.WIFI_SERVICE);
        connManager = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

        // http://stackoverflow.com/questions/15698790/broadcast-receiver-for-checking-internet-connection-in-android-app
        cordova.getActivity().getApplicationContext().registerReceiver(wifiConnectionChangedReceiver, new IntentFilter(WIFI_CONNECTION_CHANGED_INTENT_FILTER));
        cordova.getActivity().getApplicationContext().registerReceiver(wifiApChangedReceiver, new IntentFilter(WIFI_AP_CHANGED_INTENT_FILTER));
    }

    @Override
    public void onDestroy() {
        cordova.getActivity().getApplicationContext().unregisterReceiver(wifiConnectionChangedReceiver);
        cordova.getActivity().getApplicationContext().unregisterReceiver(wifiApChangedReceiver);
        releaseLock();
    }

    private JSONObject getErrorFromException(Exception e) {
        JSONObject error = new JSONObject();

        // get stacktace
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter( writer );
        e.printStackTrace(printWriter);
        printWriter.flush();

        try {
            error.put("error", e.toString());
            error.put("stacktrace", writer.toString());
        } catch (JSONException e1) {
            Log.d(TAG, "JSON-Error Object could not be created.", e);
        }

        return error;
    }

     /* the following method is for getting the wifi hotspot state */
    private int getWifiApState() {
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApState");
            return ((Integer) method.invoke(wifiManager));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return WIFI_AP_STATE_FAILED;
        }
    }

    /**
     * Return whether Wi-Fi Hotspot is enabled or disabled.
     *
     * @return {@code true} if Wi-Fi AP is enabled
     * @see #getWifiApState()
     */
    private boolean isWifiApEnabled() {
        return (getWifiApState() == WIFI_AP_STATE_ENABLED);
    }

    private InetAddress getNetIdAddress(byte[] ipAddress, byte[] subnetMask) throws UnknownHostException {
        byte[] netIdBytes = new byte[4];
        if (ipAddress.length == subnetMask.length) {
            for (int i = 0; i < ipAddress.length; i++) {
                netIdBytes[i] = (byte) ((ipAddress[i] & subnetMask[i]) & 0xFF);
            }
        }
        return InetAddress.getByAddress(netIdBytes);
    }

    private InetAddress getSubnetMask(short subnetMask) throws UnknownHostException {
        int mask = 0xffffffff << (32 - subnetMask);
        byte[] bytes = new byte[]{(byte) (mask >>> 24), (byte) (mask >> 16 & 0xff), (byte) (mask >> 8 & 0xff), (byte) (mask & 0xff)};
        return InetAddress.getByAddress(bytes);
    }

    public JSONObject getAdapterInfos() throws SocketException, JSONException, UnknownHostException {
        JSONObject adapterData = new JSONObject();

        boolean isWifiConnected = false;
        NetworkInfo activeNetworkInfo = connManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null && activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI && activeNetworkInfo.isConnected()) {
            isWifiConnected = true;
        }

        boolean isWifiApEnabled = isWifiApEnabled();

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        SupplicantState supplicantState = wifiInfo.getSupplicantState();

        adapterData.put("connected", isWifiConnected || isWifiApEnabled);
        adapterData.put("apEnabled", isWifiApEnabled);
        adapterData.put("wifiConnected", isWifiConnected);
        adapterData.put("wifiState", currentWifiStateText);
        adapterData.put("supplicantState", supplicantState.toString());

        adapterData.put("BSSID", wifiInfo.getBSSID());
        adapterData.put("HiddenSSID", wifiInfo.getHiddenSSID());
        adapterData.put("SSID", wifiInfo.getSSID());
        adapterData.put("MacAddress", wifiInfo.getMacAddress());

        String androidHostname;
        try {
            Method getString = Build.class.getDeclaredMethod("getString", String.class);
            getString.setAccessible(true);
            androidHostname = getString.invoke(null, "net.hostname").toString();
        } catch (Exception ex) {
            androidHostname = "android-" + Settings.Secure.getString(cordova.getActivity().getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        }

        adapterData.put("AndroidHostname", androidHostname);
        adapterData.put("RSSI", wifiInfo.getRssi());
        adapterData.put("LinkSpeed", wifiInfo.getLinkSpeed());

        NetworkInterface activeInterface = null;
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();){
            NetworkInterface intf = en.nextElement();
            if (intf.isUp() && intf.supportsMulticast() && intf.getInterfaceAddresses().size() > 0 && !intf.isLoopback() && !intf.isVirtual() && !intf.isPointToPoint()){
                if (activeInterface == null){
                    activeInterface = intf;
                }else{
                    if (!activeInterface.getName().contains("wlan") && !activeInterface.getName().contains("ap")) {
                        activeInterface = intf;
                    }
                }
            }
        }

        if (activeInterface != null) {
            adapterData.put("activeAdaper", activeInterface.getDisplayName());

            JSONArray adapterAddresses = new JSONArray();

            for (InterfaceAddress addr: activeInterface.getInterfaceAddresses()) {
                if (!addr.getAddress().isLoopbackAddress()) {
                    JSONObject addressData = new JSONObject();

                    boolean isIpV4 = (addr.getAddress().getAddress().length == 4);
                    addressData.put("addressType", (isIpV4) ? "ipv4" : "ipv6");

                    // Ip-Address
                    InetAddress ipAddress = addr.getAddress();
                    addressData.put("ipAddress", ipAddress.getHostAddress());

                    if (isIpV4) {
                        // Subnet-Mask
                        InetAddress subnetMask = getSubnetMask(addr.getNetworkPrefixLength());
                        addressData.put("subnetMask", subnetMask.getHostAddress());

                        // Network-ID
                        InetAddress networkId = getNetIdAddress(ipAddress.getAddress(), subnetMask.getAddress());
                        addressData.put("networkId", networkId.getHostAddress());

                        // Broadcast-Address
                        InetAddress bcast = addr.getBroadcast();
                        if (bcast != null) {
                            addressData.put("broadcastAddress", bcast.getHostAddress());
                        }
                    }

                    adapterAddresses.put(addressData);
                }
            }
            adapterData.put("addresses", adapterAddresses);
        }

        // removing reachableWlans cause the need for Android Permission ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION
        // It is neccessary to activate location services to get a list of wifi networks around. If not activated,
        // wifiManager.getScanResults returns an empty list or a SecurityException if your on Android 6.0.0
        // see https://code.google.com/p/android/issues/detail?id=185370
        // I dont want to enforce GPS permissions for this plugin.
        // Maybe i will add a seperate action ...
        /*
        JSONArray available = new JSONArray();
        List<ScanResult> scanResults = wifiManager.getScanResults();
        if (scanResults != null) {
            for (ScanResult scanResult : scanResults) {
                JSONObject ap = new JSONObject();
                ap.put("BSSID", scanResult.BSSID);
                ap.put("SSID", scanResult.SSID);
                ap.put("frequency", scanResult.frequency);
                ap.put("level", scanResult.level);
                ap.put("capabilities", scanResult.capabilities);
                available.put(ap);
            }
            adapterData.put("reachableWlans", available);
        }
        */

        return adapterData;
    }

    private void aquireLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            return;
        }
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);
        Log.d(TAG, "WifiLock: WIFI_MODE_FULL_HIGH_PERF aquired");
    }

    private void releaseLock() {
        if (wifiLock != null) {
            if (wifiLock.isHeld()){
                wifiLock.release();
                Log.d(TAG, "WifiLock: WIFI_MODE_FULL_HIGH_PERF released");
            }
            wifiLock = null;
        }
    }

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {

        if (action.equals("init")) {
            // trigger plugin initialization
            callbackContext.success();
        } else if (action.equals("getInfos")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        adapterInfos = getAdapterInfos();
                        callbackContext.success(adapterInfos);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString(), e);
                        callbackContext.error(getErrorFromException(e));
                    }
                }
            });
        } else if (action.equals("aquireWifiLock")) {
            aquireLock();
            callbackContext.success();
        } else if (action.equals("releaseWifiLock")) {
            releaseLock();
            callbackContext.success();
        } else if (action.equals("onConnectionStateChange")) {
            connectionStateChangeCallback = callbackContext;
        } else {
            return false; // 'MethodNotFound'
        }
        return true;
    }
}
