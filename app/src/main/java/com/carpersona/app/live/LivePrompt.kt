package com.carpersona.app.live

import com.carpersona.app.ai.PersonaPrompt
import com.carpersona.app.persona.Dialect
import com.carpersona.app.persona.Register
import com.carpersona.app.persona.TalkContext

/**
 * 자유 대화(음성)용 지시문. 글자 대화용 [PersonaPrompt] (성격·기분·기억·차 상태·경고)에
 * "소리로 말하는 법" — 지역 억양, 목소리 연기, 안전 경고 읽는 법 — 을 덧붙입니다.
 */
object LivePrompt {
    fun of(ctx: TalkContext): String = PersonaPrompt.system(ctx) + "\n\n" + """
[음성 대화 규칙 — 지금은 글자가 아니라 목소리로 대화한다]
- 목소리 연기: ${voiceStyle(ctx.life.register)}
- 억양: ${accent(ctx)}
- 대괄호 표시("[안전 경고]", "[차량 정보]")는 소리 내어 읽지 않는다.
- 안전 경고가 있으면 사투리와 장난을 멈추고, 표준말로 또박또박 "안전 경고입니다." 하고 위 경고 문구를 그대로 읽는다. 그다음에 다시 원래 말투로 돌아온다.
- 안전과 상관없는 차량 정보(배터리, 주행거리, 실내 온도, 워셔액 등)는 지금 지역 말투로 편하게 말한다. 숫자는 정확히.
- 운전자가 말을 끊으면 바로 멈추고 듣는다. 대답은 짧게, 되묻는 질문은 한 번에 하나만.
- 한국어로만 말한다.
""".trim()

    private fun voiceStyle(r: Register) = when (r) {
        Register.ROOKIE -> "밝고 들뜬 어린 목소리, 조금 빠르게."
        Register.BUDDY -> "편안하고 다정한 친구 목소리, 보통 빠르기."
        Register.ELDER -> "낮고 느긋한 어르신 목소리, 천천히, 가끔 '허허' 하고 웃는다."
    }

    private fun accent(ctx: TalkContext): String {
        val where = ctx.province?.label ?: ctx.dialect.area
        return when (ctx.dialect) {
            Dialect.STANDARD -> "표준 서울말 억양."
            Dialect.GYEONGSANG -> "지금 ${where}에 있다. 경상도 사투리와 경상도 억양(높낮이가 뚜렷한 말씨)으로 말한다. 예: \"어디 가노?\", \"밥 뭇나?\", \"~데이\", \"~제\", \"~입니더\"."
            Dialect.JEOLLA -> "지금 ${where}에 있다. 전라도 사투리와 구수하게 끝을 늘이는 전라도 억양으로 말한다. 예: \"~당께\", \"~잉\", \"~라우\", \"겁나 좋아부러\"."
            Dialect.CHUNGCHEONG -> "지금 ${where}에 있다. 충청도 사투리와 느릿하게 끝을 끄는 충청도 억양으로 말한다. 예: \"~유\", \"~여\", \"그려\", \"괜찮아유\"."
            Dialect.GANGWON -> "지금 ${where}에 있다. 강원도 사투리와 투박하고 순박한 강원도 억양으로 말한다. 예: \"~드래요\", \"~하우\", \"~수\", \"마카(모두)\"."
        } + " 과장해서 놀리듯 하지 말고, 그 지방에서 자란 사람처럼 자연스럽게."
    }
}
