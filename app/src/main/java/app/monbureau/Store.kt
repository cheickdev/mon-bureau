package app.monbureau

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Captured notifications, kept on the phone only. */
class Store private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "bureau.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE msgs(id INTEGER PRIMARY KEY AUTOINCREMENT, k TEXT UNIQUE, pkg TEXT, app TEXT, " +
                "chat TEXT, conv TEXT, sender TEXT, body TEXT, time INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** The unique key makes a notification that is posted again (same message) a no-op. */
    fun add(k: String, pkg: String, app: String, chat: String, conv: String, sender: String, body: String, time: Long) {
        val cv = ContentValues()
        cv.put("k", k)
        cv.put("pkg", pkg)
        cv.put("app", app)
        cv.put("chat", chat)
        cv.put("conv", conv)
        cv.put("sender", sender)
        cv.put("body", body)
        cv.put("time", time)
        writableDatabase.insertWithOnConflict("msgs", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun list(limit: Int): String {
        val out = JSONArray()
        readableDatabase.rawQuery(
            "SELECT id,pkg,app,chat,conv,sender,body,time FROM msgs ORDER BY time DESC, id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val o = JSONObject()
                o.put("id", c.getLong(0))
                o.put("pkg", c.getString(1))
                o.put("app", c.getString(2))
                o.put("chat", c.getString(3))
                o.put("conv", c.getString(4))
                o.put("sender", c.getString(5))
                o.put("text", c.getString(6))
                o.put("time", c.getLong(7))
                out.put(o)
            }
        }
        return out.toString()
    }

    fun delete(id: Long) {
        writableDatabase.delete("msgs", "id=?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete("msgs", null, null)
    }

    companion object {
        @Volatile
        private var inst: Store? = null

        fun get(ctx: Context): Store = inst ?: synchronized(this) {
            inst ?: Store(ctx.applicationContext).also { inst = it }
        }
    }
}
