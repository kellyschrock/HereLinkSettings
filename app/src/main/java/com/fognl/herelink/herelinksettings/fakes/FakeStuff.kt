package com.fognl.herelink.herelinksettings.fakes

import android.os.Bundle
import android.os.Message
import android.os.RemoteException
import android.util.Log
import com.fognl.herelink.herelinksettings.D2DInfoActivity

class ServiceState {
    companion object {
        const val STATE_IN_SERVICE = 1
    }

    fun getState(): Int = 0
}

class SignalStrength {
    val lteRsrq = 0
    val lteRsrp = 0
    val lteSignalStrength = 0

}

interface ID2DInfoListener {
    fun onD2DServiceStatusChanged(ss: ServiceState)
    fun onD2DSignalStrengthChanged(ss: SignalStrength)
    fun onD2DFrequencyListReceived()
    fun onD2DULSpeedChanged(ul_bw: Int, ul_bit_rate: Int)
    fun onD2DSnrGcsChanged(snr_master: Int, snr_slave: Int)
    fun onD2DSnrUavChanged(snr_master: Int, snr_slave: Int)
    fun onD2DAgcGcsChanged(agc_master: Int, agc_slave: Int)
    fun onD2DAgcUavChanged(agc_master: Int, agc_slave: Int)
    fun onD2DInterferenceListReceived()
    fun onD2DRadioPowerChanged(is_power_on: Boolean)

    fun onRequestFreqNegotiationDone(errorCode: Int)
    fun onRequestFreqResetDone(errorCode: Int)
    fun onRequestFreqHopCtrlDone(errorCode: Int)
    fun onRequestGetFreqHopStateDone(dl_hop_enabled: Int, ul_hop_enabled: Int, errorCode: Int)
    fun onRequestGetDlFrequencyPointDone(dl_frequency_point: Int, errorCode: Int)
    fun onRequestSendCtrlCmdDone(errorCode: Int)
    fun onRequestConfigBandwidthDone(errorCode: Int)

    interface Stub: ID2DInfoListener {
//        fun onD2DServiceStatusChanged(state: ServiceState)
//        fun onD2DSignalStrengthChanged(signal: SignalStrength)
////        fun onFrequencyListReceived()
//        fun onD2DULSpeedChanged(ul_bw: Int, ul_bit_rate: Int)
//
//        fun onD2DSnrGcsChanged(snr_master: Int, snr_slave: Int)
//        fun onD2DSnrUavChanged(snr_master: Int, snr_slave: Int)
//        fun onD2DAgcGcsChanged(agc_master: Int, agc_slave: Int)
//        fun onD2DAgcUavChanged(agc_master: Int, agc_slave: Int)
//        fun onRequestFreqNegotiationDone(errorCode: Int)
//        fun onRequestFreqResetDone(errorCode: Int) {}
//        fun onD2DInterferenceListReceived() {}
//        fun onRequestFreqHopCtrlDone(errorCode: Int)
//        fun onRequestGetFreqHopStateDone(dl_hop_enabled: Int, ul_hop_enabled: Int, errorCode: Int)
//        fun onD2DRadioPowerChanged(is_power_on: Boolean)
//        fun onRequestGetDlFrequencyPointDone(dl_frequency_point: Int, errorCode: Int)
//        fun onRequestSendCtrlCmdDone(errorCode: Int)
//        fun onRequestConfigBandwidthDone(errorCode: Int)
    }
}


interface ID2DService {
    @Throws(RemoteException::class)
    fun registerForD2dInfoChanged(listener: ID2DInfoListener)
    @Throws(RemoteException::class)
    fun unregisterCallback( listener: ID2DInfoListener)

    @Throws(RemoteException::class)
    fun requestGetD2dInfo()
    @Throws(RemoteException::class)
    fun requestD2dFreqNegotiation()
    @Throws(RemoteException::class)
    fun requestD2dFreqReset(freq_point: String)
    @Throws(RemoteException::class)
    fun requestGetD2dFreqHopState()
    @Throws(RemoteException::class)
    fun requestD2dFreqHopCtrl(dl_freq_hopping_enabled: String, ul_freq_hopping_enabled: String)
    @Throws(RemoteException::class)
    fun requestGetD2dDlFrequencyPoint()
    @Throws(RemoteException::class)
    fun requestSendD2DCtrlCmd(ctrl_cmd: String)
    @Throws(RemoteException::class)
    fun requestConfigD2dBandwidth(is_downlink: Boolean, bw_cfg: Int)
    @Throws(RemoteException::class)
    fun requestGetRadioPower(): Boolean

    fun onD2dInfoChanged(cmd_id: Int)
    fun onD2dRequestDone(cmd_id: Int, errorCode: Int)

    interface Stub: ID2DService {
        companion object {
            fun asInterface(service: Any): ID2DService? { return null }
        }

//        @Throws(RemoteException::class)
//        fun registerForD2dInfoChanged(listener: ID2DInfoListener)
    }
}
