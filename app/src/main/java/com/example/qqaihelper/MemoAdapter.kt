package com.example.qqaihelper

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 备忘录列表的 RecyclerView 适配器。
 *
 * 数据格式（空行分隔多条目）：
 *   来源: QQ
 *   发送人: 张三
 *   分类: 日程 | 备忘
 *   时间: 2026-10-06 12:00:00
 *   重要性: 高 | 中 | 低
 *   摘要: xxx
 *   关键信息: xxx
 *   智能建议: xxx
 *   内容: xxx
 *
 * 交互：
 * - 单击卡片：在多选模式下切换选中；否则切换展开/折叠详情
 * - 长按卡片：进入多选模式（由 MemoListActivity 处理）
 *
 * 折叠态仅显示：重要性 / 发送人 / 时间 / 关键信息 / 智能建议 / 摘要
 * 展开态额外显示：来源 / 分类 / 原文
 *
 * ⚠️ 数据文件中的字段前缀（"来源:" 等）为固定中文，不随 UI 语言变化。
 */
class MemoAdapter(
    private val memoList: List<String>,
    private val listener: Listener? = null
) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

    /** 交互回调（由 Activity 实现） */
    interface Listener {
        /** 点击卡片 */
        fun onMemoClick(position: Int)
        /** 长按卡片，返回是否已处理 */
        fun onMemoLongClick(position: Int): Boolean
    }

    /** 是否处于多选模式 */
    var selectionMode: Boolean = false
        private set

    /** 已选中的备忘（以原始文本为键，抗列表变化） */
    private val selected = mutableSetOf<String>()

    /** 已展开的备忘（以原始文本为键） */
    private val expanded = mutableSetOf<String>()

    class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val tvSource: TextView = view.findViewById(R.id.tvSource)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvImportance: TextView = view.findViewById(R.id.tvImportance)
        val tvSummary: TextView = view.findViewById(R.id.tvSummary)
        val tvExpandHint: TextView = view.findViewById(R.id.tvExpandHint)
        val llDetail: View = view.findViewById(R.id.llDetail)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvKeyInfo: TextView = view.findViewById(R.id.tvKeyInfo)
        val tvSuggestion: TextView = view.findViewById(R.id.tvSuggestion)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memo, parent, false)
        return MemoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val ctx = holder.itemView.context
        val raw = memoList[position]
        val lines = raw.split("\n")

        // ===== 折叠态始终显示：重要性 / 发送人 / 时间 / 关键信息 / 智能建议 / 摘要 =====
        bindImportance(holder.tvImportance, ctx, MemoParser.importance(lines))

        val sender = MemoParser.extract(lines, MemoParser.FIELD_SENDER)
        if (!sender.isNullOrBlank()) {
            holder.tvSender.text = ctx.getString(R.string.memo_sender, sender)
            holder.tvSender.visibility = View.VISIBLE
        } else {
            holder.tvSender.visibility = View.GONE
        }

        holder.tvTime.text = MemoParser.extract(lines, MemoParser.FIELD_TIME) ?: ""

        val keyInfo = MemoParser.extract(lines, MemoParser.FIELD_KEY_INFO)
        if (!keyInfo.isNullOrBlank() && keyInfo != "无") {
            holder.tvKeyInfo.text = ctx.getString(R.string.memo_key_info, keyInfo)
            holder.tvKeyInfo.visibility = View.VISIBLE
        } else {
            holder.tvKeyInfo.visibility = View.GONE
        }

        val suggestion = MemoParser.extract(lines, MemoParser.FIELD_SUGGESTION)
        if (!suggestion.isNullOrBlank() && suggestion != "无") {
            holder.tvSuggestion.text = ctx.getString(R.string.memo_suggestion, suggestion)
            holder.tvSuggestion.visibility = View.VISIBLE
        } else {
            holder.tvSuggestion.visibility = View.GONE
        }

        // 摘要（折叠态始终显示）
        holder.tvSummary.text = ctx.getString(
            R.string.memo_summary,
            MemoParser.extract(lines, MemoParser.FIELD_SUMMARY) ?: ctx.getString(R.string.memo_none)
        )

        // ===== 详情区（展开时显示）：来源 / 分类 / 原文 =====
        val isExpanded = expanded.contains(raw)
        holder.llDetail.visibility = if (isExpanded) View.VISIBLE else View.GONE
        // 折叠态限 2 行；展开态取消限制，显示完整内容
        val foldMax = if (isExpanded) Int.MAX_VALUE else 2
        holder.tvSummary.maxLines = foldMax
        holder.tvSuggestion.maxLines = foldMax
        holder.tvExpandHint.text = ctx.getString(
            if (isExpanded) R.string.memo_collapse_hint else R.string.memo_expand_hint
        )

        if (isExpanded) {
            holder.tvSource.text = ctx.getString(
                R.string.memo_from,
                MemoParser.extract(lines, MemoParser.FIELD_SOURCE) ?: ctx.getString(R.string.memo_unknown)
            )
            holder.tvSource.visibility = View.VISIBLE

            val category = MemoParser.extract(lines, MemoParser.FIELD_CATEGORY)
            if (!category.isNullOrBlank()) {
                holder.tvCategory.text = ctx.getString(R.string.memo_category, category)
                holder.tvCategory.visibility = View.VISIBLE
            } else {
                holder.tvCategory.visibility = View.GONE
            }

            holder.tvContent.text = ctx.getString(
                R.string.memo_content,
                MemoParser.extractMultiline(lines, MemoParser.FIELD_CONTENT) ?: ""
            )
        }

        // ---- 多选态 ----
        holder.cbSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.cbSelect.isChecked = selected.contains(raw)

        holder.itemView.setOnClickListener { listener?.onMemoClick(position) }
        holder.itemView.setOnLongClickListener {
            listener?.onMemoLongClick(position) ?: false
        }
    }

    override fun getItemCount() = memoList.size

    // ==================== 状态控制（由 Activity 调用） ====================

    /** 进入多选模式 */
    fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        selected.clear()
        notifyDataSetChanged()
    }

    /** 退出多选模式 */
    fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selected.clear()
        notifyDataSetChanged()
    }

    /** 切换某条备忘的选中状态 */
    fun toggleSelection(position: Int) {
        val raw = memoList.getOrNull(position) ?: return
        if (!selected.add(raw)) selected.remove(raw)
        notifyItemChanged(position)
    }

    /** 全选 / 取消全选 */
    fun setAllSelected(selectAll: Boolean) {
        selected.clear()
        if (selectAll) selected.addAll(memoList)
        notifyDataSetChanged()
    }

    /** 已选数量 */
    fun selectedCount(): Int = selected.size

    /** 是否已全选 */
    fun isAllSelected(): Boolean = memoList.isNotEmpty() && selected.size == memoList.size

    /** 当前选中的备忘原始文本 */
    fun selectedMemos(): List<String> = memoList.filter { selected.contains(it) }

    /** 切换展开/折叠 */
    fun toggleExpand(position: Int) {
        val raw = memoList.getOrNull(position) ?: return
        if (!expanded.add(raw)) expanded.remove(raw)
        notifyItemChanged(position)
    }

    /**
     * 绑定重要性标签：设置文字、颜色、背景。
     * 高 → 红；中 → 橙；低 → 灰
     */
    private fun bindImportance(view: TextView, ctx: android.content.Context, importance: String) {
        val (labelRes, bgColor) = when (importance.trim()) {
            MemoParser.IMPORTANCE_HIGH -> R.string.memo_importance_high to "#D32F2F"
            MemoParser.IMPORTANCE_LOW -> R.string.memo_importance_low to "#9E9E9E"
            else -> R.string.memo_importance_medium to "#F57C00"
        }
        view.text = ctx.getString(labelRes)
        view.setTextColor(Color.WHITE)
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = 20f
        }
        view.background = drawable
    }
}
