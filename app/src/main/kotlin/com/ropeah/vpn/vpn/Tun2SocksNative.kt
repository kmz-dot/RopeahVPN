package com.ropeah.vpn.vpn

/**
 * JNI surface for libhev-socks5-tunnel. The native methods are registered against
 * `com/ropeah/vpn/vpn/Tun2SocksNative` from the C side (see Application.mk:
 * -DPKGNAME=com/ropeah/vpn/vpn -DCLSNAME=Tun2SocksNative). Don't rename this
 * class or move it to a different package without rebuilding the .so.
 */
internal object Tun2SocksNative {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic external fun TProxyStartService(configPath: String, tunFd: Int)
    @JvmStatic external fun TProxyStopService()
    @JvmStatic external fun TProxyGetStats(): LongArray
}
