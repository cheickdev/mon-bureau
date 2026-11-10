package app.monbureau

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the notifications that arrive on this phone, once the owner has granted
 * notification access in Android settings, and files them in the local store.
 */
class NotifService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            capture(sbn)
        } catch (_: Exception) {
        }
    }

    private fun capture(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName || sbn.isOngoing) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val isWhatsApp = pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b"
        if (!isWhatsApp && prefs.getString("captureAll", "0") != "1") return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = clean(ex.getCharSequence(Notification.EXTRA_TITLE))
        val convTitle = clean(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
        val isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val conv = if (isGroup) convTitle.ifEmpty { title } else ""
        val chat = pkg + "|" + convTitle.ifEmpty { title }
        val app = if (isWhatsApp) "WhatsApp" else appLabel(pkg)
        val only = (prefs.getString("only", "") ?: "").lowercase().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val store = Store.get(this)
        var kept = false

        val bundles = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (bundles != null && bundles.isNotEmpty()) {
            for (p in bundles) {
                val b = p as? Bundle ?: continue
                val text = b.getCharSequence("text")?.toString()?.trim() ?: ""
                // A message without a sender is one the owner wrote themselves.
                val sender = clean(b.getCharSequence("sender"))
                if (text.isEmpty() || sender.isEmpty()) continue
                if (!wanted(only, conv, sender, title)) continue
                val time = b.getLong("time", sbn.postTime)
                store.add("$chat|$sender|$time|${text.hashCode()}", pkg, app, chat, conv, sender, text, time)
                kept = true
            }
        } else {
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.trim() ?: ""
            if (text.isEmpty() || title.isEmpty()) return
            if (!wanted(only, conv, title, title)) return
            store.add("${sbn.key}|$title|${text.hashCode()}", pkg, app, chat, conv, title, text, sbn.postTime)
            kept = true
        }

        if (kept) {
            val action = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
            if (action != null) replies[chat] = action
        }
    }

    private fun wanted(only: List<String>, conv: String, sender: String, title: String): Boolean {
        if (only.isEmpty()) return true
        val hay = "$conv $sender $title".lowercase()
        return only.any { hay.contains(it) }
    }

    private fun clean(cs: CharSequence?): String =
        (cs?.toString() ?: "").replace(COUNT_SUFFIX, "").trim()

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    companion object {
        const val PREFS = "bureau"
        private val COUNT_SUFFIX = Regex("""\s*\(\d+\s[^)]*\)\s*$""")

        /** Reply actions of the notifications seen since the service started, by conversation. */
        val replies = ConcurrentHashMap<String, Notification.Action>()
    }
}
