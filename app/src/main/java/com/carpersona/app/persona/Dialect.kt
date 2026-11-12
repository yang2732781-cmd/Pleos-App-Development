package com.carpersona.app.persona

import com.carpersona.app.navi.Place
import com.carpersona.app.navi.Province

/**
 * 지역 말투 (사투리). 차가 지금 있는 곳(내비 현재 위치)에 따라 정해집니다.
 *
 * ⚠ 안드로이드 기본 TTS에는 사투리 목소리가 없어서, "사투리로 쓴 글" 을 표준어 억양으로 읽습니다.
 *    (어미·단어는 사투리, 억양은 표준어)
 * 변환은 전부 기기 안에서 합니다. 인터넷·API 키가 필요 없습니다.
 * 규칙은 아래 RULES / WORDS 표 한 곳에 모여 있어서, 현지 분께 보여 드리고 바로 고칠 수 있어요.
 */
enum class Dialect(val label: String, val area: String) {
    STANDARD("표준말", "수도권"),
    GANGWON("강원도 사투리", "강원도"),
    CHUNGCHEONG("충청도 사투리", "충청도"),
    JEOLLA("전라도 사투리", "전라도"),
    GYEONGSANG("경상도 사투리", "경상도");

    companion object {
        /** 장소 → 시·도(Province) → 말투. 시·도를 모르면 표준말. 서울·경기·인천·제주는 표준말 */
        fun of(p: Place?): Dialect = Province.of(p)?.dialect ?: STANDARD

        // ───────── 변환 규칙 ─────────
        // 문장 끝 어미만 바꿉니다. E = 문장 끝(! . 또는 글 끝)
        private const val E = "(?=[!.]|\$)"
        private const val EQ = "(?=[!.?]|\$)"
        private const val H = "(?<=[가-힣])"
        private const val V = "$H[아어]"   // 해체 어미 (넘었어, 좋아)
        /** 의문사(어디·뭐·왜…)가 든 물음 — 경상도는 "~노?", 예/아니오 물음은 "~나?" */
        private const val WH = "((?:어디|뭐|왜|언제|누구|몇|어떻게|무슨)[^.!?]*?)"

        private fun r(p: String, rep: String) = Regex(p) to rep

        private val RULES: Map<Pair<Dialect, Register>, List<Pair<Regex, String>>> = mapOf(
            // ── 경상도
            (GYEONGSANG to Register.ROOKIE) to listOf(
                r("${WH}가\\?", "$1가노?"), r("${WH}해\\?", "$1하노?"), r("(뭐|누구)야\\?", "$1고?"),
                r("이야\\?", "이가?"), r("야\\?", "가?"), r("$V\\?", "나?"), r("지\\?", "제?"),
                r("고마워$E", "고맙데이"), r("신나$E", "신난다"), r("져$E", "진다"),
                r("$V$E", "다"), r("야$E", "다"), r("지$E", "제"),
                r("(할|볼|줄|갈)게$E", "$1끼다"), r("해$E", "한다"), r("줘$E", "주라"),
            ),
            (GYEONGSANG to Register.BUDDY) to listOf(
                r("세요$EQ", "이소"), r("이에요$EQ", "입니더"), r("예요$EQ", "입니더"),
                r("죠$EQ", "지예"), r("요$EQ", "예"),
            ),
            (GYEONGSANG to Register.ELDER) to listOf(
                r("주게$E", "주소"), r("구먼", "구마"), r("일세", "이데이"), r("네$E", "데이"), r("지$E", "제"), r("세$EQ", "자"),
            ),
            // ── 전라도
            (JEOLLA to Register.ROOKIE) to listOf(
                r("이야\\?", "이여?"), r("야\\?", "여?"), r("$V\\?", "당가?"),
                r("고마워$E", "고마워잉"), r("신나$E", "신나부러"), r("져$E", "져부러"),
                r("$V$E", "당께"), r("야$E", "랑께"), r("지$E", "지라잉"), r("자$E", "자잉"), r("네$E", "네잉"),
                r("(할|볼|줄|갈)게$E", "$1랑께"), r("해$E", "하당께"), r("줘$E", "줘잉"),
            ),
            (JEOLLA to Register.BUDDY) to listOf(
                r("세요$EQ", "쇼잉"), r("이에요$EQ", "이어라우"), r("예요$EQ", "여라우"),
                r("죠$EQ", "지라우"), r("(?<!로)요$EQ", "라우"),
            ),
            (JEOLLA to Register.ELDER) to listOf(
                r("주게$E", "주소잉"), r("구먼", "구먼잉"), r("일세", "일세잉"), r("네$E", "네잉"), r("지$E", "지라잉"), r("세$EQ", "세잉"),
            ),
            // ── 충청도
            (CHUNGCHEONG to Register.ROOKIE) to listOf(
                r("이야\\?", "이여?"), r("야\\?", "여?"), r("$H([아어])\\?", "$1유?"),
                r("고마워$E", "고마워유"), r("신나$E", "신나유"), r("져$E", "져유"),
                r("$H([아어])$E", "$1유"), r("야$E", "여"), r("지$E", "지유"), r("자$E", "자유"), r("네$E", "네유"),
                r("(할|볼|줄|갈)게$E", "$1게유"), r("해$E", "해유"), r("줘$E", "줘유"), r("다$E", "다유"),
            ),
            (CHUNGCHEONG to Register.BUDDY) to listOf(
                r("이에요$EQ", "이에유"), r("예요$EQ", "여유"), r("죠$EQ", "쥬"), r("요$EQ", "유"),
            ),
            (CHUNGCHEONG to Register.ELDER) to listOf(
                r("주게$E", "줘유"), r("구먼", "구먼유"), r("일세", "일세유"), r("네$E", "네유"), r("지$E", "지유"), r("세$EQ", "세유"),
            ),
            // ── 강원도 (영서 지역 ~수/~우 말씨)
            (GANGWON to Register.ROOKIE) to listOf(
                r("$V\\?", "수?"), r("$V$E", "수"), r("해$E", "하우"), r("고마워$E", "고맙수"), r("신나$E", "신나우"),
            ),
            (GANGWON to Register.BUDDY) to listOf(
                // 강원 영동·영서에서 두루 알려진 "~드래요"(지난 일), "~하우/~시우", "있수/없수"
                r("([았었였])어요$EQ", "$1드래요"), r("([있없])어요$EQ", "$1수"),
                r("세요$EQ", "시우"), r("이에요$EQ", "이우"), r("예요$EQ", "이우"), r("네요$EQ", "구려"),
                r("해요$EQ", "하우"), r("까요\\?", "까우?"), r("죠$EQ", "지요"),
            ),
            (GANGWON to Register.ELDER) to listOf(
                r("주게$E", "주시우"), r("구먼", "구려"), r("일세", "이우"), r("네$E", "구려"), r("지$E", "지요"), r("세$EQ", "시우"),
            ),
        )

        /** 단어 바꾸기 (모든 말투 공통) */
        private val WORDS: Map<Dialect, List<Pair<Regex, String>>> = mapOf(
            GYEONGSANG to listOf(r("정말", "억수로"), r("엄청", "억수로"), r("아주", "억수로"), r("조금", "쪼매"), r("얼른", "얼릉")),
            JEOLLA to listOf(r("정말", "참말로"), r("엄청", "겁나"), r("아주", "겁나"), r("너무", "겁나"), r("조금", "쪼깐"), r("얼른", "얼릉")),
            CHUNGCHEONG to listOf(r("정말", "참말로"), r("조금", "쬐끔"), r("얼른", "얼릉")),
        )

        /** 대사를 지역 말투로 바꿉니다. `[안전 경고]` 줄은 정확해야 해서 그대로 두고, `[차량 정보]` 줄(안전과 무관)은 바꿉니다. */
        fun localize(text: String, d: Dialect, r: Register): String {
            if (d == STANDARD) return text
            val rules = RULES[d to r] ?: return text
            val words = WORDS[d].orEmpty()
            return text.split("\n").joinToString("\n") { line ->
                if (line.startsWith("[안전 경고]")) line
                else {
                    var s = line
                    for ((re, rep) in words) s = s.replace(re, rep)
                    for ((re, rep) in rules) s = re.replace(s, rep)
                    s
                }
            }
        }
    }
}
