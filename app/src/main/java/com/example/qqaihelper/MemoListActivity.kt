package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 备忘录列表页。
 *
 * 数据来源：StorageHelper.getMemoFile()（外部优先，私有降级）
 *
 * 显示策略：
 * - 默认只显示重要性为「高」或「中」的备忘
 * - 点击底部「显示全部备忘」按钮后显示所有备忘
 * - 再次点击切换回过滤模式（按钮文字变为「仅显示重要备忘」）
 */
class MemoListActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnToggleAll: Button

    /** 所有备忘（未过滤） */
    private var allMemos: List<String> = emptyList()

    /** 是否显示全部（false = 只显示中高重要性） */
    private var showAll: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memo_list)

        findViewById<ImageView>(R.id.btnAiOptions).setOnClickListener {
            startActivity(Intent(this, AiOptionsActivity::class.java))
        }

        recyclerView = findViewById(R.id.recyclerViewMemos)
        recyclerView.layoutManager = LinearLayoutManager(this)
        btnToggleAll = findViewById(R.id.btnToggleAllMemos)

        btnToggleAll.setOnClickListener {
            showAll = !showAll
            updateButtonText()
            applyFilter()
        }

        loadMemos()
        updateButtonText()
        applyFilter()
    }

    private fun loadMemos() {
        val memoFile = StorageHelper.getMemoFile(applicationContext)
        allMemos = if (memoFile.exists()) {
            memoFile.readText().split("\n\n").filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    private fun updateButtonText() {
        btnToggleAll.text = getString(
            if (showAll) R.string.memo_show_important else R.string.memo_show_all
        )
    }

    /** 根据 showAll 状态过滤列表 */
    private fun applyFilter() {
        val filtered = if (showAll) {
            allMemos
        } else {
            allMemos.filter { !isLowImportance(it) }
        }
        recyclerView.adapter = MemoAdapter(filtered)
    }

    /**
     * 判断一条备忘是否为「低」重要性。
     * 无重要性字段时视为「中」（不过滤）。
     */
    private fun isLowImportance(memoText: String): Boolean {
        val line = memoText.split("\n").find { it.startsWith("重要性:") } ?: return false
        val value = line.substringAfter(":").trim()
        return value == "低"
    }
}
