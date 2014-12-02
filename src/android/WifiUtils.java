package org.apache.cordova.wifiutils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class WifiUtils extends CordovaPlugin {
    private static final String TAG = WifiUtils.class.getSimpleName();

    private WifiManager mWifiManager;
    private NetworkInfo wifiConnection;
    private WifiInfo wifiInfo;


    private static enum WIFI_AP_STATE {
        WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_ENABLING, WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_FAILED
    }

    /*the following method is for getting the wifi hotspot state*/
    private WIFI_AP_STATE getWifiApState() {
        try {
            Method method = mWifiManager.getClass().getMethod("getWifiApState");
            int tmp = ((Integer) method.invoke(mWifiManager));
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

    private JSONObject getAdapterInfos(Context context) throws SocketException, JSONException {
        JSONObject adapterData = new JSONObject();

        NetworkInterface activeInterface = null;
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();){
            NetworkInterface intf = en.nextElement();
            Log.d(TAG, intf.getDisplayName());
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
            adapterData.put("connected", isWifiConnected() || isWifiApEnabled());
            adapterData.put("apEnabled", isWifiApEnabled());
            adapterData.put("wifiConnected", isWifiConnected());
            adapterData.put("BSSID", wifiInfo.getBSSID());
            adapterData.put("HiddenSSID", wifiInfo.getHiddenSSID());
            adapterData.put("SSID", wifiInfo.getSSID());
            adapterData.put("MacAddress", wifiInfo.getMacAddress());
            adapterData.put("RSSI", wifiInfo.getRssi());
            adapterData.put("LinkSpeed", wifiInfo.getLinkSpeed());

            JSONArray adapterAddresses = new JSONArray();

            for (InterfaceAddress addr: activeInterface.getInterfaceAddresses()) {
                if (!addr.getAddress().isLoopbackAddress()) {
                    try {
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
                    } catch (UnknownHostException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
            adapterData.put("addresses", adapterAddresses);
        }

        JSONArray available = new JSONArray();
        for (ScanResult scanResult : mWifiManager.getScanResults()) {
            JSONObject ap = new JSONObject();
            ap.put("BSSID", scanResult.BSSID);
            ap.put("SSID", scanResult.SSID);
            ap.put("frequency", scanResult.frequency);
            ap.put("level", scanResult.level);
            ap.put("capabilities", scanResult.capabilities);
            available.put(ap);
        }
        adapterData.put("reachableWlans", available);

        return adapterData;
    }

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {

        final Context context = cordova.getActivity().getApplicationContext();

        if (mWifiManager == null) {
            mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            wifiConnection = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            wifiInfo = mWifiManager.getConnectionInfo();
        }

        if (action.equals("getInfos")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        callbackContext.success(getAdapterInfos(context));
                    } catch (Exception e) {
                        Log.d(TAG, "send exception:" + e.toString());
                        callbackContext.error("Exception: " + e.toString());
                    }
                }
            });
        } else {
            return false; // 'MethodNotFound'
        }
        return true;
    }
}
