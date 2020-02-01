package com.fognl.herelink.herelinksettings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fognl.herelink.herelinksettings.fakes.ID2DInfoListener;
import com.fognl.herelink.herelinksettings.fakes.ID2DService;
import com.fognl.herelink.herelinksettings.fakes.ServiceState;
import com.fognl.herelink.herelinksettings.fakes.SignalStrength;
import com.fognl.herelink.herelinksettings.fakes.SystemProperties;

import java.util.Arrays;
import java.util.List;

public class D2DSettingsActivityJava extends AppCompatActivity {

    private static final String TAG = D2DSettingsActivity.class.getSimpleName();

    private static final int QUERY_STATUS_INTERVAL = 100;
    private static final int INVALID = 0x7FFFFFFF;

    private static final String D2D_DEVICE_CONTROLLER = "controller";
    private static final String DEFAULT_D2D_IFACE_VALUE = "lmi40";
    private static final String D2D_DEFAULT_DL_FREQUENCY = "47400";

    private static final String PROPERTY_D2D_DEVICE_NAME = "persist.sys.d2d.device";
    private static final String PROPERTY_D2D_CURRENT_DL_FREQUENCY = "persist.sys.d2d.dl.frequency";
    private static final String PROPERTY_D2D_CURRENT_COMMAND_ID = "persist.sys.d2d.cmd.id";
    private static final String PROPERTY_D2D_CURRENT_COMMAND_PARAMETER = "persist.sys.d2d.cmd.parameter";
    private static final String PROPERTY_D2D_UL_FREQ_HOP_ENABLED = "persist.sys.d2d.ul.freqhop";
    private static final String PROPERTY_D2D_DL_FREQ_HOP_ENABLED = "persist.sys.d2d.dl.freqhop";
    private static final String PROPERTY_D2D_IFACE_DEV = "persist.sys.d2d.iface.dev";
    private static final String PROPERTY_D2D_DESIRE_RADIO_POWER = "persist.radio.sim1.power";
    private static final String PROPERTY_D2D_DYNAMIC_FREQ_UPDT_ENABLED = "persist.sys.d2d.fupdt.enabled";
    private static final String PROPERTY_D2D_UL_BANDWIDTH = "persist.sys.d2d.ul.bandwidth";
    private static final String PROPERTY_D2D_DL_BANDWIDTH = "persist.sys.d2d.dl.bandwidth";

    private static final int EVENT_GET_CURRENT_STATUS_REQUEST = 1;
    private static final int EVENT_GET_RADIO_POWER = 2;
    private static final int EVENT_SEND_D2D_CTRL_CMD = 3;
    private static final int EVENT_SEND_D2D_CTRL_CMD_DONE = 4;
    private static final int EVENT_REQUEST_FREQUENCY_NEGOTIATION = 5;
    private static final int EVENT_REQUEST_FREQUENCY_NEGOTIATION_DONE = 6;
    private static final int EVENT_QUERY_DL_FREQUENCY_POINT = 7;
    private static final int EVENT_FREQUENCY_HOPPING_CTRL = 8;
    private static final int EVENT_FREQUENCY_HOPPING_CTRL_DONE = 9;
    private static final int EVENT_QUERY_FREQUENCY_HOPPING_STATE = 10;
    private static final int EVENT_CONFIG_D2D_DL_BW = 11;
    private static final int EVENT_CONFIG_D2D_UL_BW = 12;
    private static final int EVENT_CONFIG_D2D_BW_DONE = 13;

    private static final String D2D_INFO_UL_HOPPING_CTRL = "UL_HOP";
    private static final String D2D_INFO_DL_HOPPING_CTRL = "DL_HOP";
    private static final String D2D_CTRL_CMD = "CTRL_CMD";
    private static final String D2D_BW_CONFIG = "BW_CFG";
    private static final String D2D_CMD_RESULT_CODE = "ERR_CODE";


    private String mDlFrequencyNum = null;
    private boolean mD2DLinkConnected = false;
    private boolean mBound = false;
    private boolean mIsRadioPowerOn = true;
    private boolean mIsUlFreqHoppingEnabled = false;
    private boolean mIsDlFreqHoppingEnabled = false;
    private int mPlaneUlGrantBW = 0;
    private int mPlaneUlDataRate = 0;
    private int mSnrGcsMaster = -40;
    private int mSnrGcsSlave = -40;
    private int mSnrUavMaster = -40;
    private int mSnrUavSlave = -40;
    private int mAgcGcsMaster = 100;
    private int mAgcGcsSlave = 100;
    private int mAgcUavMaster = 100;
    private int mAgcUavSlave = 100;
    private int mControllerMasterRsrp = INVALID;
    private int mControllerSlaveRsrp = INVALID;
    private int mPlaneMasterRsrp = INVALID;
    private int mPlaneSlaveRsrp = INVALID;

    private ID2DService mD2DService = null;

    private final Handler mMsgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String rspCmd = null;
            boolean isOk  = false;
            int errorCode = 0;
            Log.v(TAG, "mMsgHandler handleMessage msg.what = " + msg.what);
            switch (msg.what) {
                case EVENT_GET_CURRENT_STATUS_REQUEST:
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                        removeMessages(EVENT_GET_CURRENT_STATUS_REQUEST);
                        mMsgHandler.sendMessageDelayed(obtainMessage(EVENT_GET_CURRENT_STATUS_REQUEST),
                                QUERY_STATUS_INTERVAL);
                    } else {
                        try {
                            mD2DService.requestGetD2dInfo();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_REQUEST_FREQUENCY_NEGOTIATION:
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                        removeMessages(EVENT_REQUEST_FREQUENCY_NEGOTIATION);
                        mMsgHandler.sendMessageDelayed(obtainMessage(EVENT_REQUEST_FREQUENCY_NEGOTIATION),
                                QUERY_STATUS_INTERVAL);
                    } else {
                        try {
                            mD2DService.requestD2dFreqNegotiation();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_REQUEST_FREQUENCY_NEGOTIATION_DONE:
                    hideProgress();
                    errorCode = msg.getData().getInt(D2D_CMD_RESULT_CODE);
                    if (errorCode != 0) {
                        Toast.makeText(D2DSettingsActivityJava.this,
                                getString(R.string.d2d_freq_negotiation_fail),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                case EVENT_GET_RADIO_POWER:
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                        removeMessages(EVENT_GET_RADIO_POWER);
                        mMsgHandler.sendMessageDelayed(obtainMessage(EVENT_GET_RADIO_POWER),
                                QUERY_STATUS_INTERVAL);
                    } else {
                        try {
                            mIsRadioPowerOn = mD2DService.requestGetRadioPower();
                            mMsgHandler.post(updateRadioPower);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_SEND_D2D_CTRL_CMD:
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                        removeMessages(EVENT_SEND_D2D_CTRL_CMD);
                        mMsgHandler.sendMessageDelayed(obtainMessage(EVENT_SEND_D2D_CTRL_CMD),
                                QUERY_STATUS_INTERVAL);
                    } else {
                        try {
                            mD2DService.requestSendD2DCtrlCmd(msg.getData().getString(D2D_CTRL_CMD));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_SEND_D2D_CTRL_CMD_DONE:
                    errorCode = msg.getData().getInt(D2D_CMD_RESULT_CODE);
                    if (0 == errorCode) {
                        Toast.makeText(D2DSettingsActivityJava.this, getString(R.string.str_prompt_reboot_device), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(D2DSettingsActivityJava.this, getString(R.string.d2d_config_command_err), Toast.LENGTH_SHORT).show();
                    }
                    break;
                case EVENT_QUERY_DL_FREQUENCY_POINT:
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                        removeMessages(EVENT_QUERY_DL_FREQUENCY_POINT);
                        mMsgHandler.sendMessageDelayed(obtainMessage(EVENT_QUERY_DL_FREQUENCY_POINT),
                                QUERY_STATUS_INTERVAL);
                    } else {
                        try {
                            mD2DService.requestGetD2dDlFrequencyPoint();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_FREQUENCY_HOPPING_CTRL:
                    removeMessages(EVENT_FREQUENCY_HOPPING_CTRL);
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                    } else {
                        try {
                            mD2DService.requestD2dFreqHopCtrl(msg.getData().getString(D2D_INFO_DL_HOPPING_CTRL),
                                    msg.getData().getString(D2D_INFO_UL_HOPPING_CTRL));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_FREQUENCY_HOPPING_CTRL_DONE:
                    hideProgress();
                    errorCode = msg.getData().getInt(D2D_CMD_RESULT_CODE);
                    if (0 == errorCode) {
                        Toast.makeText(D2DSettingsActivityJava.this,
                                getString(R.string.d2d_freq_hopping_ctrl_success),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(D2DSettingsActivityJava.this,
                                getString(R.string.d2d_freq_hopping_ctrl_fail),
                                Toast.LENGTH_SHORT).show();
                    }
                    // query frequency hopping state to ensure the setting request take effect
                    try {
                        mD2DService.requestGetD2dFreqHopState();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                case EVENT_QUERY_FREQUENCY_HOPPING_STATE:
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                        removeMessages(EVENT_QUERY_FREQUENCY_HOPPING_STATE);
                        mMsgHandler.sendMessageDelayed(obtainMessage(EVENT_QUERY_FREQUENCY_HOPPING_STATE),
                                QUERY_STATUS_INTERVAL);
                    } else {
                        try {
                            mD2DService.requestGetD2dFreqHopState();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_CONFIG_D2D_DL_BW:
                    removeMessages(EVENT_CONFIG_D2D_DL_BW);
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                    } else {
                        try {
                            mD2DService.requestConfigD2dBandwidth(true, msg.getData().getInt(D2D_BW_CONFIG));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_CONFIG_D2D_UL_BW:
                    removeMessages(EVENT_CONFIG_D2D_UL_BW);
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry");
                    } else {
                        try {
                            mD2DService.requestConfigD2dBandwidth(false, msg.getData().getInt(D2D_BW_CONFIG));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case EVENT_CONFIG_D2D_BW_DONE:
                    errorCode = msg.getData().getInt(D2D_CMD_RESULT_CODE);
                    if (0 == errorCode) {
                        Toast.makeText(D2DSettingsActivityJava.this,
                                getString(R.string.d2d_bw_config_success),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(D2DSettingsActivityJava.this,
                                getString(R.string.d2d_bw_config_fail),
                                Toast.LENGTH_SHORT).show();
                    }
//                    updateD2dBandwidth();
                    break;
                default:
                    Log.v(TAG, "mMsgHandler handleMessage invalid msg: " + msg.what);
            }
        }
    };


    private final ID2DInfoListener mD2DInfoListener = new ID2DInfoListener.Stub() {
        public void onD2DServiceStatusChanged(ServiceState ss) {
            Log.v(TAG, "onD2DServiceStatusChanged: service state = " + ss);
            int state = ss.getState();
            if (ss.STATE_IN_SERVICE == state) {
                mD2DLinkConnected = true;
            } else {
                mD2DLinkConnected = false;
                // reset D2D info when D2D link disconnected
                mMsgHandler.post(resetD2DInfo);
            }
            mMsgHandler.post(updateServiceState);
        }

        public void onD2DSignalStrengthChanged(SignalStrength ss) {
            Log.v(TAG, "onD2DSignalStrengthChanged: signal strength = " + ss);

            if (ss.getLteRsrq() == INVALID) {
                Log.v(TAG, "onD2DSignalStrengthChanged: plane");
                if (ss.getLteRsrp() != INVALID) {
                    mPlaneMasterRsrp = ss.getLteRsrp();
                }

                if (ss.getLteSignalStrength() != 99) {
                    mPlaneSlaveRsrp = -1 * ss.getLteSignalStrength();
                }
            } else {
                Log.v(TAG, "onD2DSignalStrengthChanged: controller");
                if (ss.getLteRsrp() != INVALID) {
                    mControllerMasterRsrp = ss.getLteRsrp();
                }
                if (ss.getLteSignalStrength() != 99) {
                    mControllerSlaveRsrp = -1 * ss.getLteSignalStrength();
                }
            }

            mMsgHandler.post(updateSignalStrength);
        }

        public void onD2DFrequencyListReceived() {
            Log.v(TAG, "onD2DFrequencyListReceived");
        }

        public void onD2DULSpeedChanged(int ul_bw, int ul_bit_rate) {
            Log.v(TAG, "onD2DULSpeedChanged: ul_bw = " + ul_bw + ", ul_bit_rate = " + ul_bit_rate);
            mPlaneUlGrantBW = ul_bw;
            mPlaneUlDataRate = ul_bit_rate;
            mMsgHandler.post(updateQosInfo);
        }

        public void onD2DSnrGcsChanged(int snr_master, int snr_slave) {
            Log.v(TAG, "onD2DSnrGcsChanged: snr_master = " + snr_master + " snr_slave = " + snr_slave);
            mSnrGcsMaster = snr_master;
            mSnrGcsSlave = snr_slave;
            mMsgHandler.post(updateQosInfo);
        }

        public void onD2DSnrUavChanged(int snr_master, int snr_slave) {
            Log.v(TAG, "onD2DSnrUavChanged: snr_master = " + snr_master + " snr_slave = " + snr_slave);
            mSnrUavMaster = snr_master;
            mSnrUavSlave = snr_slave;
            mMsgHandler.post(updateQosInfo);
        }

        public void onD2DAgcGcsChanged(int agc_master, int agc_slave) {
            Log.v(TAG, "onD2DAgcGcsChanged: agc_master = " + agc_master + " agc_slave = " + agc_slave);
            mAgcGcsMaster = agc_master;
            mAgcGcsSlave = agc_slave;
            mMsgHandler.post(updateQosInfo);
        }

        public void onD2DAgcUavChanged(int agc_master, int agc_slave) {
            Log.v(TAG, "onD2DAgcUavChanged: agc_master = " + agc_master + " agc_slave = " + agc_slave);
            mAgcUavMaster = agc_master;
            mAgcUavSlave = agc_slave;
            mMsgHandler.post(updateQosInfo);
        }

        public void onRequestFreqNegotiationDone(int errorCode) {
            Bundle args   = new Bundle();
            Message msg = mMsgHandler.obtainMessage(EVENT_REQUEST_FREQUENCY_NEGOTIATION_DONE);
            Log.v(TAG, "onRequestFreqNegotiationDone: errorCode = " + errorCode);

            args.putInt(D2D_CMD_RESULT_CODE, errorCode);
            msg.setData(args);
            mMsgHandler.sendMessage(msg);
        }

        public void onRequestFreqResetDone(int errorCode) {}
        public void onD2DInterferenceListReceived() {}

        public void onRequestFreqHopCtrlDone(int errorCode) {
            Bundle args   = new Bundle();
            Message msg = mMsgHandler.obtainMessage(EVENT_FREQUENCY_HOPPING_CTRL_DONE);
            Log.v(TAG, "onRequestFreqHopCtrlDone: errorCode = " + errorCode);

            args.putInt(D2D_CMD_RESULT_CODE, errorCode);
            msg.setData(args);
            mMsgHandler.sendMessage(msg);
        }

        public void onRequestGetFreqHopStateDone(int dl_hop_enabled, int ul_hop_enabled, int errorCode) {
            Log.v(TAG, "onRequestGetFreqHopStateDone: dl_hop_enabled = " + dl_hop_enabled + ", ul_hop_enabled = " +
                    ul_hop_enabled + ", errorCode = " + errorCode);
            if (0 == errorCode) {
                mIsDlFreqHoppingEnabled = (1 == dl_hop_enabled) ? true : false;
                mIsUlFreqHoppingEnabled = (1 == ul_hop_enabled) ? true : false;
                mMsgHandler.post(updateFreqHopState);
            }
        }

        public void onD2DRadioPowerChanged(boolean is_power_on) {
            Log.v(TAG, "onD2DRadioPowerChanged: is_power_on = " + is_power_on);
            mIsRadioPowerOn = is_power_on;
            mMsgHandler.post(updateRadioPower);
        }

        public void onRequestGetDlFrequencyPointDone(int dl_frequency_point, int errorCode) {
            Log.v(TAG, "onRequestGetDlFrequencyPointDone: dl_frequency_point = " + dl_frequency_point +
                    ", errorCode = " + errorCode);
            if (0 == errorCode) {
                mDlFrequencyNum = String.valueOf(dl_frequency_point);
                mMsgHandler.post(updateDlFreq);
            }
        }

        public void onRequestSendCtrlCmdDone(int errorCode) {
            Bundle args   = new Bundle();
            Message msg = mMsgHandler.obtainMessage(EVENT_SEND_D2D_CTRL_CMD_DONE);
            Log.v(TAG, "onRequestSendCtrlCmdDone: errorCode = " + errorCode);

            args.putInt(D2D_CMD_RESULT_CODE, errorCode);
            msg.setData(args);
            mMsgHandler.sendMessage(msg);
        }

        public void onRequestConfigBandwidthDone(int errorCode) {
            Bundle args   = new Bundle();
            Message msg = mMsgHandler.obtainMessage(EVENT_CONFIG_D2D_BW_DONE);
            Log.v(TAG, "onRequestConfigBandwidthDone: errorCode = " + errorCode);

            args.putInt(D2D_CMD_RESULT_CODE, errorCode);
            msg.setData(args);
            mMsgHandler.sendMessage(msg);
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected (ComponentName componentName, IBinder service) {
            Log.v(TAG, "D2DService connected");
            mD2DService = ID2DService.Stub.Companion.asInterface(service);
            try {
                mD2DService.registerForD2dInfoChanged(mD2DInfoListener);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.v(TAG, "D2DService disconnected");
            mD2DService = null;
            mBound = false;
        }
    };

    private final Runnable updateSignalStrength = new Runnable() {
        @Override
        public void run() {
            if (mPlaneMasterRsrp != INVALID && mPlaneSlaveRsrp != INVALID) {
                mVehicleSignalStrengthText.setText(getString(R.string.str_master_rsrp) + String.valueOf(mPlaneMasterRsrp) + " dBm; " +
                        getString(R.string.str_slave_rsrp) + String.valueOf(mPlaneSlaveRsrp) + " dBm");
            } else if (mPlaneMasterRsrp != INVALID) {
                mVehicleSignalStrengthText.setText(getString(R.string.str_master_rsrp) + String.valueOf(mPlaneMasterRsrp) + " dBm; " +
                        getString(R.string.str_slave_rsrp) + getString(R.string.str_signal_strength_unknown));
            } else {
                mVehicleSignalStrengthText.setText(getString(R.string.str_signal_strength_unknown));
            }

            if (mControllerMasterRsrp != INVALID && mControllerSlaveRsrp != INVALID) {
                mControllerSignalStrengthText.setText(getString(R.string.str_master_rsrp) + String.valueOf(mControllerMasterRsrp) + " dBm; " +
                        getString(R.string.str_slave_rsrp) + String.valueOf(mControllerSlaveRsrp) + " dBm");
            } else if (mControllerMasterRsrp != INVALID) {
                mControllerSignalStrengthText.setText(getString(R.string.str_master_rsrp) + String.valueOf(mControllerMasterRsrp) + " dBm; " +
                        getString(R.string.str_slave_rsrp) + getString(R.string.str_signal_strength_unknown));
            } else {
                mControllerSignalStrengthText.setText(getString(R.string.str_signal_strength_unknown));
            }
        }
    };

    private final Runnable updateQosInfo = new Runnable() {
        @Override
        public void run() {
//            String qos_value = getString(R.string.str_plane_qos_value);
//            mQosText.setText(String.format(qos_value, mPlaneUlGrantBW, mPlaneUlDataRate,
//                mSnrGcsMaster, mSnrGcsSlave, mSnrUavMaster, mSnrUavSlave, mAgcGcsMaster, mAgcGcsSlave, mAgcUavMaster));
        }
    };

    private final Runnable updateServiceState = new Runnable() {
        @Override
        public void run() {
            String d2dLinkState = getString(R.string.str_d2d_disconnected);
            if (mD2DLinkConnected) {
                d2dLinkState = getString(R.string.str_d2d_connected);
            }
            mVehicleStatusText.setText(d2dLinkState);
        }
    };

    private final Runnable updateFreqHopState = new Runnable() {
        @Override
        public void run() {
            if (mIsUlFreqHoppingEnabled) {
                mUlFreqHopCheck.setChecked(true);
            } else {
                mUlFreqHopCheck.setChecked(false);
            }

            if (mIsDlFreqHoppingEnabled) {
                mDlFreqHopCheck.setChecked(true);
            } else {
                mDlFreqHopCheck.setChecked(false);
            }
        }
    };

    private final Runnable updateRadioPower = new Runnable() {
        @Override
        public void run() {
            if (!mIsRadioPowerOn) {
                mRadioPowerCheck.setChecked(true);
            } else {
                mRadioPowerCheck.setChecked(false);
            }
        }
    };

    private final Runnable updateDlFreq = new Runnable() {
        @Override
        public void run() {
            mDownlinkFrequencyText.setText(mDlFrequencyNum);
            SystemProperties.set(PROPERTY_D2D_CURRENT_DL_FREQUENCY, mDlFrequencyNum);
        }
    };

    private final Runnable resetD2DInfo = new Runnable() {
        @Override
        public void run() {
            mPlaneUlGrantBW       = 0;
            mPlaneUlDataRate      = 0;
            mSnrGcsMaster         = -40;
            mSnrGcsSlave          = -40;
            mSnrUavMaster         = -40;
            mSnrUavSlave          = -40;
            mAgcGcsMaster         = 100;
            mAgcGcsSlave          = 100;
            mAgcUavMaster         = 100;
            mAgcUavSlave          = 100;
            mPlaneMasterRsrp      = INVALID;
            mPlaneSlaveRsrp       = INVALID;
            mQosText.setText(getString(R.string.str_plane_qos_unknown));
        }
    };

    private CheckBox mUlFreqHopCheck = null;
    private CheckBox mDlFreqHopCheck = null;
    private CheckBox mRadioPowerCheck = null;
    private CheckBox mAutoFreqCheck = null;
    private Spinner mIfaceSpinner = null;
    private Spinner mDlBandwidthSpinner = null;
    private Spinner mUlBandwidthSpinner = null;
    private TextView mControllerSignalStrengthText = null;
    private TextView mVehicleSignalStrengthText = null;
    private TextView mVehicleStatusText = null;
    private TextView mQosText = null;
    private TextView mDownlinkFrequencyText = null;
    private EditText mConfigTypeEdit = null;
    private EditText mConfigValueEdit = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_d2d_settings);
        initViews();
        hideProgress();
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();
        unbindService();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        bindToD2DService();
        updateD2dIfaceDev();
        updateD2dBandwidth();
        updateFreqDynamicUpdtCheckBox();
        /* refresh D2D info */
        // query D2D link state and signal strength
        Message msg = mMsgHandler.obtainMessage(EVENT_GET_CURRENT_STATUS_REQUEST);
        mMsgHandler.sendMessage(msg);
        // query D2D downlink frequency point
        msg = mMsgHandler.obtainMessage(EVENT_QUERY_DL_FREQUENCY_POINT);
        mMsgHandler.sendMessage(msg);
        // query D2D radio power state
        msg = mMsgHandler.obtainMessage(EVENT_GET_RADIO_POWER);
        mMsgHandler.sendMessage(msg);
        // query D2D frequency hopping state
        msg = mMsgHandler.obtainMessage(EVENT_QUERY_FREQUENCY_HOPPING_STATE);
        mMsgHandler.sendMessage(msg);
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        super.onStop();
        hideProgress();
        mControllerSignalStrengthText.setText(R.string.str_signal_strength_unknown);
        mVehicleSignalStrengthText.setText(R.string.str_signal_strength_unknown);
        mVehicleStatusText.setText(R.string.str_d2d_disconnected);
    }

    public void bindToD2DService() {
        Log.v(TAG, "bindToD2DService");
        Intent toService = new Intent();
        toService.setClassName("com.pinecone.telephony", "com.pinecone.telephony.D2DService");
        this.bindService(toService, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void unbindService() {
        Log.v(TAG, "unbindToD2DService");
        if (mD2DService != null) {
            try {
                mD2DService.unregisterCallback(mD2DInfoListener);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mMsgHandler.removeCallbacksAndMessages(null);
        this.unbindService(mConnection);
        mD2DService = null;
        mBound = false;
    }

    private void initViews() {
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onSaveClick();
            }
        });

        findViewById(R.id.btn_request_negotiation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestFreqNegotiation();
            }
        });

        ((CheckBox)findViewById(R.id.chk_radio_power)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onRadioPowerChecked(b);
            }
        });

        ((CheckBox)findViewById(R.id.chk_uplink_freq_hopping)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onFreqHoppingChecked(true, b);
            }
        });

        ((CheckBox)findViewById(R.id.chk_downlink_freq_hopping)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onFreqHoppingChecked(false, b);
            }
        });

        ((CheckBox)findViewById(R.id.chk_auto_freq_select)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onAutoFreqSelectChecked(b);
            }
        });

        String curr_cmd_id;
        String curr_cmd_para;

        mUlFreqHopCheck = findViewById(R.id.chk_uplink_freq_hopping);
        mDlFreqHopCheck = findViewById(R.id.chk_downlink_freq_hopping);
        mRadioPowerCheck = findViewById(R.id.chk_radio_power);
        mAutoFreqCheck = findViewById(R.id.chk_auto_freq_select);
        mIfaceSpinner = findViewById(R.id.spin_d2d_iface_dev_setting);
        mDlBandwidthSpinner = findViewById(R.id.spin_dl_bandwidth);
        mUlBandwidthSpinner = findViewById(R.id.spin_ul_bandwidth);
        mControllerSignalStrengthText = findViewById(R.id.txt_controller_signal_strength);
        mVehicleSignalStrengthText = findViewById(R.id.txt_vehicle_signal_strength);
        mVehicleStatusText = findViewById(R.id.txt_vehicle_signal_state);
        mQosText = findViewById(R.id.txt_vehicle_qos);
        mDownlinkFrequencyText = findViewById(R.id.txt_downlink_freq_point);
        mConfigTypeEdit = findViewById(R.id.edit_command_type);
        mConfigValueEdit = findViewById(R.id.edit_command_value);

        mDlBandwidthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String[] array = getResources().getStringArray(R.array.entryvalues_d2d_config_bandwidth);
                setD2dBandwidth(true, array[i]);
            }

            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        mUlBandwidthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String[] array = getResources().getStringArray(R.array.entryvalues_d2d_config_bandwidth);
                setD2dBandwidth(false, array[i]);
            }

            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        mIfaceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                final String[] array = getResources().getStringArray(R.array.entryvalues_d2d_config_iface_dev);
                SystemProperties.set(PROPERTY_D2D_IFACE_DEV, array[i]);
            }

            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        curr_cmd_id = SystemProperties.get(PROPERTY_D2D_CURRENT_COMMAND_ID, "0");
        if (!curr_cmd_id.equals("0")) {
            mConfigTypeEdit.setText(curr_cmd_id);
        }

        curr_cmd_para = SystemProperties.get(PROPERTY_D2D_CURRENT_COMMAND_PARAMETER, "0");
        if (!curr_cmd_para.equals("0")) {
            mConfigValueEdit.setText(curr_cmd_para);
        }
    }

    private void onSaveClick() {
        Bundle  args           = new Bundle();
        Message msg            = mMsgHandler.obtainMessage(EVENT_SEND_D2D_CTRL_CMD);
        String  d2d_config_cmd = null;
        String  cmd_id         = mConfigTypeEdit.getText().toString().trim();
        String  cmd_parameter  = mConfigValueEdit.getText().toString().trim();
        if (cmd_id == null || cmd_id.equals("0")) {
            Toast.makeText(this, getString(R.string.d2d_config_command_id_err), Toast.LENGTH_SHORT).show();
            return;
        } else if (cmd_parameter == null || cmd_parameter.equals("0")) {
            Toast.makeText(this, getString(R.string.d2d_config_command_parameter_err), Toast.LENGTH_SHORT).show();
            return;
        }
        d2d_config_cmd = cmd_id + "," + cmd_parameter;
        SystemProperties.set(PROPERTY_D2D_CURRENT_COMMAND_ID, cmd_id);
        SystemProperties.set(PROPERTY_D2D_CURRENT_COMMAND_PARAMETER, cmd_parameter);

        args.putString(D2D_CTRL_CMD, d2d_config_cmd);
        msg.setData(args);
        mMsgHandler.sendMessage(msg);
    }

    private void updateFreqDynamicUpdtCheckBox() {
        String isAutoMode = SystemProperties.get(PROPERTY_D2D_DYNAMIC_FREQ_UPDT_ENABLED, "0");
        if (isAutoMode.equals("1")) {
            Log.v(TAG, "updateFreqDynamicUpdtCheckBox: set checked true");
            mAutoFreqCheck.setChecked(true);
        } else {
            Log.v(TAG, "updateFreqDynamicUpdtCheckBox: set checked false");
            mAutoFreqCheck.setChecked(false);
        }
    }

    private void setD2dRadioPower(boolean isPowerOn) {
        Settings.Global.putInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, isPowerOn ? 0 : 1);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", !isPowerOn);
//        sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void setD2dFreqHopping(boolean isUplink, boolean isEnabled) {
        Bundle args   = new Bundle();
        Message msg = mMsgHandler.obtainMessage(EVENT_FREQUENCY_HOPPING_CTRL);
        final int resId;

        if (isUplink) {
            args.putString(D2D_INFO_DL_HOPPING_CTRL, mIsDlFreqHoppingEnabled ? "1" : "0");
            args.putString(D2D_INFO_UL_HOPPING_CTRL, isEnabled ? "1" : "0");
            msg.setData(args);
            mMsgHandler.sendMessageDelayed(msg, QUERY_STATUS_INTERVAL);
            SystemProperties.set(PROPERTY_D2D_UL_FREQ_HOP_ENABLED, isEnabled ? "1" : "0");
            resId = R.string.status_freq_hop_up;
        } else {
            args.putString(D2D_INFO_DL_HOPPING_CTRL, isEnabled ? "1" : "0");
            args.putString(D2D_INFO_UL_HOPPING_CTRL, mIsUlFreqHoppingEnabled ? "1" : "0");
            msg.setData(args);
            mMsgHandler.sendMessageDelayed(msg, QUERY_STATUS_INTERVAL);
            SystemProperties.set(PROPERTY_D2D_DL_FREQ_HOP_ENABLED, isEnabled ? "1":"0");
            resId = R.string.status_freq_hop_dn;
        }

        showProgress(resId);
    }

    private void onRadioPowerChecked(boolean isChecked) {
        Log.v(TAG, "clickD2dRadioPower: isChecked = " + isChecked + ", mIsRadioPowerOn = " + mIsRadioPowerOn);
        mRadioPowerCheck.setEnabled(false);
        if (!isChecked && !mIsRadioPowerOn) {
            setD2dRadioPower(true);
        } else if (isChecked && mIsRadioPowerOn) {
            setD2dRadioPower(false);
        } else {
            Log.e(TAG, "clickD2dRadioPower invalid state");
            mRadioPowerCheck.setChecked(!isChecked);
        }
        mRadioPowerCheck.setEnabled(true);
    }

    private void onFreqHoppingChecked(boolean isUplink, boolean isChecked) {
        if (isUplink) {
            Log.v(TAG, "clickD2dFreqHoppingCtrl: Uplink ctrl, isChecked = " + isChecked +
                    ", mIsUlFreqHoppingEnabled = " + mIsUlFreqHoppingEnabled);
            mUlFreqHopCheck.setEnabled(false);
            if (!isChecked && mIsUlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, false);
            } else if (isChecked && !mIsUlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, true);
            } else {
                Log.e(TAG, "clickD2dFreqHoppingCtrl: Uplink ctrl, invalid state");
                mUlFreqHopCheck.setChecked(!isChecked);
            }
            mUlFreqHopCheck.setEnabled(true);
        } else {
            Log.v(TAG, "clickD2dFreqHoppingCtrl: Downlink ctrl, isChecked = " + isChecked +
                    ", mIsDlFreqHoppingEnabled = " + mIsDlFreqHoppingEnabled);
            mDlFreqHopCheck.setEnabled(false);
            if (!isChecked && mIsDlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, false);
            } else if (isChecked && !mIsDlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, true);
            } else {
                Log.e(TAG, "clickD2dFreqHoppingCtrl: Downlink ctrl, invalid state");
                mDlFreqHopCheck.setChecked(!isChecked);
            }
            mDlFreqHopCheck.setEnabled(true);
        }
    }

    private void onAutoFreqSelectChecked(boolean isChecked) {
        Log.v(TAG, "clickD2dFreqDynamicUpdtCtrl: isChecked = " + isChecked);
        mAutoFreqCheck.setEnabled(false);
        if (!isChecked) {
            SystemProperties.set(PROPERTY_D2D_DYNAMIC_FREQ_UPDT_ENABLED, "0");
        } else {
            SystemProperties.set(PROPERTY_D2D_DYNAMIC_FREQ_UPDT_ENABLED, "1");
        }
        mAutoFreqCheck.setEnabled(true);
    }

    public void setD2dIfaceDev(String value){
        SystemProperties.set(PROPERTY_D2D_IFACE_DEV, value);
    }

    public String getD2dIfaceDev() {
        return SystemProperties.get(PROPERTY_D2D_IFACE_DEV, DEFAULT_D2D_IFACE_VALUE);
    }

    private void updateD2dIfaceDev() {
        final String iface_dev = getD2dIfaceDev();
        final String[] array = getResources().getStringArray(R.array.entries_d2d_config_iface_dev);
        final List<String> list = Arrays.asList(array);
        int index = list.indexOf(iface_dev);
        mIfaceSpinner.setSelection(index);
    }

    private void updateD2dBandwidth() {
        String value = SystemProperties.get(PROPERTY_D2D_DL_BANDWIDTH, "5");
        final String[] array = getResources().getStringArray(R.array.entryvalues_d2d_config_bandwidth);
        final List<String> list = Arrays.asList(array);
        int index = list.indexOf(value);
        mDlBandwidthSpinner.setSelection(index);

        value = SystemProperties.get(PROPERTY_D2D_UL_BANDWIDTH, "5");
        index = list.indexOf(value);
        mUlBandwidthSpinner.setSelection(index);
    }

    public void setD2dBandwidth(boolean isDownLink, String value) {
        Bundle args   = new Bundle();
        Message msg   = null;
        if (isDownLink) {
            msg = mMsgHandler.obtainMessage(EVENT_CONFIG_D2D_DL_BW);
            args.putInt(D2D_BW_CONFIG, Integer.parseInt(value));
            msg.setData(args);
        } else {
            msg = mMsgHandler.obtainMessage(EVENT_CONFIG_D2D_UL_BW);
            args.putInt(D2D_BW_CONFIG, Integer.parseInt(value));
            msg.setData(args);
        }
        mMsgHandler.sendMessage(msg);
    }

    private void showProgress(int messageResId) {
        findViewById(R.id.layout_progress).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.txt_progress)).setText(messageResId);
    }

    private void hideProgress() {
        findViewById(R.id.layout_progress).setVisibility(View.GONE);
    }

    private void requestFreqNegotiation() {
        Message msg = mMsgHandler.obtainMessage(EVENT_REQUEST_FREQUENCY_NEGOTIATION);
        mMsgHandler.sendMessageDelayed(msg, QUERY_STATUS_INTERVAL);
        showProgress(R.string.status_req_freq_negotiation);
    }
}
