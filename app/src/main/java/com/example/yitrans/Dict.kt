@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yitrans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DictScreen(st: Store) {
    var t by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var sec by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = t, containerColor = Color.White) {
            Tab(selected = t == 0, onClick = { t = 0; q = "" }, text = { Text("词句分类") })
            Tab(selected = t == 1, onClick = { t = 1; q = "" }, text = { Text("音节表") })
        }
        OutlinedTextField(
            q, { q = it }, singleLine = true, shape = RoundedCornerShape(12.dp),
            placeholder = { Text(if (t == 0) "搜索：中文 / 拼音 / 彝文" else "搜索拼音，如 ne") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        )
        if (t == 0) {
            val key = q.trim().lowercase()
            val list: List<Sent>? = when {
                key.isNotEmpty() -> Repo.sents.filter { it.zh.contains(key) || it.py.lowercase().contains(key) || it.yi.contains(key) }.take(60)
                sec != null -> Repo.sents.filter { it.sec == sec }
                else -> null
            }
            if (list == null) {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(Repo.sections()) { s ->
                        AppCard(Modifier.clickable { sec = s }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                Text("${Repo.sents.count { it.sec == s }} 句  ›", color = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                if (key.isEmpty()) TextButton(onClick = { sec = null }) { Text("‹ 返回分类（$sec）") }
                else Text("找到 ${list.size} 条", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) { items(list) { SentCard(it, st) } }
            }
        } else {
            val key = q.trim().lowercase()
            val list = Repo.syllables().filter { key.isEmpty() || it.first.startsWith(key) || it.second.toString() == q.trim() }
            Text("共 ${list.size} 个音节（取自书中例句；完整字表见翻译页键盘「字表」）", fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f).padding(horizontal = 8.dp)) {
                items(list) { (r, c) ->
                    Card(
                        Modifier.padding(4.dp), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(c.toString(), fontSize = 30.sp, textAlign = TextAlign.Center)
                            Text(r, fontSize = 13.sp, color = Red)
                        }
                    }
                }
            }
        }
    }
}
