package org.apache.cordova.wifiutils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
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
import java.util.List;

public class WifiUtils extends CordovaPlugin {
    private static final String TAG = WifiUtils.class.getSimpleName();

    private WifiManager wifiManager;
    private NetworkInfo wifiConnection;
    private ConnectivityManager connManager;

    private static enum WIFI_AP_STATE {
        WIFI_AP_STATE_DISABLING,
        WIFI_AP_STATE_DISABLED,
        WIFI_AP_STATE_ENABLING,
        WIFI_AP_STATE_ENABLED,
        WIFI_AP_STATE_FAILED
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

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.d(TAG, "initialize");
        wifiManager = (WifiManager) cordova.getActivity().getSystemService(Context.WIFI_SERVICE);
        connManager = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /*the following method is for getting the wifi hotspot state*/
    private WIFI_AP_STATE getWifiApState() {
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApState");
            int tmp = ((Integer) method.invoke(wifiManager));
            // Fix for Android 4
            if (tmp > 10) {
                tmp = tmp - 10;
            }
            return WIFI_AP_STATE.class.getEnumConstants()[tmp];
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return WIFI_AP_STATE.WIFI_AP_STATE_FAILED;
        }
    }

    /**
     * Return whether Wi-Fi Hotspot is enabled or disabled.
     *
     * @return {@code true} if Wi-Fi AP is enabled
     * @see #getWifiApState()
     */
    private boolean isWifiApEnabled() {
        return (getWifiApState() == WIFI_AP_STATE.WIFI_AP_STATE_ENABLED);
    }

    private boolean isWifiConnected() {
        return (wifiConnection != null && wifiConnection.isConnected());
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

    private JSONObject getAdapterInfos() throws SocketException, JSONException, UnknownHostException {
        JSONObject adapterData = new JSONObject();

        wifiConnection = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        adapterData.put("connected", isWifiConnected() || isWifiApEnabled());
        adapterData.put("apEnabled", isWifiApEnabled());
        adapterData.put("wifiConnected", isWifiConnected());
        adapterData.put("BSSID", wifiInfo.getBSSID());
        adapterData.put("HiddenSSID", wifiInfo.getHiddenSSID());
        adapterData.put("SSID", wifiInfo.getSSID());
        adapterData.put("MacAddress", wifiInfo.getMacAddress());
        adapterData.put("RSSI", wifiInfo.getRssi());
        adapterData.put("LinkSpeed", wifiInfo.getLinkSpeed());

        NetworkInterface activeInterface = null;
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();){
            NetworkInterface intf = en.nextElement();
            if (intf.isUp() && intf.supportsMulticast() && intf.getInterfaceAddresses().size() > 0 && !intf.isLoopback() && !intf.isVirtual() && !intf.isPointToPoint()){
                Log.d(TAG, intf.getDisplayName());
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

        return adapterData;
    }

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {

        if (action.equals("getInfos")) {
            Log.d(TAG, "getInfos");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getAdapterInfos());
                    } catch (Exception e) {
                        Log.e(TAG, e.toString(), e);
                        callbackContext.error(getErrorFromException(e));
                    }
                }
            });
        } else {
            return false; // 'MethodNotFound'
        }
        return true;
    }
}
