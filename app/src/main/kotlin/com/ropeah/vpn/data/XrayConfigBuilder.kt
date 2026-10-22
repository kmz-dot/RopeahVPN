package com.ropeah.vpn.data

import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a minimal Xray JSON config that runs a local SOCKS5 inbound at
 * 127.0.0.1:[socksPort] and forwards to one outbound configured from [server].
 *
 * The SOCKS5 inbound is what tun2socks will dial into. The config intentionally
 * stays minimal (no routing rules beyond direct/block fallbacks, no DNS server)
 * to keep the surface small and easy to audit.
 */
object XrayConfigBuilder {

    fun build(server: ServerConfig, socksPort: Int, logLevel: String = "debug"): JsonObject = buildJsonObject {
        put("log", buildJsonObject { put("loglevel", logLevel) })
        put("stats", buildJsonObject {})
        put("policy", buildJsonObject {
            put("system", buildJsonObject {
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        })
        put("inbounds", buildJsonArray { add(socksInbound(socksPort)) })
        put("outbounds", buildJsonArray {
            add(proxyOutbound(server))
            add(buildJsonObject {
                put("tag", "direct"); put("protocol", "freedom")
            })
            add(buildJsonObject {
                put("tag", "block"); put("protocol", "blackhole")
            })
        })
    }

    private fun socksInbound(port: Int): JsonObject = buildJsonObject {
        put("tag", "socks-in")
        put("listen", "127.0.0.1")
        put("port", port)
        put("protocol", "socks")
        put("settings", buildJsonObject {
            put("auth", "noauth")
            put("udp", true)
        })
        put("sniffing", buildJsonObject {
            put("enabled", true)
            put("destOverride", buildJsonArray {
                add(JsonPrimitive("http"))
                add(JsonPrimitive("tls"))
            })
        })
    }

    private fun proxyOutbound(s: ServerConfig): JsonObject = buildJsonObject {
        put("tag", "proxy")
        when (s.protocol) {
            Protocol.VMess -> {
                put("protocol", "vmess")
                put("settings", buildJsonObject {
                    put("vnext", buildJsonArray {
                        add(buildJsonObject {
                            put("address", s.address)
                            put("port", s.port)
                            put("users", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", s.uuid ?: "")
                                    put("security", "auto")
                                })
                            })
                        })
                    })
                })
            }
            Protocol.VLESS -> {
                put("protocol", "vless")
                put("settings", buildJsonObject {
                    put("vnext", buildJsonArray {
                        add(buildJsonObject {
                            put("address", s.address)
                            put("port", s.port)
                            put("users", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", s.uuid ?: "")
                                    put("encryption", "none")
                                })
                            })
                        })
                    })
                })
            }
            Protocol.Trojan -> {
                put("protocol", "trojan")
                put("settings", buildJsonObject {
                    put("servers", buildJsonArray {
                        add(buildJsonObject {
                            put("address", s.address)
                            put("port", s.port)
                            put("password", s.password ?: "")
                        })
                    })
                })
            }
            Protocol.Shadowsocks -> {
                put("protocol", "shadowsocks")
                put("settings", buildJsonObject {
                    put("servers", buildJsonArray {
                        add(buildJsonObject {
                            put("address", s.address)
                            put("port", s.port)
                            put("method", s.method ?: "aes-256-gcm")
                            put("password", s.password ?: "")
                        })
                    })
                })
            }
        }
        put("streamSettings", streamSettings(s))
    }

    private fun streamSettings(s: ServerConfig): JsonObject = buildJsonObject {
        val network = s.network?.takeIf { it.isNotBlank() } ?: "tcp"
        put("network", network)
        val sec = when {
            s.protocol == Protocol.Trojan -> "tls"
            s.security == "tls" || s.security == "reality" -> s.security!!
            else -> "none"
        }
        put("security", sec)
        if (sec == "tls") {
            put("tlsSettings", buildJsonObject {
                s.sni?.takeIf { it.isNotBlank() }?.let { put("serverName", it) }
                put("allowInsecure", false)
            })
        }
        if (network == "ws") {
            put("wsSettings", buildJsonObject {
                s.path?.takeIf { it.isNotBlank() }?.let { put("path", decodePercentOnce(it)) }
                s.host?.takeIf { it.isNotBlank() }?.let { host ->
                    put("headers", buildJsonObject { put("Host", host) })
                }
            })
        }
    }

    /**
     * The backend's structured server objects can carry an encoded WS path
     * (`%2Fvless-ntls`), while Xray takes `wsSettings.path` literally and
     * dials `/%2Fvless-ntls`. Decode percent escapes once, matching what
     * [ConfigParser] produces for the same share URI. Invalid escapes are
     * left untouched.
     */
    private fun decodePercentOnce(value: String): String {
        if ('%' !in value) return value
        val out = ByteArrayOutputStream(value.length)
        var plainStart = 0
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                val hi = Character.digit(value[i + 1], 16)
                val lo = Character.digit(value[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    if (plainStart < i) {
                        out.write(value.substring(plainStart, i).toByteArray(Charsets.UTF_8))
                    }
                    out.write((hi shl 4) or lo)
                    i += 3
                    plainStart = i
                    continue
                }
            }
            i++
        }
        if (plainStart < value.length) {
            out.write(value.substring(plainStart).toByteArray(Charsets.UTF_8))
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
