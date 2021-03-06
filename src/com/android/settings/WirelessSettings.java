/* Copyright (c) 2016, The Linux Foundation. All rights reserved.*/

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.settings.nfc.NfcEnabler;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.RestrictedLockUtils;
import java.lang.Exception;
import java.lang.Override;
import com.android.settingslib.RestrictedPreference;
import org.codeaurora.wfcservice.IWFCService;
import org.codeaurora.wfcservice.IWFCServiceCB;

import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtListenerBaseImpl;
import org.codeaurora.ims.internal.IQtiImsExtListener;
import org.codeaurora.ims.QtiImsExtManager;
import org.codeaurora.ims.utils.QtiImsExtUtils;

public class WirelessSettings extends SettingsPreferenceFragment implements Indexable {
    private static final String TAG = "WirelessSettings";
    private static final boolean DEBUG = true;

    private static final String KEY_TOGGLE_AIRPLANE = "toggle_airplane";
    private static final String KEY_TOGGLE_NFC = "toggle_nfc";
    private static final String KEY_WIMAX_SETTINGS = "wimax_settings";
    private static final String KEY_ANDROID_BEAM_SETTINGS = "android_beam_settings";
    private static final String KEY_VPN_SETTINGS = "vpn_settings";
    private static final String KEY_TETHER_SETTINGS = "tether_settings";
    private static final String KEY_PROXY_SETTINGS = "proxy_settings";
    private static final String KEY_MOBILE_NETWORK_SETTINGS = "mobile_network_settings";
    private static final String KEY_MANAGE_MOBILE_PLAN = "manage_mobile_plan";
    private static final String KEY_WFC_SETTINGS = "wifi_calling_settings";
    private static final String KEY_WFC_ENHANCED_SETTINGS = "wifi_calling_enhanced_settings";
    private static final String KEY_CALL_SETTINGS = "call_settings";

    private static final String ACTION_WIFI_CALL_ON = "com.android.wificall.TURNON";
    private static final String ACTION_WIFI_CALL_OFF = "com.android.wificall.TURNOFF";
    private static final String WIFI_CALLING_PREFERRED = "preference";
    private static final String KEY_NETWORK_RESET = "network_reset";

    public static final String EXIT_ECM_RESULT = "exit_ecm_result";
    public static final int REQUEST_CODE_EXIT_ECM = 1;

    /*add for VoLTE Preferred on/off Begin*/
    private static final String IMS_SERVICE_PKG_NAME = "org.codeaurora.ims";
    private static final int MSG_ID_VOLTE_PREFERENCE_UPDATED_RESPONSE = 0x01;
    private static final int MSG_ID_VOLTE_PREFERENCE_QUERIED_RESPONCE = 0X02;
    /*add for VoLTE Preferred on/off end*/

    private AirplaneModeEnabler mAirplaneModeEnabler;
    private SwitchPreference mAirplaneModePreference;
    private NfcEnabler mNfcEnabler;
    private NfcAdapter mNfcAdapter;

    private ConnectivityManager mCm;
    private TelephonyManager mTm;
    private PackageManager mPm;
    private UserManager mUm;

    boolean mIsNetworkSettingsAvailable = false;
    boolean mProvisioningVWiFiEnabled  = false;

    private static final int MANAGE_MOBILE_PLAN_DIALOG_ID = 1;
    private static final String SAVED_MANAGE_MOBILE_PLAN_MSG = "mManageMobilePlanMessage";
    private final String KEY = "persist.sys.provisioning";

    private Preference mButtonWfc;
    private boolean mEnhancedWFCSettingsEnabled = false;

    private IWFCService mWFCService;

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "AIDLExample connect service");
            mWFCService = IWFCService.Stub.asInterface(service);
            try {
                mWFCService.registerCallback(mCallback);
            } catch (RemoteException re) {
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, " AIDLExample disconnect service");
            mWFCService = null;
        }
    };

    private IWFCServiceCB mCallback = new IWFCServiceCB.Stub() {
        public void updateWFCMessage(String errorCode) {
            if (!mEnhancedWFCSettingsEnabled || (errorCode == null)) {
                if(DEBUG) Log.e(TAG, "updateWFCMessage fail.");
                return ;
            }
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (DEBUG) Log.d(TAG, "new UI thread.");
                    try {
                        if (mWFCService.getWifiCallingStatus()) {
                            if (mButtonWfc instanceof WFCPreference) {
                                ((WFCPreference) mButtonWfc).setSummary(errorCode);
                            } else {
                                mButtonWfc.setSummary(errorCode);
                            }
                        }
                    } catch (RemoteException r) {
                        Log.e(TAG, "getWifiCallingStatus RemoteException");
                    }
                }
            });

        }
    };

    private void updateCallback() {
        Log.i(TAG, "call back from settings is called");
    }

    private void unbindWFCService() {
        if (!mEnhancedWFCSettingsEnabled) {
            return;
        }
        if (mWFCService != null) {
            try {
                Log.d(TAG, "WFCService unbindService");
                mWFCService.unregisterCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "WFCService unregister error " + e);
            }
        }

        getActivity().unbindService(mConnection);
        Log.d(TAG, "WFCService unbind error ");
    }

    @Override
    public void onDestroy() {
        unbindWFCService();
        super.onDestroy();
    }

    private static final String VOICE_OVER_LTE = "voice_over_lte";
    private SwitchPreference mVoLtePreference;
    private boolean mLteEnabled = false;
    private QtiImsExtManager mImsExtManager = null;
    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceFragment's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        log("onPreferenceTreeClick: preference=" + preference);
        if (preference == mAirplaneModePreference && Boolean.parseBoolean(
                SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode launch ECM app dialog
            startActivityForResult(
                new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                REQUEST_CODE_EXIT_ECM);
            return true;
        } else if (preference == findPreference(KEY_MANAGE_MOBILE_PLAN)) {
            onManageMobilePlanClick();
        } else if (mLteEnabled && preference == mVoLtePreference) {
            boolean translateValue = mVoLtePreference.isChecked();
            if(mImsExtManager != null) {
                try{
                    mImsExtManager.updateVoltePreference(mImsExtManager.getImsPhoneId(),
                        translateValue?1:0, imsInterfaceListener);
                } catch (QtiImsException e) {
                    Log.e(TAG, e.toString());
                }
            }

        } else if (preference == findPreference(KEY_MOBILE_NETWORK_SETTINGS)
                && mIsNetworkSettingsAvailable) {
            onMobileNetworkSettingsClick();
            return true;
        }
        // Let the intents be launched by the Preference manager
        return super.onPreferenceTreeClick(preference);
    }

    public void onMobileNetworkSettingsClick() {
        log("onMobileNetworkSettingsClick:");
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        log("qti MobileNetworkSettings Enabled");
        // prepare intent to start qti MobileNetworkSettings activity
        intent.setComponent(new ComponentName("com.qualcomm.qti.networksetting",
               "com.qualcomm.qti.networksetting.MobileNetworkSettings"));
        startActivity(intent);
    }
    private String mManageMobilePlanMessage;
    public void onManageMobilePlanClick() {
        log("onManageMobilePlanClick:");
        mManageMobilePlanMessage = null;
        Resources resources = getActivity().getResources();

        NetworkInfo ni = mCm.getActiveNetworkInfo();
        if (mTm.hasIccCard() && (ni != null)) {
            // Check for carrier apps that can handle provisioning first
            Intent provisioningIntent = new Intent(TelephonyIntents.ACTION_CARRIER_SETUP);
            List<String> carrierPackages =
                    mTm.getCarrierPackageNamesForIntent(provisioningIntent);
            if (carrierPackages != null && !carrierPackages.isEmpty()) {
                if (carrierPackages.size() != 1) {
                    Log.w(TAG, "Multiple matching carrier apps found, launching the first.");
                }
                provisioningIntent.setPackage(carrierPackages.get(0));
                startActivity(provisioningIntent);
                return;
            }

            // Get provisioning URL
            String url = mCm.getMobileProvisioningUrl();
            if (!TextUtils.isEmpty(url)) {
                Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                        Intent.CATEGORY_APP_BROWSER);
                intent.setData(Uri.parse(url));
                intent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                        Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "onManageMobilePlanClick: startActivity failed" + e);
                }
            } else {
                // No provisioning URL
                String operatorName = mTm.getSimOperatorName();
                if (TextUtils.isEmpty(operatorName)) {
                    // Use NetworkOperatorName as second choice in case there is no
                    // SPN (Service Provider Name on the SIM). Such as with T-mobile.
                    operatorName = mTm.getNetworkOperatorName();
                    if (TextUtils.isEmpty(operatorName)) {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_unknown_sim_operator);
                    } else {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_no_provisioning_url, operatorName);
                    }
                } else {
                    mManageMobilePlanMessage = resources.getString(
                            R.string.mobile_no_provisioning_url, operatorName);
                }
            }
        } else if (mTm.hasIccCard() == false) {
            // No sim card
            mManageMobilePlanMessage = resources.getString(R.string.mobile_insert_sim_card);
        } else {
            // NetworkInfo is null, there is no connection
            mManageMobilePlanMessage = resources.getString(R.string.mobile_connect_to_internet);
        }
        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            log("onManageMobilePlanClick: message=" + mManageMobilePlanMessage);
            showDialog(MANAGE_MOBILE_PLAN_DIALOG_ID);
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        log("onCreateDialog: dialogId=" + dialogId);
        switch (dialogId) {
            case MANAGE_MOBILE_PLAN_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(mManageMobilePlanMessage)
                            .setCancelable(false)
                            .setPositiveButton(com.android.internal.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    log("MANAGE_MOBILE_PLAN_DIALOG.onClickListener id=" + id);
                                    mManageMobilePlanMessage = null;
                                }
                            })
                            .create();
        }
        return super.onCreateDialog(dialogId);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.WIRELESS;
    }

    private void broadcastWifiCallingStatus(Context ctx, boolean isTurnOn, int preference) {
        if(DEBUG) Log.d(TAG, "broadcastWifiCallingStatus:");
        Intent intent = new Intent(isTurnOn ? ACTION_WIFI_CALL_ON
                    : ACTION_WIFI_CALL_OFF);
        intent.putExtra(WIFI_CALLING_PREFERRED, preference);
        ctx.sendBroadcast(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mManageMobilePlanMessage = savedInstanceState.getString(SAVED_MANAGE_MOBILE_PLAN_MSG);
        }
        log("onCreate: mManageMobilePlanMessage=" + mManageMobilePlanMessage);

        mCm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mTm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mPm = getPackageManager();
        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        addPreferencesFromResource(R.xml.wireless_settings);

        final boolean isAdmin = mUm.isAdminUser();

        final Activity activity = getActivity();
        mAirplaneModePreference = (SwitchPreference) findPreference(KEY_TOGGLE_AIRPLANE);
        SwitchPreference nfc = (SwitchPreference) findPreference(KEY_TOGGLE_NFC);
        RestrictedPreference androidBeam = (RestrictedPreference) findPreference(
                KEY_ANDROID_BEAM_SETTINGS);
        if (QtiImsExtUtils.isCarrierOneSupported()
                    && QtiImsExtUtils.isCarrierOneCallSettingsAvailable(getActivity())) {
            Preference callSettingsPref = (PreferenceScreen) findPreference(KEY_CALL_SETTINGS);
            Intent callSettingsIntent = new Intent();
            callSettingsIntent.setAction("org.codeaurora.CALL_SETTINGS");
            callSettingsPref.setIntent(callSettingsIntent);
        }

        mAirplaneModeEnabler = new AirplaneModeEnabler(activity, mAirplaneModePreference);
        mNfcEnabler = new NfcEnabler(activity, nfc, androidBeam);

        mEnhancedWFCSettingsEnabled = getActivity().getResources().getBoolean(
                    R.bool.wifi_call_enhanced_setting);
        if (mEnhancedWFCSettingsEnabled) {
            mButtonWfc = (WFCPreference) findPreference(KEY_WFC_ENHANCED_SETTINGS);
            removePreference(KEY_WFC_SETTINGS);
            mButtonWfc.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.MAIN");
                    intent.setPackage("com.qualcomm.qti.wfcservice");
                    intent.setClassName("com.qualcomm.qti.wfcservice",
                            "com.qualcomm.qti.wfcservice.WifiCallingEnhancedSettings");
                    mButtonWfc.setIntent(intent);
                    return false;
                }
            });

            mButtonWfc.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    int wfcPreference = -1;
                    try {
                        wfcPreference = mWFCService.getWifiCallingPreference();
                    } catch (RemoteException re) {
                        Log.e(TAG, "getWifiCallingPreference RemoteException");
                        return false;
                    }

                    boolean isChecked = (Boolean) value;

                    try {
                        mWFCService.setWifiCalling(isChecked, wfcPreference);
                    } catch (RemoteException r) {
                        Log.e(TAG, "setWifiCalling RemoteException");
                    }

                    if (!isChecked) {
                        ((WFCPreference) preference).setSummary(R.string.disabled);
                    }

                    broadcastWifiCallingStatus(getActivity(), isChecked, wfcPreference);
                    return false;
                }
            });
        } else {
            mButtonWfc = (PreferenceScreen) findPreference(KEY_WFC_SETTINGS);
            mProvisioningVWiFiEnabled = this.getResources().
                    getBoolean(R.bool.config_provision_wificalling_pref);
                if (mProvisioningVWiFiEnabled) {
                    String value = SystemProperties.get(KEY,"false");
                    if (!Boolean.parseBoolean(value)) {
                        final Context context = getActivity();
                        ImsManager.setWfcSetting(context, false);
                    }
                    mButtonWfc.setEnabled(Boolean.parseBoolean(value));
                }
            removePreference(KEY_WFC_ENHANCED_SETTINGS);
        }

        String toggleable = Settings.Global.getString(activity.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);

        //enable/disable wimax depending on the value in config.xml
        final boolean isWimaxEnabled = isAdmin && this.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if (!isWimaxEnabled || RestrictedLockUtils.hasBaseUserRestriction(activity,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, UserHandle.myUserId())) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = findPreference(KEY_WIMAX_SETTINGS);
            if (ps != null) root.removePreference(ps);
        } else {
            if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIMAX )
                    && isWimaxEnabled) {
                Preference ps = findPreference(KEY_WIMAX_SETTINGS);
                ps.setDependency(KEY_TOGGLE_AIRPLANE);
            }
        }

        // Manually set dependencies for Wifi when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIFI)) {
            findPreference(KEY_VPN_SETTINGS).setDependency(KEY_TOGGLE_AIRPLANE);
        }
        // Disable VPN.
        // TODO: http://b/23693383
        if (!isAdmin || RestrictedLockUtils.hasBaseUserRestriction(activity,
                UserManager.DISALLOW_CONFIG_VPN, UserHandle.myUserId())) {
            removePreference(KEY_VPN_SETTINGS);
        }

        // Manually set dependencies for Bluetooth when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_BLUETOOTH)) {
            // No bluetooth-dependent items in the list. Code kept in case one is added later.
        }

        // Manually set dependencies for NFC when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_NFC)) {
            findPreference(KEY_TOGGLE_NFC).setDependency(KEY_TOGGLE_AIRPLANE);
            findPreference(KEY_ANDROID_BEAM_SETTINGS).setDependency(KEY_TOGGLE_AIRPLANE);
        }

        // Remove NFC if not available
        mNfcAdapter = NfcAdapter.getDefaultAdapter(activity);
        if (mNfcAdapter == null) {
            getPreferenceScreen().removePreference(nfc);
            getPreferenceScreen().removePreference(androidBeam);
            mNfcEnabler = null;
        }

        // Remove Mobile Network Settings and Manage Mobile Plan for secondary users,
        // if it's a wifi-only device, or for MSIM devices
        if (!isAdmin || Utils.isWifiOnly(getActivity()) || Utils.showSimCardTile(getActivity()) ||
                RestrictedLockUtils.hasBaseUserRestriction(activity,
                        UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, UserHandle.myUserId())) {
            removePreference(KEY_MOBILE_NETWORK_SETTINGS);
            removePreference(KEY_MANAGE_MOBILE_PLAN);
        } else {
            mIsNetworkSettingsAvailable = Utils.isNetworkSettingsApkAvailable(getActivity());
        }
        // Remove Mobile Network Settings and Manage Mobile Plan
        // if config_show_mobile_plan sets false.
        final boolean isMobilePlanEnabled = this.getResources().getBoolean(
                R.bool.config_show_mobile_plan);
        if (!isMobilePlanEnabled) {
            Preference pref = findPreference(KEY_MANAGE_MOBILE_PLAN);
            if (pref != null) {
                removePreference(KEY_MANAGE_MOBILE_PLAN);
            }
        }

        // Remove Airplane Mode settings if it's a stationary device such as a TV.
        if (mPm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            removePreference(KEY_TOGGLE_AIRPLANE);
        }

        // Enable Proxy selector settings if allowed.
        Preference mGlobalProxy = findPreference(KEY_PROXY_SETTINGS);
        final DevicePolicyManager mDPM = (DevicePolicyManager)
                activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        // proxy UI disabled until we have better app support
        getPreferenceScreen().removePreference(mGlobalProxy);
        mGlobalProxy.setEnabled(mDPM.getGlobalProxyAdmin() == null);

        // Disable Tethering if it's not allowed or if it's a wifi-only device
        final ConnectivityManager cm =
                (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

        final boolean adminDisallowedTetherConfig = RestrictedLockUtils.checkIfRestrictionEnforced(
                activity, UserManager.DISALLOW_CONFIG_TETHERING, UserHandle.myUserId()) != null;
        if ((!cm.isTetheringSupported() && !adminDisallowedTetherConfig) ||
                RestrictedLockUtils.hasBaseUserRestriction(activity,
                        UserManager.DISALLOW_CONFIG_TETHERING, UserHandle.myUserId())) {
            getPreferenceScreen().removePreference(findPreference(KEY_TETHER_SETTINGS));
        } else if (!adminDisallowedTetherConfig) {
            Preference p = findPreference(KEY_TETHER_SETTINGS);
            p.setTitle(com.android.settingslib.Utils.getTetheringLabel(cm));

            if (this.getResources().getBoolean(
                    R.bool.config_tethering_settings_display_summary)){
                RestrictedPreference rp = (RestrictedPreference) p;
                rp.useAdminDisabledSummary(false);
                p.setSummary(R.string.tethering_settings_summary);
            }
            // Grey out if provisioning is not available.
            p.setEnabled(!TetherSettings
                    .isProvisioningNeededButUnavailable(getActivity()));

            mLteEnabled = getActivity().getResources()
                    .getBoolean(com.android.internal.R.bool.config_volte_preferred);
            mVoLtePreference = (SwitchPreference) findPreference(VOICE_OVER_LTE);
            if (mLteEnabled) {
                mVoLtePreference.setEnabled(false);
                mImsExtManager = QtiImsExtManager.getInstance();
                if(mImsExtManager != null) {
                    try {
                        Log.d(TAG,"queryVoltePreference!");
                        mImsExtManager.queryVoltePreference(mImsExtManager.getImsPhoneId(),
                                imsInterfaceListener);
                    } catch (QtiImsException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            } else {
                getPreferenceScreen().removePreference(mVoLtePreference);
            }
        }

        // Remove network reset if not allowed
        if (RestrictedLockUtils.hasBaseUserRestriction(activity,
                UserManager.DISALLOW_NETWORK_RESET, UserHandle.myUserId())) {
            removePreference(KEY_NETWORK_RESET);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mEnhancedWFCSettingsEnabled && mWFCService == null) {
            //bind WFC service
            final Intent intentWfc = new Intent();
            intentWfc.setAction("com.qualcomm.qti.wfcservice.IWFCService");
            intentWfc.setPackage("com.qualcomm.qti.wfcservice");
            getActivity().bindService(intentWfc, mConnection, Context.BIND_AUTO_CREATE);
        }

        mAirplaneModeEnabler.resume();
        if (mNfcEnabler != null) {
            mNfcEnabler.resume();
        }

        // update Wi-Fi Calling setting
        final Context context = getActivity();
        if (ImsManager.isWfcEnabledByPlatform(context) &&
                ImsManager.isWfcProvisionedOnDevice(context)) {
            getPreferenceScreen().addPreference(mButtonWfc);

            if (!mEnhancedWFCSettingsEnabled) {
                mButtonWfc.setSummary(WifiCallingSettings.getWfcModeSummary(
                       context, ImsManager.getWfcMode(context)));
            } else {
                if (!ImsManager.isWfcEnabledByUser(context)) {
                    ((WFCPreference) mButtonWfc).setChecked(false);
                    ((WFCPreference) mButtonWfc).setSummary(R.string.disabled);
                } else {
                    ((WFCPreference) mButtonWfc).setChecked(true);
                    ((WFCPreference) mButtonWfc).setSummary(
                            SystemProperties.get("sys.wificall.status.msg"));
                }
            }

            mButtonWfc.setSummary(WifiCallingSettings.getWfcModeSummary(
                    context, ImsManager.getWfcMode(context, mTm.isNetworkRoaming())));
        } else {
            log("WFC not supported. Remove WFC menu");
            if (mButtonWfc != null) getPreferenceScreen().removePreference(mButtonWfc);
        }
        if (QtiImsExtUtils.isCarrierOneSupported() && mUm.isAdminUser()
                 && QtiImsExtUtils.isCarrierOneCallSettingsAvailable(getActivity())) {
             //Call Settings already have WFC settings.
            removePreference(KEY_WFC_SETTINGS);
        } else {
            removePreference(KEY_CALL_SETTINGS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            outState.putString(SAVED_MANAGE_MOBILE_PLAN_MSG, mManageMobilePlanMessage);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mAirplaneModeEnabler.pause();
        if (mNfcEnabler != null) {
            mNfcEnabler.pause();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_EXIT_ECM) {
            Boolean isChoiceYes = data.getBooleanExtra(EXIT_ECM_RESULT, false);
            // Set Airplane mode based on the return value and checkbox state
            mAirplaneModeEnabler.setAirplaneModeInECM(isChoiceYes,
                    mAirplaneModePreference.isChecked());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_more_networks;
    }

    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(
                    Context context, boolean enabled) {
                // Remove wireless settings from search in demo mode
                if (UserManager.isDeviceInDemoMode(context)) {
                    return Collections.emptyList();
                }
                SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.wireless_settings;
                return Arrays.asList(sir);
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                final ArrayList<String> result = new ArrayList<String>();

                final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
                final boolean isSecondaryUser = !um.isAdminUser();
                final boolean isWimaxEnabled = !isSecondaryUser
                        && context.getResources().getBoolean(
                        com.android.internal.R.bool.config_wimaxEnabled);
                if (!isWimaxEnabled) {
                    result.add(KEY_WIMAX_SETTINGS);
                }

                if (isSecondaryUser) { // Disable VPN
                    result.add(KEY_VPN_SETTINGS);
                }

                // Remove NFC if not available
                final NfcManager manager = (NfcManager)
                        context.getSystemService(Context.NFC_SERVICE);
                if (manager != null) {
                    NfcAdapter adapter = manager.getDefaultAdapter();
                    if (adapter == null) {
                        result.add(KEY_TOGGLE_NFC);
                        result.add(KEY_ANDROID_BEAM_SETTINGS);
                    }
                }

                // Remove Mobile Network Settings and Manage Mobile Plan if it's a wifi-only device.
                if (isSecondaryUser || Utils.isWifiOnly(context)) {
                    result.add(KEY_MOBILE_NETWORK_SETTINGS);
                    result.add(KEY_MANAGE_MOBILE_PLAN);
                }

                // Remove Mobile Network Settings and Manage Mobile Plan
                // if config_show_mobile_plan sets false.
                final boolean isMobilePlanEnabled = context.getResources().getBoolean(
                        R.bool.config_show_mobile_plan);
                if (!isMobilePlanEnabled) {
                    result.add(KEY_MANAGE_MOBILE_PLAN);
                }

                final PackageManager pm = context.getPackageManager();

                // Remove Airplane Mode settings if it's a stationary device such as a TV.
                if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
                    result.add(KEY_TOGGLE_AIRPLANE);
                }

                // proxy UI disabled until we have better app support
                result.add(KEY_PROXY_SETTINGS);

                // Disable Tethering if it's not allowed or if it's a wifi-only device
                ConnectivityManager cm = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (isSecondaryUser || !cm.isTetheringSupported()) {
                    result.add(KEY_TETHER_SETTINGS);
                }

                if (!ImsManager.isWfcEnabledByPlatform(context) ||
                        !ImsManager.isWfcProvisionedOnDevice(context)) {
                    result.add(KEY_WFC_SETTINGS);
                }

                if (RestrictedLockUtils.hasBaseUserRestriction(context,
                        UserManager.DISALLOW_NETWORK_RESET, UserHandle.myUserId())) {
                    result.add(KEY_NETWORK_RESET);
                }

                return result;
            }
        };

    /*add for VoLTE Preferred on/off begin*/
    /**
     * add Listener to get volte preference update and query response;
    */
    private IQtiImsExtListener imsInterfaceListener = new QtiImsExtListenerBaseImpl() {
        @Override
        public void onVoltePreferenceUpdated(int result) {
            Log.d(TAG, "voltePreferenceUpdated, result = " + result);
            Message msg = mImsResponseHandler.obtainMessage();
            msg.what = MSG_ID_VOLTE_PREFERENCE_UPDATED_RESPONSE;
            msg.arg1 = result;
            msg.sendToTarget();
        }

        @Override
        public void onVoltePreferenceQueried(int result, int mode) {
            Log.d(TAG, "voltePreferenceQueried, result = " + result + " mode = " + mode);
            Message msg = mImsResponseHandler.obtainMessage();
            msg.what = MSG_ID_VOLTE_PREFERENCE_QUERIED_RESPONCE;
            msg.arg1 = result;
            msg.arg2 = mode;
            msg.sendToTarget();
        }
    };

    private Handler mImsResponseHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ID_VOLTE_PREFERENCE_UPDATED_RESPONSE:
                    int updateResult = msg.arg1;
                    if(updateResult == QtiImsExtUtils.QTI_IMS_REQUEST_ERROR) {
                        mVoLtePreference.setChecked(!mVoLtePreference.isChecked());
                    }
                    try{
                        Settings.Global.putInt(getActivity().getContentResolver(),
                                Settings.Global.VOLTE_PREFERRED_ON,
                                mVoLtePreference.isChecked()? 1 : 0);
                    }catch (Exception e) {

                    }
                    break;
                case MSG_ID_VOLTE_PREFERENCE_QUERIED_RESPONCE:
                    int queryResult = msg.arg1;
                    int mode = msg.arg2;
                    int value = Settings.Global.getInt(getActivity()
                            .getContentResolver(), Settings.Global.VOLTE_PREFERRED_ON, 1);
                    Log.d(TAG, "Local setting status = " + value);
                    mVoLtePreference.setEnabled(true);
                    mVoLtePreference.setChecked(value == 1 ? true : false);
                    if((queryResult == QtiImsExtUtils.QTI_IMS_REQUEST_SUCCESS) && (mode != value)) {
                       try {
                            if(mImsExtManager != null) {
                                mImsExtManager.updateVoltePreference(
                                        mImsExtManager.getImsPhoneId(),
                                        value,
                                        imsInterfaceListener);
                            }
                        } catch (QtiImsException e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                    break;
            }
        }
    };
    /*add for VoLTE Preferred on/off end*/
}
