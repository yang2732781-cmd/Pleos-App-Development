package com.chagok.app.diary

import com.chagok.app.trip.DayNight
import com.chagok.app.trip.TripLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 주행 기록(TripLog)을 사람이 쓴 것 같은 일기 문장으로 바꿉니다.
 * 생성형 AI 없이 규칙(템플릿)으로 만들기 때문에 인터넷이 없어도 되고 결과가 항상 안정적입니다.
 */
object DiaryWriter {

    fun write(log: TripLog, endedAt: Long, distanceKm: Float): DiaryEntry {
        val durationMin = ((endedAt - log.startedAt) / 60000).toInt().coerceAtLeast(1)
        val dest = log.destination?.name
        val startPart = DayNight.dayPart(log.startedAt)
        val endPart = DayNight.dayPart(endedAt)
        val rained = log.rainMinutes >= 3

        // ── 제목 ──
        val goal = if (dest != null) "${dest} 가는 길" else "어딘가로 가는 길"
        val title = when {
            rained && log.sunsetSeen -> "비 내리던 해 질 녘, $goal"
            rained -> "비 내리는 ${startPart.word}, $goal"
            log.sunsetSeen -> "노을을 지나 ${dest ?: "목적지"}까지"
            log.tunnelCount >= 3 -> "터널을 ${log.tunnelCount}번 지난 ${startPart.word}"
            startPart.isDark -> "${startPart.word}의 고요한 드라이브, $goal"
            else -> "${startPart.word}의 드라이브, $goal"
        }

        // ── 본문 ──
        val sb = StringBuilder()
        val who = when (val n = log.rearAtStart ?: 0) {
            0 -> "혼자"
            else -> "뒷좌석에 ${n}명을 태우고"
        }
        sb.append("${dateText(log.startedAt)} ${startPart.word} ${clockText(log.startedAt)}, $who 출발했어요. ")

        if (rained) {
            val first = log.firstRainAt?.let { ((it - log.startedAt) / 60000).toInt() } ?: 0
            if (first >= 5) sb.append("출발하고 ${first}분쯤 지나 비가 내리기 시작했고, ")
            else sb.append("출발할 때부터 비가 내렸고, ")
            sb.append("약 ${durationText(log.rainMinutes.roundToInt())} 동안 와이퍼가 바쁘게 움직였어요. ")
            log.lastRainEndAt?.takeIf { log.rainOnSince == null }?.let {
                sb.append("비는 ${DayNight.dayPart(it).word} ${clockText(it)}쯤 그쳤어요. ")
            }
        }
        if (log.tunnelCount > 0) sb.append("터널을 ${log.tunnelCount}번 지났어요. ")
        if (log.sunsetSeen) {
            val dark = log.darkMinutes.roundToInt()
            sb.append("가는 길에 해가 저물어, 마지막 ${durationText(dark)}은 밤길이었어요. ")
        }
        if (log.sickAlerts > 0) {
            val causes = log.sickReasons.take(2)
            val why = when (causes.size) {
                0 -> ""
                1 -> "${causes[0]} 때문에 "
                else -> "${Josa.and(causes[0])} ${causes[1]} 때문에 "
            }
            sb.append("${why}뒷좌석 멀미 주의가 ${log.sickAlerts}번 떴어요. ")
            log.restStop?.let { sb.append("${it.name}에 들러 잠시 쉬어 가기로 했어요. ") }
        }
        if (log.windowOpened) sb.append("창문을 열고 바깥 공기를 쐬기도 했어요. ")
        if (log.tempMin != null && log.tempMax != null) {
            val avg = (log.tempMin!! + log.tempMax!!) / 2
            val feel = when {
                avg < 19f -> "조금 서늘했어요"
                avg <= 25f -> "포근했어요"
                else -> "조금 더웠어요"
            }
            val lo = log.tempMin!!.roundToInt(); val hi = log.tempMax!!.roundToInt()
            sb.append(if (lo == hi) "차 안은 ${lo}도로 $feel. " else "차 안은 ${lo}~${hi}도로 $feel. ")
        }
        sb.append("${durationText(durationMin)} 동안 ${"%.0f".format(distanceKm)}km를 달려 ")
        sb.append("${endPart.word} ${clockText(endedAt)}에 ${dest?.let { Josa.at(it) } ?: "목적지에"} 도착했어요.")

        // ── 태그 ──
        val tags = buildList {
            add("🛣 ${"%.0f".format(distanceKm)}km")
            add("⏱ ${durationText(durationMin)}")
            if (rained) add("☔ 비 ${durationText(log.rainMinutes.roundToInt())}")
            if (log.tunnelCount > 0) add("🚇 터널 ${log.tunnelCount}")
            if (log.darkMinutes >= 5) add("🌙 밤길 ${durationText(log.darkMinutes.roundToInt())}")
            if (log.sickAlerts > 0) add("🤢 멀미 주의 ${log.sickAlerts}")
            log.restStop?.let { add("☕ ${it.name}") }
            if ((log.rearAtStart ?: 0) > 0) add("👨‍👩‍👧 ${(log.rearAtStart ?: 0) + 1}명")
        }

        return DiaryEntry(
            id = endedAt,
            startedAt = log.startedAt,
            endedAt = endedAt,
            destination = log.destination,
            distanceKm = distanceKm,
            durationMin = durationMin,
            title = title,
            body = sb.toString().trim(),
            tags = tags,
            moments = log.moments.toList(),
        )
    }

    fun dateText(t: Long): String = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(t)

    /** 예: 6시 12분 (12시간제, 앞에 오전/오후 대신 '저녁' 같은 말을 붙여 씀) */
    fun clockText(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        var h = c.get(Calendar.HOUR_OF_DAY) % 12
        if (h == 0) h = 12
        val m = c.get(Calendar.MINUTE)
        return if (m == 0) "${h}시" else "${h}시 ${m}분"
    }

    fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.KOREAN).format(t)

    fun durationText(min: Int): String = when {
        min < 60 -> "${min}분"
        min % 60 == 0 -> "${min / 60}시간"
        else -> "${min / 60}시간 ${min % 60}분"
    }
}

/** 한국어 조사 붙이기 (받침 있으면 '으로/에', 없으면 '로/에') */
object Josa {
    private fun hasBatchim(word: String): Boolean {
        val c = word.trim().lastOrNull() ?: return false
        if (c !in '가'..'힣') return false
        return (c - '가') % 28 != 0
    }

    private fun isRieul(word: String): Boolean {
        val c = word.trim().lastOrNull() ?: return false
        return c in '가'..'힣' && (c - '가') % 28 == 8
    }

    /** 강릉 → 강릉으로, 집 → 집으로, 바다 → 바다로, 서울 → 서울로 */
    fun toward(word: String) = word + if (hasBatchim(word) && !isRieul(word)) "으로" else "로"

    /** 굽잇길 → 굽잇길과, 정체 → 정체와 */
    fun and(word: String) = word + if (hasBatchim(word)) "과" else "와"

    /** 강릉 → 강릉에 */
    fun at(word: String) = "${word}에"
}
