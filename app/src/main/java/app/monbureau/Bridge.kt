package app.monbureau

import android.app.Activity
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.JavascriptInterface
import org.json.JSONArray
import java.io.File

/** What the page can ask the phone to do. Exposed to the bundled page as `Native`. */
class Bridge(private val act: Activity) {
    private val stateFile get() = File(act.filesDir, "state.json")
    private val prefs get() = act.getSharedPreferences(NotifService.PREFS, Context.MODE_PRIVATE)

    @JavascriptInterface
    fun loadState(): String = try {
        if (stateFile.exists()) stateFile.readText() else ""
    } catch (_: Exception) {
        ""
    }

    @JavascriptInterface
    fun saveState(json: String) {
        // Write beside, then swap: a kill mid-write must not leave a truncated file.
        val tmp = File(act.filesDir, "state.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(stateFile)) {
            stateFile.writeText(json)
            tmp.delete()
        }
    }

    @JavascriptInterface
    fun messages(limit: Int): String = Store.get(act).list(limit)

    @JavascriptInterface
    fun deleteMessage(id: String) {
        id.toLongOrNull()?.let { Store.get(act).delete(it) }
    }

    @JavascriptInterface
    fun clearMessages() = Store.get(act).clear()

    @JavascriptInterface
    fun hasAccess(): Boolean {
        val on = Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners") ?: ""
        return on.split(":").any { it.startsWith(act.packageName + "/") }
    }

    @JavascriptInterface
    fun openAccess() = start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    @JavascriptInterface
    fun openAppInfo() =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName)))

    @JavascriptInterface
    fun replyable(): String = JSONArray(NotifService.replies.keys.toList()).toString()

    @JavascriptInterface
    fun reply(chat: String, text: String): Boolean {
        val action = NotifService.replies[chat] ?: return false
        val inputs = action.remoteInputs ?: return false
        return try {
            val intent = Intent()
            val results = Bundle()
            for (ri in inputs) results.putCharSequence(ri.resultKey, text)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(act, 0, intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun getPref(key: String): String = prefs.getString(key, "") ?: ""

    @JavascriptInterface
    fun setPref(key: String, value: String) = prefs.edit().putString(key, value).apply()

    @JavascriptInterface
    fun openUrl(url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    @JavascriptInterface
    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(Intent.createChooser(send, null))
    }

    /** Colours the system bars to match the theme chosen in the page. */
    @JavascriptInterface
    fun setBars(dark: Boolean) {
        act.runOnUiThread {
            val color = if (dark) Color.parseColor("#101513") else Color.parseColor("#ECF0ED")
            act.window.statusBarColor = color
            act.window.navigationBarColor = color
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val view = act.window.decorView
            @Suppress("DEPRECATION")
            view.systemUiVisibility = if (dark) view.systemUiVisibility and light.inv() else view.systemUiVisibility or light
        }
    }

    private fun start(intent: Intent) {
        act.runOnUiThread {
            try {
                act.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}
