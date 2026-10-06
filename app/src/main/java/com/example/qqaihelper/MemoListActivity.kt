package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
 *
 * 交互：
 * - 单击卡片：切换展开/折叠详情（多选态下则为切换选中）
 * - 长按卡片：进入多选模式
 * - 多选模式下可全选 / 分享 / 删除 / 取消
 */
class MemoListActivity : BaseActivity(), MemoAdapter.Listener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnToggleAll: Button
    private lateinit var llSelectionBar: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var adapter: MemoAdapter

    /** 所有备忘（未过滤） */
    private var allMemos: List<String> = emptyList()

    /** 当前显示的备忘（过滤后） */
    private var visibleMemos: List<String> = emptyList()

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
        llSelectionBar = findViewById(R.id.llSelectionBar)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)

        btnToggleAll.setOnClickListener {
            showAll = !showAll
            updateButtonText()
            applyFilter()
        }

        // ---- 多选操作栏 ----
        btnSelectAll.setOnClickListener {
            val selectAll = !adapter.isAllSelected()
            adapter.setAllSelected(selectAll)
            btnSelectAll.text = getString(
                if (selectAll) R.string.memo_deselect_all else R.string.memo_select_all
            )
            updateSelectionBar()
        }
        findViewById<Button>(R.id.btnShareSelected).setOnClickListener { shareSelected() }
        findViewById<Button>(R.id.btnDeleteSelected).setOnClickListener { confirmDeleteSelected() }
        findViewById<Button>(R.id.btnCancelSelect).setOnClickListener { exitSelectionMode() }
    }

    override fun onResume() {
        super.onResume()
        loadMemos()
        updateButtonText()
        applyFilter()
    }

    private fun loadMemos() {
        val memoFile = StorageHelper.getMemoFile(applicationContext)
        allMemos = if (memoFile.exists()) {
            MemoParser.splitMemos(memoFile.readText())
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
        visibleMemos = MemoParser.filter(allMemos, showAll)
        adapter = MemoAdapter(visibleMemos, this)
        recyclerView.adapter = adapter
    }

    // ==================== 卡片交互 ====================

    override fun onMemoClick(position: Int) {
        if (adapter.selectionMode) {
            adapter.toggleSelection(position)
            updateSelectionBar()
        } else {
            adapter.toggleExpand(position)
        }
    }

    override fun onMemoLongClick(position: Int): Boolean {
        if (!adapter.selectionMode) {
            adapter.enterSelectionMode()
            adapter.toggleSelection(position)
            updateSelectionBar()
        }
        return true
    }

    // ==================== 多选操作 ====================

    private fun updateSelectionBar() {
        val count = adapter.selectedCount()
        if (count == 0) {
            llSelectionBar.visibility = View.GONE
            btnToggleAll.visibility = View.VISIBLE
        } else {
            llSelectionBar.visibility = View.VISIBLE
            btnToggleAll.visibility = View.GONE
            tvSelectionCount.text = getString(R.string.memo_selected_count, count)
        }
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        llSelectionBar.visibility = View.GONE
        btnToggleAll.visibility = View.VISIBLE
    }

    private fun shareSelected() {
        val memos = adapter.selectedMemos()
        if (memos.isEmpty()) return
        val text = memos.joinToString("\n\n----------\n\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.memo_share_title)))
    }

    private fun confirmDeleteSelected() {
        val count = adapter.selectedCount()
        if (count == 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.memo_delete_confirm_title)
            .setMessage(getString(R.string.memo_delete_confirm_msg, count))
            .setPositiveButton(R.string.memo_delete_selected) { _, _ -> deleteSelected() }
            .setNegativeButton(R.string.memo_cancel_select, null)
            .show()
    }

    private fun deleteSelected() {
        val toDelete = adapter.selectedMemos().toSet()
        if (toDelete.isEmpty()) return
        val remaining = allMemos.filterNot { toDelete.contains(it) }
        writeMemos(remaining)
        val deletedCount = allMemos.size - remaining.size
        allMemos = remaining
        exitSelectionMode()
        applyFilter()
        Toast.makeText(this, getString(R.string.memo_deleted_toast, deletedCount), Toast.LENGTH_SHORT).show()
    }

    /** 把备忘列表重写回文件（保留原有存储路径策略） */
    private fun writeMemos(memos: List<String>) {
        try {
            val memoFile = StorageHelper.getMemoFile(applicationContext)
            val content = if (memos.isEmpty()) "" else memos.joinToString("\n\n") + "\n\n"
            memoFile.writeText(content)
            AppLogger.d("✅ 备忘录已更新，剩余 " + memos.size + " 条")
        } catch (e: Exception) {
            AppLogger.e("写入备忘录失败: " + e.message)
            Toast.makeText(this, getString(R.string.memo_deleted_toast, 0), Toast.LENGTH_SHORT).show()
        }
    }
}
