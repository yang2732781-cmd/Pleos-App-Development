package com.chagok.app.diary

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.chagok.app.navi.Place
import org.json.JSONArray
import org.json.JSONObject

/** 주행 중 일어난 일 하나 (타임라인 한 줄) */
data class Moment(val at: Long, val icon: String, val text: String)

/** 일기 한 편 = 주행 한 번 */
data class DiaryEntry(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val destination: Place?,
    val distanceKm: Float,
    val durationMin: Int,
    val title: String,
    val body: String,
    val tags: List<String>,
    val moments: List<Moment>,
)

/**
 * 일기장 저장소. 휴대폰(차량) 안에만 저장합니다 (SharedPreferences + JSON).
 * 서버로 보내지 않으므로 개인정보 동의가 따로 필요 없습니다.
 */
class DiaryStore(context: Context) {
    private val prefs = context.getSharedPreferences("diary", Context.MODE_PRIVATE)

    /** 화면이 자동으로 다시 그려지는 목록 (최신이 맨 앞) */
    val entries = mutableStateListOf<DiaryEntry>()

    init {
        val text = prefs.getString("entries", null)
        if (text != null) runCatching {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) entries += fromJson(arr.getJSONObject(i))
        }
    }

    fun add(entry: DiaryEntry) {
        entries.add(0, entry)
        save()
    }

    fun delete(entry: DiaryEntry) {
        entries.removeAll { it.id == entry.id }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        entries.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    private fun toJson(e: DiaryEntry) = JSONObject().apply {
        put("id", e.id); put("startedAt", e.startedAt); put("endedAt", e.endedAt)
        e.destination?.let { d ->
            put("destName", d.name)
            d.lat?.let { put("lat", it) }
            d.lon?.let { put("lon", it) }
        }
        put("distanceKm", e.distanceKm.toDouble()); put("durationMin", e.durationMin)
        put("title", e.title); put("body", e.body)
        put("tags", JSONArray(e.tags))
        put("moments", JSONArray().apply {
            e.moments.forEach { m -> put(JSONObject().put("at", m.at).put("icon", m.icon).put("text", m.text)) }
        })
    }

    private fun fromJson(o: JSONObject): DiaryEntry {
        val tags = o.optJSONArray("tags") ?: JSONArray()
        val moments = o.optJSONArray("moments") ?: JSONArray()
        return DiaryEntry(
            id = o.getLong("id"),
            startedAt = o.getLong("startedAt"),
            endedAt = o.getLong("endedAt"),
            destination = if (o.has("destName")) Place(
                o.getString("destName"),
                if (o.has("lat")) o.getDouble("lat") else null,
                if (o.has("lon")) o.getDouble("lon") else null,
            ) else null,
            distanceKm = o.optDouble("distanceKm", 0.0).toFloat(),
            durationMin = o.optInt("durationMin"),
            title = o.optString("title"),
            body = o.optString("body"),
            tags = List(tags.length()) { tags.getString(it) },
            moments = List(moments.length()) {
                val m = moments.getJSONObject(it)
                Moment(m.getLong("at"), m.getString("icon"), m.getString("text"))
            },
        )
    }
}
