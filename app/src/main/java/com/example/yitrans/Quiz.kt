@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yitrans

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun yiAns(q: Q) = q.type == 4 || q.type == 5
fun typed(q: Q) = q.type >= 3

/** 作答框：答案是彝文时用内置彝文键盘(由外层放 YiKeyboard)，否则用系统输入法 */
@Composable
fun AnswerBox(q: Q, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    if (yiAns(q)) {
        val kc = LocalSoftwareKeyboardController.current
        val fm = LocalFocusManager.current
        LaunchedEffect(q.id) { kc?.hide(); fm.clearFocus() }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .background(Color(0xFFF1F1F1), RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text(
                if (value.isEmpty()) "用下方彝文键盘作答…" else if (enabled) "$value|" else value,
                fontSize = 24.sp, color = if (value.isEmpty()) Color.Gray else Color.Black
            )
        }
    } else {
        val fr = remember(q.id) { FocusRequester() }
        OutlinedTextField(
            value, onChange, enabled = enabled, shape = RoundedCornerShape(12.dp),
            placeholder = { Text("输入中文答案") },
            modifier = Modifier.fillMaxWidth().focusRequester(fr)
        )
        LaunchedEffect(q.id) { if (enabled) fr.requestFocus() }
    }
}

@Composable
fun QuestionCard(q: Q, no: String? = null) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Text((no ?: "") + q.prompt, fontSize = 14.sp, color = Red, fontWeight = FontWeight.Medium)
            Text(q.shown, fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun OptionButton(text: String, bg: Color, big: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) { Text(text, fontSize = if (big) 20.sp else 16.sp, modifier = Modifier.padding(vertical = 4.dp)) }
}

// ---------------- 练习 ----------------

@Composable
fun QuizScreen(st: Store) {
    // 6混合 7选择 2判断 5填空 8翻译 9收藏题
    var mode by rememberSaveable { mutableIntStateOf(6) }
    var q by remember { mutableStateOf<Q?>(null) }
    var pick by remember { mutableIntStateOf(-1) }
    var text by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var got by remember { mutableDoubleStateOf(0.0) }
    var total by remember { mutableIntStateOf(0) }
    var score by remember { mutableDoubleStateOf(0.0) }

    fun next() {
        pick = -1; text = ""; done = false; got = 0.0
        q = when (mode) {
            9 -> st.favQ.randomOrNull()
            6 -> Repo.makeQ((0..5).random())
            7 -> Repo.makeQ((0..1).random())
            8 -> Repo.makeQ((3..4).random())
            else -> Repo.makeQ(mode)
        }
    }
    LaunchedEffect(mode) { next() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val names = listOf("混合", "选择题", "判断题", "填空题", "翻译题", "收藏题")
                val modes = listOf(6, 7, 2, 5, 8, 9)
                names.forEachIndexed { i, n -> FilterChip(selected = mode == modes[i], onClick = { mode = modes[i] }, label = { Text(n) }) }
            }
            Text("已做 $total 题，累计得分 ${"%.1f".format(score)}", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            val cur = q
            if (cur == null) {
                Text(if (mode == 9) "还没有收藏的题目" else "加载中…", color = Color.Gray)
            } else {
                QuestionCard(cur)
                if (!typed(cur)) {
                    cur.options.forEachIndexed { i, o ->
                        val bg = when {
                            !done -> Color(0xFFFFF3D6)
                            i == cur.ans -> Color(0xFFC8E6C9)
                            i == pick -> Color(0xFFFFCDD2)
                            else -> Color(0xFFF5F5F5)
                        }
                        OptionButton(o, bg, cur.type == 1) {
                            if (!done) { pick = i; done = true; got = Repo.grade(cur, i.toString()); total++; score += got }
                        }
                    }
                } else {
                    AnswerBox(cur, text, { text = it }, !done)
                    if (!done) {
                        Button(
                            onClick = { done = true; got = Repo.grade(cur, text); total++; score += got },
                            enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("提交答案") }
                    }
                }
                if (done) {
                    val msg = when {
                        got >= 1.0 -> "✔ 完全正确"
                        got > 0 -> "◐ 部分正确（得 ${"%.1f".format(got)} 分）"
                        else -> "✘ 回答错误"
                    }
                    Text(msg, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    if (typed(cur)) Text("参考答案：" + cur.options[0], fontSize = 18.sp, color = Color(0xFF2E7D32))
                    Text(cur.expl, fontSize = 15.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                    val fav = st.favQ.any { it.id == cur.id }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { st.toggleQ(cur) }) { Text(if (fav) "★ 已收藏" else "☆ 收藏本题") }
                        Button(onClick = { next() }) { Text("下一题") }
                    }
                }
            }
        }
        val cur = q
        if (cur != null && typed(cur) && yiAns(cur) && !done) {
            YiKeyboard(onText = { text += it }, onBack = { text = text.dropLast(1) })
        }
    }
}

// ---------------- 考试 ----------------

class ExamState {
    var stage by mutableIntStateOf(0) // 0说明 1答题 2成绩
    var qs by mutableStateOf<List<Q>>(emptyList())
    val ans = mutableStateListOf<String>()
    var idx by mutableIntStateOf(0)
    var got by mutableStateOf<List<Double>>(emptyList())
    var score by mutableIntStateOf(0)

    fun start() {
        qs = Repo.makeExam(); ans.clear(); qs.forEach { ans.add("") }; idx = 0; stage = 1
    }

    fun submit(st: Store) {
        got = qs.mapIndexed { i, q -> Repo.grade(q, ans[i]) }
        score = qs.indices.sumOf { got[it] * Repo.pts(qs[it].type) }.roundToInt()
        st.addScore(score)
        stage = 2
    }
}

@Composable
fun ExamScreen(st: Store, ex: ExamState) {
    when (ex.stage) {
        0 -> ExamIntro(st, ex)
        1 -> ExamRun(st, ex)
        else -> ExamResult(st, ex)
    }
}

@Composable
fun ExamIntro(st: Store, ex: ExamState) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            AppCard {
                Column(Modifier.padding(20.dp)) {
                    Text("模拟试卷（满分 100）", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Red)
                    Text("选择题 10 题 × 3 分 = 30 分", Modifier.padding(top = 10.dp))
                    Text("填空题 5 题 × 4 分 = 20 分")
                    Text("判断题 10 题 × 2 分 = 20 分")
                    Text("翻译题 6 题 × 5 分 = 30 分（彝→中 3 题，中→彝 3 题）")
                    Text("题目从《彝语900句》随机抽取，交卷后显示得分与错题。", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { ex.start() }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("开始考试") }
                }
            }
        }
        item {
            Text("历史成绩" + (st.best()?.let { "（最高 $it 分）" } ?: ""), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (st.scores.isEmpty()) Text("还没有成绩", color = Color.Gray)
        }
        itemsIndexed(st.scores.take(15)) { _, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(s.substringBefore("|"), color = Color.Gray, modifier = Modifier.weight(1f))
                Text(s.substringAfter("|") + " 分", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ExamRun(st: Store, ex: ExamState) {
    val q = ex.qs[ex.idx]
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("第 ${ex.idx + 1} / ${ex.qs.size} 题 · ${Repo.group(q.type)} · ${Repo.pts(q.type)} 分", color = Color.Gray, fontSize = 13.sp)
            LinearProgressIndicator(progress = { (ex.idx + 1f) / ex.qs.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            QuestionCard(q)
            if (!typed(q)) {
                q.options.forEachIndexed { i, o ->
                    val sel = ex.ans[ex.idx] == i.toString()
                    OptionButton(o, if (sel) Color(0xFFFFD54F) else Color(0xFFFFF3D6), q.type == 1) { ex.ans[ex.idx] = i.toString() }
                }
            } else {
                AnswerBox(q, ex.ans[ex.idx], { ex.ans[ex.idx] = it }, true)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (ex.idx > 0) ex.idx-- }, enabled = ex.idx > 0, modifier = Modifier.weight(1f)) { Text("上一题") }
                if (ex.idx < ex.qs.size - 1) {
                    Button(onClick = { ex.idx++ }, modifier = Modifier.weight(1f)) { Text("下一题") }
                } else {
                    Button(onClick = { confirm = true }, modifier = Modifier.weight(1f)) { Text("交卷") }
                }
            }
            TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("提前交卷") }
        }
        if (typed(q) && yiAns(q)) {
            YiKeyboard(onText = { ex.ans[ex.idx] = ex.ans[ex.idx] + it }, onBack = { ex.ans[ex.idx] = ex.ans[ex.idx].dropLast(1) })
        }
    }
    if (confirm) {
        val blank = ex.ans.count { it.isBlank() }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认交卷？") },
            text = { Text(if (blank > 0) "还有 $blank 题未作答，交卷后按 0 分计算。" else "全部题目已作答。") },
            confirmButton = { TextButton(onClick = { confirm = false; ex.submit(st) }) { Text("交卷") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续答题") } }
        )
    }
}

@Composable
fun ExamResult(st: Store, ex: ExamState) {
    val grade = when {
        ex.score >= 90 -> "优秀"; ex.score >= 80 -> "良好"; ex.score >= 60 -> "及格"; else -> "继续加油"
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ex.score}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Red)
                    Text("满分 100 · $grade", color = Color.Gray)
                    listOf("选择题", "填空题", "判断题", "翻译题").forEach { g ->
                        val idxs = ex.qs.indices.filter { Repo.group(ex.qs[it].type) == g }
                        val full = idxs.sumOf { Repo.pts(ex.qs[it].type) }
                        val have = idxs.sumOf { ex.got[it] * Repo.pts(ex.qs[it].type) }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(g, Modifier.weight(1f))
                            Text("${"%.1f".format(have)} / $full")
                        }
                    }
                    Button(onClick = { ex.start() }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("再考一次") }
                    OutlinedButton(onClick = { ex.stage = 0 }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
                }
            }
            Text("错题 / 失分题", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }
        itemsIndexed(ex.qs) { i, q ->
            if (ex.got[i] < 1.0) {
                val mine = if (!typed(q)) ex.ans[i].toIntOrNull()?.let { q.options[it] } else ex.ans[i].ifBlank { null }
                AppCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("第 ${i + 1} 题 · ${Repo.group(q.type)} · 得 ${"%.1f".format(ex.got[i] * Repo.pts(q.type))}/${Repo.pts(q.type)} 分", fontSize = 12.sp, color = Red)
                        Text(q.prompt, fontSize = 13.sp, color = Color.Gray)
                        Text(q.shown, fontSize = 20.sp)
                        Text("你的答案：" + (mine ?: "未作答"), fontSize = 15.sp, color = Color(0xFFC62828))
                        Text("正确答案：" + q.options[q.ans], fontSize = 16.sp, color = Color(0xFF2E7D32))
                        Text(q.expl, fontSize = 13.sp, color = Color.Gray)
                        val fav = st.favQ.any { it.id == q.id }
                        TextButton(onClick = { st.toggleQ(q) }) { Text(if (fav) "★ 已收藏" else "☆ 收藏本题") }
                    }
                }
            }
        }
    }
}
