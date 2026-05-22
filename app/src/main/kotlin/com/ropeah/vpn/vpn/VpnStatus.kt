package com.ropeah.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnStatus { Disconnected, Connecting, Connected }

object VpnStateBus {
    private val _status = MutableStateFlow(VpnStatus.Disconnected)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    private val _traffic = MutableStateFlow<TrafficSample?>(null)
    val traffic: StateFlow<TrafficSample?> = _traffic.asStateFlow()

    internal fun set(value: VpnStatus) {
        _status.value = value
        when (value) {
            VpnStatus.Connected ->
                if (_connectedSince.value == null) _connectedSince.value = System.currentTimeMillis()
            VpnStatus.Disconnected -> {
                _connectedSince.value = null
                _traffic.value = null
            }
            else -> Unit
        }
    }

    internal fun publishTraffic(sample: TrafficSample) {
        _traffic.value = sample
    }
}
