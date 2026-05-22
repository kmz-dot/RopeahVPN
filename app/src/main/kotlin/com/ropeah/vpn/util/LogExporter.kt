package com.ropeah.vpn.util

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    fun capture(context: Context): File {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "ropeah-$ts.log")
        val pid = Process.myPid().toString()
        val cmd = arrayOf(
            "logcat", "-d", "-v", "time",
            "--pid", pid,
            "RopeahVpnService:V",
            "XrayController:V",
            "XrayCallback:V",
            "Tun2SocksBridge:V",
            "GoLog:V",
            "Go:V",
            "AndroidRuntime:E",
            "System.err:V",
            "*:S",
        )
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        out.outputStream().use { sink -> proc.inputStream.copyTo(sink) }
        proc.waitFor()
        return out
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RopeahVPN log ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
