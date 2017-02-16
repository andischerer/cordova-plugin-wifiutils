interface Cordova {
  exec(success: (data: any) => any, fail: (err: any) => any, service: string, action: string, args?: any[]): void;
}

declare var cordova: Cordova;
declare var module: any;

interface Window {
  wifiutils: WifiUtils;
}

type wifiConnectionState = 'CONNECTING' | 'CONNECTED' | 'SUSPENDED' | 'DISCONNECTING' | 'DISCONNECTED' | 'UNKNOWN'
type wifiApState = 'WIFI_AP_STATE_DISABLING' | 'WIFI_AP_STATE_DISABLED' | 'WIFI_AP_STATE_ENABLING' | 'WIFI_AP_STATE_ENABLED' | 'WIFI_AP_STATE_FAILED'

interface IWifiInfos {
  connected: boolean;
  apEnabled: boolean;
  wifiConnected: boolean;
  wifiState: wifiConnectionState | wifiApState;
  supplicantState: string;
  BSSID: string;
  HiddenSSID: boolean;
  SSID: string;
  MacAddress: string;
  AndroidHostname: string;
  RSSI: number;
  LinkSpeed: number;
  activeAdaper: string;
  addresses: Address[];
}

interface Address {
    addressType: 'ipv4' | 'ipv6';
    ipAddress: string;
    subnetMask: string;
    networkId: string;
    broadcastAddress: string;
}

class WifiUtils {
  init(
    successCallback: () => void,
    errorCallback: (error: string) => void
  ): void {
    cordova.exec(successCallback, errorCallback, 'WifiUtils', 'init', []);
  }

  getInfos(
    successCallback: (wifiInfos: IWifiInfos) => void,
    errorCallback: (error: string) => void
  ): void {
    cordova.exec(successCallback, errorCallback, 'WifiUtils', 'getInfos', []);
  }
}

module.exports = new WifiUtils();