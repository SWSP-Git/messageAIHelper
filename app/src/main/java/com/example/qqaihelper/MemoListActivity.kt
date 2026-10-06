package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * 备忘录列表页。
 *
 * 数据来源：
 * QQNotificationListener 在处理「备忘」类型的消息时，会把内容追加写入
 * filesDir/memo_list.txt。本页面只是简单地读取这个文件并列表展示。
 *
 * 为什么不用数据库（Room）：
 * 早期版本尝试用 Room 做持久化，但新版 Android Studio 的 KSP 与 kapt
 * 插件存在兼容性问题，反复踩坑后决定退回纯文本方案。备忘录只是「浏览」
 * 场景，不需要复杂查询，纯文本完全够用。
 *
 * 文件格式（每条备忘之间用空行分隔）：
 *   来源: QQ
 *   时间: 2026-10-06 12:00:00
 *   摘要: 摘要内容
 *   内容: 原始消息
 *   （空行）
 *   来源: 微信
 *   ...
 */
class MemoListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memo_list)

        // 齿轮 → AI 与日程设置页
        findViewById<ImageView>(R.id.btnAiOptions).setOnClickListener {
            startActivity(Intent(this, AiOptionsActivity::class.java))
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewMemos)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 读取备忘录文件，按空行分割成条目
        val memoFile = File(filesDir, "memo_list.txt")
        val memos = if (memoFile.exists()) {
            memoFile.readText().split("\n\n").filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        // 交给适配器渲染成卡片列表
        recyclerView.adapter = MemoAdapter(memos)
    }
}
