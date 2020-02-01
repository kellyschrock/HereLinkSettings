package com.fognl.herelink.herelinksettings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fognl.herelink.herelinksettings.fakes.*
import com.fognl.herelink.herelinksettings.fakes.ID2DService.Stub.Companion.asInterface
import kotlinx.android.synthetic.main.activity_d2d_settings.*

class D2DSettingsActivity : AppCompatActivity() {
    companion object {
        private val TAG = D2DSettingsActivity::class.java.simpleName

        private const val QUERY_STATUS_INTERVAL = 100
        private const val INVALID = 0x7FFFFFFF

        private const val D2D_DEVICE_PLANE = "plane"
        private const val D2D_DEVICE_CONTROLLER = "controller"
        private const val DEFAULT_D2D_IFACE_VALUE = "lmi40"
        private const val D2D_DEFAULT_DL_FREQUENCY = "47400"

        private const val PROPERTY_D2D_DEVICE_NAME = "persist.sys.d2d.device"
        private const val PROPERTY_D2D_CURRENT_DL_FREQUENCY = "persist.sys.d2d.dl.frequency"
        private const val PROPERTY_D2D_CURRENT_COMMAND_ID = "persist.sys.d2d.cmd.id"
        private const val PROPERTY_D2D_CURRENT_COMMAND_PARAMETER = "persist.sys.d2d.cmd.parameter"
        private const val PROPERTY_D2D_UL_FREQ_HOP_ENABLED = "persist.sys.d2d.ul.freqhop"
        private const val PROPERTY_D2D_DL_FREQ_HOP_ENABLED = "persist.sys.d2d.dl.freqhop"
        private const val PROPERTY_D2D_IFACE_DEV = "persist.sys.d2d.iface.dev"
        private const val PROPERTY_D2D_DESIRE_RADIO_POWER = "persist.radio.sim1.power"
        private const val PROPERTY_D2D_DYNAMIC_FREQ_UPDT_ENABLED = "persist.sys.d2d.fupdt.enabled"
        private const val PROPERTY_D2D_UL_BANDWIDTH = "persist.sys.d2d.ul.bandwidth"
        private const val PROPERTY_D2D_DL_BANDWIDTH = "persist.sys.d2d.dl.bandwidth"

        private const val EVENT_GET_CURRENT_STATUS_REQUEST = 1
        private const val EVENT_GET_RADIO_POWER = 2
        private const val EVENT_SEND_D2D_CTRL_CMD = 3
        private const val EVENT_SEND_D2D_CTRL_CMD_DONE = 4
        private const val EVENT_REQUEST_FREQUENCY_NEGOTIATION = 5
        private const val EVENT_REQUEST_FREQUENCY_NEGOTIATION_DONE = 6
        private const val EVENT_QUERY_DL_FREQUENCY_POINT = 7
        private const val EVENT_FREQUENCY_HOPPING_CTRL = 8
        private const val EVENT_FREQUENCY_HOPPING_CTRL_DONE = 9
        private const val EVENT_QUERY_FREQUENCY_HOPPING_STATE = 10
        private const val EVENT_CONFIG_D2D_DL_BW = 11
        private const val EVENT_CONFIG_D2D_UL_BW = 12
        private const val EVENT_CONFIG_D2D_BW_DONE = 13

        private const val D2D_INFO_UL_HOPPING_CTRL = "UL_HOP"
        private const val D2D_INFO_DL_HOPPING_CTRL = "DL_HOP"
        private const val D2D_CTRL_CMD = "CTRL_CMD"
        private const val D2D_BW_CONFIG = "BW_CFG"
        private const val D2D_CMD_RESULT_CODE = "ERR_CODE"
    }

    private var mDlFrequencyNum: String? = null
    private var mD2DLinkConnected = false
    private var mBound = false
    private var mIsRadioPowerOn = true
    private var mIsUlFreqHoppingEnabled = false
    private var mIsDlFreqHoppingEnabled = false
    private var mPlaneUlGrantBW = 0
    private var mPlaneUlDataRate = 0
    private var mSnrGcsMaster = -40
    private var mSnrGcsSlave = -40
    private var mSnrUavMaster = -40
    private var mSnrUavSlave = -40
    private var mAgcGcsMaster = 100
    private var mAgcGcsSlave = 100
    private var mAgcUavMaster = 100
    private var mAgcUavSlave = 100
    private var mControllerMasterRsrp = INVALID
    private var mControllerSlaveRsrp = INVALID
    private var mPlaneMasterRsrp = INVALID
    private var mPlaneSlaveRsrp = INVALID

    private var mD2DService: ID2DService? = null

    private val mMsgHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            var errorCode = 0
            Log.v(TAG, "mMsgHandler handleMessage msg.what = " + msg.what)
            
            when (msg.what) {
                EVENT_GET_CURRENT_STATUS_REQUEST -> if (!mBound) {
                    Log.v(TAG, "service has not bound yet, retry")
                    removeMessages(EVENT_GET_CURRENT_STATUS_REQUEST)
                    sendMessageDelayed(obtainMessage(EVENT_GET_CURRENT_STATUS_REQUEST), QUERY_STATUS_INTERVAL.toLong())
                } else {
                    try {
                        mD2DService?.requestGetD2dInfo()
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
                EVENT_REQUEST_FREQUENCY_NEGOTIATION -> if (!mBound) {
                    Log.v(TAG, "service has not bound yet, retry")
                    removeMessages(EVENT_REQUEST_FREQUENCY_NEGOTIATION)
                    sendMessageDelayed(obtainMessage(EVENT_REQUEST_FREQUENCY_NEGOTIATION), QUERY_STATUS_INTERVAL.toLong())
                    hideProgress()
                } else {
                    try {
                        mD2DService?.requestD2dFreqNegotiation()
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
                EVENT_REQUEST_FREQUENCY_NEGOTIATION_DONE -> {
                    hideProgress()
                    errorCode = msg.data.getInt(D2D_CMD_RESULT_CODE)
                    if (errorCode != 0) {
                        toast(getString(R.string.d2d_freq_negotiation_fail))
                    }
                }
                EVENT_GET_RADIO_POWER -> if (!mBound) {
                    Log.v(TAG, "service has not bound yet, retry")
                    removeMessages(EVENT_GET_RADIO_POWER)
                    sendMessageDelayed(obtainMessage(EVENT_GET_RADIO_POWER), QUERY_STATUS_INTERVAL.toLong())
                } else {
                    try {
                        mIsRadioPowerOn = mD2DService?.requestGetRadioPower() ?: false
                        post(updateRadioPower)
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
                EVENT_SEND_D2D_CTRL_CMD -> if (!mBound) {
                    Log.v(TAG, "service has not bound yet, retry")
                    removeMessages(EVENT_SEND_D2D_CTRL_CMD)
                    sendMessageDelayed(obtainMessage(EVENT_SEND_D2D_CTRL_CMD), QUERY_STATUS_INTERVAL.toLong())
                } else {
                    try {
                        msg.data.getString(D2D_CTRL_CMD)?.let {
                            mD2DService?.requestSendD2DCtrlCmd(it)
                        }
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
                EVENT_SEND_D2D_CTRL_CMD_DONE -> {
                    errorCode = msg.data.getInt(D2D_CMD_RESULT_CODE)
                    if (0 == errorCode) {
                        toast(getString(R.string.str_prompt_reboot_device))
                    } else {
                        toast(getString(R.string.d2d_config_command_err))
                    }
                }
                EVENT_QUERY_DL_FREQUENCY_POINT -> if (!mBound) {
                    Log.v(TAG, "service has not bound yet, retry")
                    removeMessages(EVENT_QUERY_DL_FREQUENCY_POINT)
                    sendMessageDelayed(obtainMessage(EVENT_QUERY_DL_FREQUENCY_POINT), QUERY_STATUS_INTERVAL.toLong())
                } else {
                    try {
                        mD2DService?.requestGetD2dDlFrequencyPoint()
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
                EVENT_FREQUENCY_HOPPING_CTRL -> {
                    removeMessages(EVENT_FREQUENCY_HOPPING_CTRL)
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry")
                    } else {
                        try {
                            mD2DService?.requestD2dFreqHopCtrl(
                                msg.data.getString(D2D_INFO_DL_HOPPING_CTRL)!!,
                                msg.data.getString(D2D_INFO_UL_HOPPING_CTRL)!!
                            )
                        } catch (e: RemoteException) {
                            e.printStackTrace()
                        }
                    }
                }
                EVENT_FREQUENCY_HOPPING_CTRL_DONE -> {
                    hideProgress()
                    errorCode = msg.data.getInt(D2D_CMD_RESULT_CODE)
                    if (0 == errorCode) {
                        toast(getString(R.string.d2d_freq_hopping_ctrl_success))
                    } else {
                        toast(getString(R.string.d2d_freq_hopping_ctrl_fail))
                    }
                    // query frequency hopping state to ensure the setting request take effect
                    try {
                        mD2DService?.requestGetD2dFreqHopState()
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
                EVENT_QUERY_FREQUENCY_HOPPING_STATE -> if (!mBound) {
                    Log.v(TAG, "service has not bound yet, retry")
                    removeMessages(EVENT_QUERY_FREQUENCY_HOPPING_STATE)
                    sendMessageDelayed(obtainMessage(EVENT_QUERY_FREQUENCY_HOPPING_STATE), QUERY_STATUS_INTERVAL.toLong())
                } else {
                    try {
                        mD2DService?.requestGetD2dFreqHopState()
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
                EVENT_CONFIG_D2D_DL_BW -> {
                    removeMessages(EVENT_CONFIG_D2D_DL_BW)
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry")
                    } else {
                        try {
                            mD2DService?.requestConfigD2dBandwidth(true, msg.data.getInt(D2D_BW_CONFIG))
                        } catch (e: RemoteException) {
                            e.printStackTrace()
                        }
                    }
                }
                EVENT_CONFIG_D2D_UL_BW -> {
                    removeMessages(EVENT_CONFIG_D2D_UL_BW)
                    if (!mBound) {
                        Log.v(TAG, "service has not bound yet, retry")
                    } else {
                        try {
                            mD2DService?.requestConfigD2dBandwidth(false, msg.data.getInt(D2D_BW_CONFIG))
                        } catch (e: RemoteException) {
                            e.printStackTrace()
                        }
                    }
                }
                EVENT_CONFIG_D2D_BW_DONE -> {
                    errorCode = msg.data.getInt(D2D_CMD_RESULT_CODE)
                    if (0 == errorCode) {
                        toast(getString(R.string.d2d_bw_config_success))
                    } else {
                        toast(getString(R.string.d2d_bw_config_fail))
                    }
                    updateD2dBandwidth()
                }
                else -> Log.v(
                    TAG,
                    "mMsgHandler handleMessage invalid msg: " + msg.what
                )
            }
        }
    }    

    private val mD2DInfoListener: ID2DInfoListener =
        object : ID2DInfoListener.Stub {
            override fun onD2DServiceStatusChanged(ss: ServiceState) {
                Log.v(
                    TAG,
                    "onD2DServiceStatusChanged: service state = $ss"
                )
                val state = ss.getState()
                if (ServiceState.STATE_IN_SERVICE == state) {
                    mD2DLinkConnected = true
                } else {
                    mD2DLinkConnected = false
                    // reset D2D info when D2D link disconnected
                    mMsgHandler.post(resetD2DInfo)
                }

                mMsgHandler.post(updateServiceState)
            }

            override fun onD2DSignalStrengthChanged(ss: SignalStrength) {
                Log.v(TAG, "onD2DSignalStrengthChanged: signal strength = $ss")

                if (ss.lteRsrq == INVALID) {
                    Log.v(TAG, "onD2DSignalStrengthChanged: vehicle")
                    if (ss.lteRsrp != INVALID) {
                        mPlaneMasterRsrp = ss.lteRsrp
                    }

                    if (ss.lteSignalStrength != 99) {
                        mPlaneSlaveRsrp = -1 * ss.lteSignalStrength
                    }
                } else {
                    Log.v(TAG, "onD2DSignalStrengthChanged: controller")

                    if (ss.lteRsrp != INVALID) {
                        mControllerMasterRsrp = ss.lteRsrp
                    }

                    if (ss.lteSignalStrength != 99) {
                        mControllerSlaveRsrp = -1 * ss.lteSignalStrength
                    }
                }
                mMsgHandler.post(updateSignalStrength)
            }

            override fun onD2DFrequencyListReceived() {
                Log.v(TAG, "onD2DFrequencyListReceived")
            }

            override fun onD2DULSpeedChanged(ul_bw: Int, ul_bit_rate: Int) {
                Log.v(
                    TAG,
                    "onD2DULSpeedChanged: ul_bw = $ul_bw, ul_bit_rate = $ul_bit_rate"
                )
                mPlaneUlGrantBW = ul_bw
                mPlaneUlDataRate = ul_bit_rate
                mMsgHandler.post(updateQosInfo)
            }

            override fun onD2DSnrGcsChanged(snr_master: Int, snr_slave: Int) {
                Log.v(
                    TAG,
                    "onD2DSnrGcsChanged: snr_master = $snr_master snr_slave = $snr_slave"
                )
                mSnrGcsMaster = snr_master
                mSnrGcsSlave = snr_slave
                mMsgHandler.post(updateQosInfo)
            }

            override fun onD2DSnrUavChanged(snr_master: Int, snr_slave: Int) {
                Log.v(
                    TAG,
                    "onD2DSnrUavChanged: snr_master = $snr_master snr_slave = $snr_slave"
                )
                mSnrUavMaster = snr_master
                mSnrUavSlave = snr_slave
                mMsgHandler.post(updateQosInfo)
            }

            override fun onD2DAgcGcsChanged(agc_master: Int, agc_slave: Int) {
                Log.v(
                    TAG,
                    "onD2DAgcGcsChanged: agc_master = $agc_master agc_slave = $agc_slave"
                )
                mAgcGcsMaster = agc_master
                mAgcGcsSlave = agc_slave
                mMsgHandler.post(updateQosInfo)
            }

            override fun onD2DAgcUavChanged(agc_master: Int, agc_slave: Int) {
                Log.v(
                    TAG,
                    "onD2DAgcUavChanged: agc_master = $agc_master agc_slave = $agc_slave"
                )
                mAgcUavMaster = agc_master
                mAgcUavSlave = agc_slave
                mMsgHandler.post(updateQosInfo)
            }

            override fun onRequestFreqNegotiationDone(errorCode: Int) {
                val args = Bundle()
                val msg: Message =
                    mMsgHandler.obtainMessage(EVENT_REQUEST_FREQUENCY_NEGOTIATION_DONE)
                Log.v(
                    TAG,
                    "onRequestFreqNegotiationDone: errorCode = $errorCode"
                )
                args.putInt(D2D_CMD_RESULT_CODE, errorCode)
                msg.data = args
                mMsgHandler.sendMessage(msg)
            }

            override fun onRequestFreqResetDone(errorCode: Int) {}
            override fun onD2DInterferenceListReceived() {}
            override fun onRequestFreqHopCtrlDone(errorCode: Int) {
                val args = Bundle()
                val msg: Message =
                    mMsgHandler.obtainMessage(EVENT_FREQUENCY_HOPPING_CTRL_DONE)
                Log.v(
                    TAG,
                    "onRequestFreqHopCtrlDone: errorCode = $errorCode"
                )
                args.putInt(D2D_CMD_RESULT_CODE, errorCode)
                msg.data = args
                mMsgHandler.sendMessage(msg)
            }

            override fun onRequestGetFreqHopStateDone(
                dl_hop_enabled: Int,
                ul_hop_enabled: Int,
                errorCode: Int
            ) {
                Log.v(
                    TAG,
                    "onRequestGetFreqHopStateDone: dl_hop_enabled = " + dl_hop_enabled + ", ul_hop_enabled = " +
                            ul_hop_enabled + ", errorCode = " + errorCode
                )
                if (0 == errorCode) {
                    mIsDlFreqHoppingEnabled = if (1 == dl_hop_enabled) true else false
                    mIsUlFreqHoppingEnabled = if (1 == ul_hop_enabled) true else false
                    mMsgHandler.post(updateFreqHopState)
                }
            }

            override fun onD2DRadioPowerChanged(is_power_on: Boolean) {
                Log.v(
                    TAG,
                    "onD2DRadioPowerChanged: is_power_on = $is_power_on"
                )
                mIsRadioPowerOn = is_power_on
                mMsgHandler.post(updateRadioPower)
            }

            override fun onRequestGetDlFrequencyPointDone(
                dl_frequency_point: Int,
                errorCode: Int
            ) {
                Log.v(
                    TAG,
                    "onRequestGetDlFrequencyPointDone: dl_frequency_point = " + dl_frequency_point +
                            ", errorCode = " + errorCode
                )
                if (0 == errorCode) {
                    mDlFrequencyNum = dl_frequency_point.toString()
                    mMsgHandler.post(updateDlFreq)
                }
            }

            override fun onRequestSendCtrlCmdDone(errorCode: Int) {
                val args = Bundle()
                val msg: Message =
                    mMsgHandler.obtainMessage(EVENT_SEND_D2D_CTRL_CMD_DONE)
                Log.v(
                    TAG,
                    "onRequestSendCtrlCmdDone: errorCode = $errorCode"
                )
                args.putInt(D2D_CMD_RESULT_CODE, errorCode)
                msg.data = args
                mMsgHandler.sendMessage(msg)
            }

            override fun onRequestConfigBandwidthDone(errorCode: Int) {
                val args = Bundle()
                val msg: Message =
                    mMsgHandler.obtainMessage(EVENT_CONFIG_D2D_BW_DONE)
                Log.v(
                    TAG,
                    "onRequestConfigBandwidthDone: errorCode = $errorCode"
                )
                args.putInt(D2D_CMD_RESULT_CODE, errorCode)
                msg.data = args
                mMsgHandler.sendMessage(msg)
            }
        }


    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            Log.v(TAG, "D2DService connected")
            mD2DService = asInterface(service)
            try {
                mD2DService?.registerForD2dInfoChanged(mD2DInfoListener)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            mBound = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.v(TAG, "D2DService disconnected")
            mD2DService = null
            mBound = false
        }
    }

    private val updateSignalStrength = Runnable {
        if (mPlaneMasterRsrp != INVALID && mPlaneSlaveRsrp != INVALID) {
            txt_vehicle_signal_strength.setText(
                getString(R.string.str_master_rsrp) + mPlaneMasterRsrp.toString() + " dBm; " +
                        getString(R.string.str_slave_rsrp) + mPlaneSlaveRsrp.toString() + " dBm"
            )
        } else if (mPlaneMasterRsrp != INVALID) {
            txt_vehicle_signal_strength.setText(
                getString(R.string.str_master_rsrp) + mPlaneMasterRsrp.toString() + " dBm; " +
                        getString(R.string.str_slave_rsrp) + getString(R.string.str_signal_strength_unknown)
            )
        } else {
            txt_vehicle_signal_strength.setText(getString(R.string.str_signal_strength_unknown))
        }

        if (mControllerMasterRsrp != INVALID && mControllerSlaveRsrp != INVALID) {
            txt_controller_signal_strength.setText(
                getString(R.string.str_master_rsrp) + mControllerMasterRsrp.toString() + " dBm; " +
                        getString(R.string.str_slave_rsrp) + mControllerSlaveRsrp.toString() + " dBm"
            )
        } else if (mControllerMasterRsrp != INVALID) {
            txt_controller_signal_strength.setText(
                getString(R.string.str_master_rsrp) + mControllerMasterRsrp.toString() + " dBm; " +
                        getString(R.string.str_slave_rsrp) + getString(R.string.str_signal_strength_unknown)
            )
        } else {
            txt_controller_signal_strength.setText(getString(R.string.str_signal_strength_unknown))
        }
    }

    private val updateQosInfo = Runnable {
        val qos_value = getString(R.string.str_plane_qos_value)

        txt_vehicle_qos.setText(String.format(qos_value, mPlaneUlGrantBW, mPlaneUlDataRate,
                mSnrGcsMaster, mSnrGcsSlave, mSnrUavMaster, mSnrUavSlave, mAgcGcsMaster, mAgcGcsSlave, mAgcUavMaster, mAgcUavSlave));
    }


    private val updateServiceState = Runnable {
        txt_vehicle_signal_state.setText(if(mD2DLinkConnected)
            R.string.str_d2d_connected
        else
            R.string.str_d2d_disconnected)
    }

    private val updateFreqHopState = Runnable {
        chk_uplink_freq_hopping.isChecked = mIsUlFreqHoppingEnabled
        chk_downlink_freq_hopping.isChecked = mIsDlFreqHoppingEnabled
    }

    private val updateRadioPower = Runnable {
        chk_radio_power.isChecked = (!mIsRadioPowerOn)
    }

    private val updateDlFreq = Runnable {
        txt_downlink_freq_point.setText(mDlFrequencyNum.toString())
        mDlFrequencyNum?.let {
            SystemProperties.set(PROPERTY_D2D_CURRENT_DL_FREQUENCY, it)
        }
    }

    private val resetD2DInfo = Runnable {
        mPlaneUlGrantBW = 0
        mPlaneUlDataRate = 0
        mSnrGcsMaster = -40
        mSnrGcsSlave = -40
        mSnrUavMaster = -40
        mSnrUavSlave = -40
        mAgcGcsMaster = 100
        mAgcGcsSlave = 100
        mAgcUavMaster = 100
        mAgcUavSlave = 100
        mPlaneMasterRsrp = INVALID
        mPlaneSlaveRsrp = INVALID
        txt_vehicle_qos.setText(getString(R.string.str_plane_qos_unknown))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_d2d_settings)
        init()
        hideProgress()
    }

    override fun onPause() {
        super.onPause()
        unbindToD2DService()
    }

    override fun onResume() {
        super.onResume()

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        bindToD2DService()
        updateD2dIfaceDev()
        updateD2dBandwidth()
        updateFreqDynamicUpdtCheckBox()
        /* refresh D2D info */ // query D2D link state and signal strength
        var msg = mMsgHandler.obtainMessage(EVENT_GET_CURRENT_STATUS_REQUEST)
        mMsgHandler.sendMessage(msg)
        // query D2D downlink frequency point
        msg = mMsgHandler.obtainMessage(EVENT_QUERY_DL_FREQUENCY_POINT)
        mMsgHandler.sendMessage(msg)
        // query D2D radio power state
        msg = mMsgHandler.obtainMessage(EVENT_GET_RADIO_POWER)
        mMsgHandler.sendMessage(msg)
        // query D2D frequency hopping state
        msg = mMsgHandler.obtainMessage(EVENT_QUERY_FREQUENCY_HOPPING_STATE)
        mMsgHandler.sendMessage(msg)
    }

    override fun onStop() {
        super.onStop()
        hideProgress()
    }

    private fun init() {
        btn_save.setOnClickListener { onSaveClick() }
        btn_request_negotiation.setOnClickListener { requestFreqNegotiation() }
        chk_radio_power.setOnCheckedChangeListener { _, checked -> onRadioPowerChecked(checked) }
        chk_uplink_freq_hopping.setOnCheckedChangeListener { _, checked -> onFreqHoppingChecked(true, checked) }
        chk_downlink_freq_hopping.setOnCheckedChangeListener { _, checked -> onFreqHoppingChecked(false, checked) }
        chk_auto_freq_select.setOnCheckedChangeListener { _, checked -> onAutoFreqSelectChecked(checked) }

        spin_ul_bandwidth.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, itemId: Long) {
                val array = resources.getStringArray(R.array.entryvalues_d2d_config_bandwidth)
                setD2dBandwidth(false, array[position])
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        spin_dl_bandwidth.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, itemId: Long) {
                val array = resources.getStringArray(R.array.entryvalues_d2d_config_bandwidth)
                setD2dBandwidth(true, array[position])
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        spin_d2d_iface_dev_setting.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, itemId: Long) {
                val array = resources.getStringArray(R.array.entryvalues_d2d_config_iface_dev)
                setD2dIfaceDev(position, array[position])
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setD2dIfaceDev(idx: Int, value: String) {
        Log.d(TAG, "setD2dIfaceDev: idx = $idx")
        SystemProperties.set(PROPERTY_D2D_IFACE_DEV, value)
    }

    private fun updateD2dBandwidth() {
        var value = SystemProperties.get(PROPERTY_D2D_DL_BANDWIDTH, "5")
//        var index: Int = mD2dDlBandwidth.findIndexOfValue(value)
//        var summary: CharSequence = mD2dDlBandwidth.getEntries().get(index)
//        mD2dDlBandwidth.setSummary(summary)
//        mD2dDlBandwidth.setValueIndex(index)

        value = SystemProperties.get(PROPERTY_D2D_UL_BANDWIDTH, "5")
//        index = mD2dUlBandwidth.findIndexOfValue(value)
//        summary = mD2dUlBandwidth.getEntries().get(index)
//        mD2dUlBandwidth.setSummary(summary)
//        mD2dUlBandwidth.setValueIndex(index)
    }

    private fun hideProgress() {
        // dismiss progress
        layout_progress.visibility = View.GONE
    }

    private fun showProgress(titleId: Int, msgId: Int) {
        // progress
        txt_progress.text = getString(msgId)
        layout_progress.visibility = View.VISIBLE
    }

    fun setD2dBandwidth(isDownLink: Boolean, value: String) {
        val args = Bundle()
        var msg: Message? = null
        if (isDownLink) {
            msg = mMsgHandler.obtainMessage(EVENT_CONFIG_D2D_DL_BW)
            args.putInt(D2D_BW_CONFIG, value.toInt())
            msg.data = args
        } else {
            msg = mMsgHandler.obtainMessage(EVENT_CONFIG_D2D_UL_BW)
            args.putInt(D2D_BW_CONFIG, value.toInt())
            msg.data = args
        }
        mMsgHandler.sendMessage(msg)
    }

    private fun toast(str: String) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show()
    }

    private fun onRadioPowerChecked(isChecked: Boolean) {
        Log.v(TAG, "clickD2dRadioPower: isChecked = $isChecked, mIsRadioPowerOn = $mIsRadioPowerOn")

        chk_radio_power.isEnabled = false

        if (!isChecked && !mIsRadioPowerOn) {
            setD2dRadioPower(true)
        } else if (isChecked && mIsRadioPowerOn) {
            setD2dRadioPower(false)
        } else {
            Log.e(TAG, "clickD2dRadioPower invalid state")
            chk_radio_power.isChecked = (!isChecked)
        }

        chk_radio_power.setEnabled(true)
    }

    private fun setD2dRadioPower(isPowerOn: Boolean) {
        Settings.Global.putInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (isPowerOn) 0 else 1)
        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING)
        intent.putExtra("state", !isPowerOn)
        // TODO: ??? sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private fun onFreqHoppingChecked(isUplink: Boolean, isChecked: Boolean) {
        if (isUplink) {
            Log.v(TAG, "clickD2dFreqHoppingCtrl: Uplink ctrl, isChecked = " + isChecked + ", mIsUlFreqHoppingEnabled = " + mIsUlFreqHoppingEnabled)
            chk_uplink_freq_hopping.setEnabled(false)
            if (!isChecked && mIsUlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, false)
            } else if (isChecked && !mIsUlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, true)
            } else {
                Log.e(TAG, "clickD2dFreqHoppingCtrl: Uplink ctrl, invalid state")
                chk_downlink_freq_hopping.setChecked(!isChecked)
            }
            chk_uplink_freq_hopping.setEnabled(true)
        } else {
            Log.v(TAG, "clickD2dFreqHoppingCtrl: Downlink ctrl, isChecked = " + isChecked + ", mIsDlFreqHoppingEnabled = " + mIsDlFreqHoppingEnabled)
            chk_downlink_freq_hopping.setEnabled(false)
            if (!isChecked && mIsDlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, false)
            } else if (isChecked && !mIsDlFreqHoppingEnabled) {
                setD2dFreqHopping(isUplink, true)
            } else {
                Log.e(TAG, "clickD2dFreqHoppingCtrl: Downlink ctrl, invalid state");
                chk_downlink_freq_hopping.setChecked(!isChecked)
            }
            chk_downlink_freq_hopping.setEnabled(true)
        }
    }

    private fun setD2dFreqHopping(isUplink: Boolean, isEnabled: Boolean) {
        val args = Bundle()
        val msg =
            mMsgHandler.obtainMessage(EVENT_FREQUENCY_HOPPING_CTRL)
        if (isUplink) {
            args.putString(D2D_INFO_DL_HOPPING_CTRL, if (mIsDlFreqHoppingEnabled) "1" else "0")
            args.putString(D2D_INFO_UL_HOPPING_CTRL, if (isEnabled) "1" else "0")
            msg.data = args
            mMsgHandler.sendMessageDelayed(msg, QUERY_STATUS_INTERVAL.toLong())
            SystemProperties.set(PROPERTY_D2D_UL_FREQ_HOP_ENABLED, if (isEnabled) "1" else "0")
        } else {
            args.putString(D2D_INFO_DL_HOPPING_CTRL, if (isEnabled) "1" else "0")
            args.putString(D2D_INFO_UL_HOPPING_CTRL, if (mIsUlFreqHoppingEnabled) "1" else "0")
            msg.data = args
            mMsgHandler.sendMessageDelayed(msg, QUERY_STATUS_INTERVAL.toLong())
            SystemProperties.set(PROPERTY_D2D_DL_FREQ_HOP_ENABLED, if (isEnabled) "1" else "0")
        }
        showProgress(R.string.str_frequency_hopping, R.string.str_please_waiting)
    }

    private fun onAutoFreqSelectChecked(isChecked: Boolean) {
        Log.v(TAG, "clickD2dFreqDynamicUpdtCtrl: isChecked = $isChecked")
        chk_auto_freq_select.setEnabled(false)
        val value = if(isChecked) "1" else "0"
        SystemProperties.set(PROPERTY_D2D_DYNAMIC_FREQ_UPDT_ENABLED, value)
        chk_auto_freq_select.setEnabled(true)
    }

    private fun onSaveClick() {
        val args = Bundle()
        val msg = mMsgHandler.obtainMessage(EVENT_SEND_D2D_CTRL_CMD)
        var d2d_config_cmd: String? = null
        val cmd_id = edit_command_type.getText().toString().trim({ it <= ' ' })
        val cmd_parameter = edit_command_value.getText().toString().trim({ it <= ' ' })
        if (cmd_id == null || cmd_id == "0") {
            toast(getString(R.string.d2d_config_command_id_err))
            return
        } else if (cmd_parameter == null || cmd_parameter == "0") {
            toast(getString(R.string.d2d_config_command_parameter_err))
            return
        }

        d2d_config_cmd = "$cmd_id,$cmd_parameter"
        SystemProperties.set(PROPERTY_D2D_CURRENT_COMMAND_ID, cmd_id)
        SystemProperties.set(PROPERTY_D2D_CURRENT_COMMAND_PARAMETER, cmd_parameter)
        args.putString(D2D_CTRL_CMD, d2d_config_cmd)
        msg.data = args
        mMsgHandler.sendMessage(msg)
    }

    private fun updateD2dIfaceDev() {
        Log.v(TAG, "updateD2dIfaceDev")
        getD2dIfaceDev()?.let { value ->
            val array = resources.getStringArray(R.array.entryvalues_d2d_config_iface_dev)
            val index = array.indexOf(value)
            spin_d2d_iface_dev_setting.setSelection(index)
        }
    }

    fun getD2dIfaceDev(): String? {
        return SystemProperties.get(PROPERTY_D2D_IFACE_DEV, DEFAULT_D2D_IFACE_VALUE)
    }

    fun bindToD2DService() {
        val toService = Intent()
        toService.setClassName("com.pinecone.telephony", "com.pinecone.telephony.D2DService")
        this.bindService(toService, mConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindToD2DService() {
        Log.v(TAG, "unbindToD2DService")
        mD2DService?.apply {
            try {
                unregisterCallback(mD2DInfoListener)
            } catch(e: RemoteException) { e.printStackTrace() }
        }

        mMsgHandler.removeCallbacksAndMessages(null)
        unbindService(mConnection)
        mD2DService = null
        mBound = false
    }

    private fun updateFreqDynamicUpdtCheckBox() {
        chk_auto_freq_select.isChecked =
            SystemProperties.get(PROPERTY_D2D_DYNAMIC_FREQ_UPDT_ENABLED, "0") == "1"
    }

    private fun requestFreqNegotiation() {
        val msg = mMsgHandler.obtainMessage(EVENT_REQUEST_FREQUENCY_NEGOTIATION)
        mMsgHandler.sendMessageDelayed(msg, QUERY_STATUS_INTERVAL.toLong())
        showProgress(R.string.str_frequency_negotiation, R.string.str_please_waiting)
    }
}
