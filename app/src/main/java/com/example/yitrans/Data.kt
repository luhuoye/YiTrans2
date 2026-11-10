package com.example.yitrans

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class Sent(val sec: String, val yi: String, val py: String, val zh: String) {
    fun toJson(): JSONObject = JSONObject().put("sec", sec).put("yi", yi).put("py", py).put("zh", zh)
    companion object {
        fun from(o: JSONObject) = Sent(o.getString("sec"), o.getString("yi"), o.getString("py"), o.getString("zh"))
    }
}

/** type: 0 彝→中选择, 1 中→彝选择, 2 判断 */
data class Q(
    val type: Int, val prompt: String, val shown: String,
    val options: List<String>, val ans: Int, val expl: String
) {
    val id: String get() = "$type|$shown|${options[ans]}"
    fun toJson(): JSONObject = JSONObject().put("type", type).put("prompt", prompt).put("shown", shown)
        .put("options", JSONArray(options)).put("ans", ans).put("expl", expl)

    companion object {
        fun from(o: JSONObject): Q {
            val a = o.getJSONArray("options")
            return Q(o.getInt("type"), o.getString("prompt"), o.getString("shown"),
                (0 until a.length()).map { a.getString(it) }, o.getInt("ans"), o.getString("expl"))
        }
    }
}

object Repo {
    var sents: List<Sent> = emptyList()
    private var cleanYi: List<String> = emptyList()
    private var cleanZh: List<String> = emptyList()
    private var roman: List<Pair<String, Char>> = emptyList()

    private fun read(c: Context, name: String) =
        c.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun load(c: Context) {
        if (sents.isNotEmpty()) return
        val a = JSONArray(read(c, "sentences.json"))
        sents = (0 until a.length()).map { Sent.from(a.getJSONObject(it)) }
        cleanYi = sents.map { clean(it.yi) }
        cleanZh = sents.map { clean(it.zh) }
        val m = JSONObject(read(c, "yimap.json"))
        roman = m.keys().asSequence().map { Pair(m.getString(it), it[0]) }
            .sortedWith(compareBy({ it.first.length }, { it.first }, { it.second })).toList()
    }

    fun clean(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    /** 拼音候选：先完全匹配，再前缀匹配 */
    fun cands(b: String): List<Char> {
        if (b.isEmpty()) return emptyList()
        val exact = roman.filter { it.first == b }.map { it.second }
        val pre = roman.filter { it.first != b && it.first.startsWith(b) }.map { it.second }
        return (exact + pre).distinct().take(60)
    }

    private fun grams(s: String): Set<String> =
        if (s.length < 2) s.map { it.toString() }.toSet() else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()

    private fun score(q: String, t: String): Double {
        if (q.isEmpty() || t.isEmpty()) return 0.0
        if (t == q) return 3.0
        if (t.contains(q)) return 2.0 + q.length.toDouble() / t.length
        if (t.length >= 2 && q.contains(t)) return 1.5 + t.length.toDouble() / q.length
        val a = grams(q); val b = grams(t)
        return 2.0 * a.count { it in b } / (a.size + b.size)
    }

    /** 词句库检索（非机器翻译）：返回最相近的若干条 */
    fun search(query: String, fromYi: Boolean): List<Sent> {
        val q = clean(query)
        if (q.isEmpty()) return emptyList()
        val pool = if (fromYi) cleanYi else cleanZh
        return sents.indices.map { it to score(q, pool[it]) }
            .filter { it.second >= 0.3 }.sortedByDescending { it.second }.take(10).map { sents[it.first] }
    }

    fun isYi(c: Char) = c.code in 0xA000..0xA48C

    fun sim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val x = grams(a); val y = grams(b)
        return 2.0 * x.count { it in y } / (x.size + y.size)
    }

    fun sections(): List<String> = sents.map { it.sec }.distinct()
    fun syllables(): List<Pair<String, Char>> = roman.sortedWith(compareBy({ it.first }, { it.second }))

    /** 题型: 0彝→中选择 1中→彝选择 2判断 3翻译(彝→中) 4翻译(中→彝) 5填空 */
    fun makeQ(kind: Int, s: Sent = sents.random()): Q {
        val pool = sents.filter { it.zh != s.zh && it.yi != s.yi }.shuffled()
        val expl = "${s.yi}\n${s.py}\n${s.zh}"
        return when (kind) {
            0 -> {
                val o = (pool.distinctBy { it.zh }.take(3).map { it.zh } + s.zh).shuffled()
                Q(0, "选择题：下面的彝文是什么意思？", s.yi, o, o.indexOf(s.zh), expl)
            }
            1 -> {
                val o = (pool.distinctBy { it.yi }.take(3).map { it.yi } + s.yi).shuffled()
                Q(1, "选择题：下面的中文用彝文怎么说？", s.zh, o, o.indexOf(s.yi), expl)
            }
            2 -> {
                val right = Random.nextBoolean()
                val zh = if (right) s.zh else pool.first().zh
                Q(2, "判断题：下面的译文是否正确？", "${s.yi}\n译文：$zh", listOf("正确", "错误"), if (right) 0 else 1, expl)
            }
            3 -> Q(3, "翻译题：把彝文翻译成中文", s.yi, listOf(s.zh), 0, expl)
            4 -> Q(4, "翻译题：把中文翻译成彝文（用彝文键盘）", s.zh, listOf(s.yi), 0, expl)
            else -> {
                val b = if (s.yi.count { isYi(it) } >= 3) s else sents.first { it.yi.count { c -> isYi(c) } >= 3 }
                val k = b.yi.indices.filter { isYi(b.yi[it]) }.random()
                val blank = b.yi.substring(0, k) + "＿" + b.yi.substring(k + 1)
                Q(5, "填空题：根据中文，补全彝文（输入＿处的字）", "${b.zh}\n$blank", listOf(b.yi[k].toString()), 0,
                    "${b.yi}\n${b.py}\n${b.zh}")
            }
        }
    }

    fun pts(t: Int) = when (t) { 0, 1 -> 3; 2 -> 2; 5 -> 4; else -> 5 }
    fun group(t: Int) = when (t) { 0, 1 -> "选择题"; 5 -> "填空题"; 2 -> "判断题"; else -> "翻译题" }

    /** 试卷：选择10×3 + 填空5×4 + 判断10×2 + 翻译6×5 = 100 分 */
    fun makeExam(): List<Q> {
        val ss = sents.shuffled(); var i = 0
        val kinds = List(5) { 0 } + List(5) { 1 } + List(5) { 5 } + List(10) { 2 } + List(3) { 3 } + List(3) { 4 }
        return kinds.map { makeQ(it, ss[i++]) }
    }

    /** 判分：返回 0.0~1.0 的得分比例。选择/判断 a 为选项序号 */
    fun grade(q: Q, a: String): Double {
        if (a.isBlank()) return 0.0
        return when (q.type) {
            0, 1, 2 -> if (a.toIntOrNull() == q.ans) 1.0 else 0.0
            5 -> if (clean(a) == clean(q.options[0])) 1.0 else 0.0
            else -> {
                val c = clean(a); val t = q.options[0]
                val alts = t.split("/").map { clean(it) } + clean(t)
                if (alts.any { it == c }) 1.0 else {
                    val s = alts.maxOf { sim(c, it) }
                    if (s >= 0.8) 0.8 else if (s >= 0.5) 0.5 else 0.0
                }
            }
        }
    }
}
