package com.example.qqaihelper

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 备忘录列表的 RecyclerView 适配器。
 *
 * 数据格式（空行分隔多条目）：
 *   来源: QQ
 *   时间: 2026-10-06 12:00:00
 *   重要性: 高 | 中 | 低
 *   摘要: xxx
 *   关键信息: xxx
 *   智能建议: xxx
 *   内容: xxx
 *
 * 重要性视觉标识：
 * - 高（红底白字）
 * - 中（橙底白字）
 * - 低（灰底白字）
 *
 * ⚠️ 数据文件中的字段前缀（"来源:" 等）为固定中文，不随 UI 语言变化，
 * UI 展示的标签通过 strings.xml 做翻译。
 */
class MemoAdapter(private val memoList: List<String>) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

    class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSource: TextView = view.findViewById(R.id.tvSource)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvImportance: TextView = view.findViewById(R.id.tvImportance)
        val tvKeyInfo: TextView = view.findViewById(R.id.tvKeyInfo)
        val tvSuggestion: TextView = view.findViewById(R.id.tvSuggestion)
        val tvSummary: TextView = view.findViewById(R.id.tvSummary)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memo, parent, false)
        return MemoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val ctx = holder.itemView.context
        val lines = memoList[position].split("\n")

        holder.tvSource.text = ctx.getString(
            R.string.memo_from,
            extract(lines, "来源") ?: ctx.getString(R.string.memo_unknown)
        )
        // 发送人：AI 提取到时显示，未提取到时隐藏
        val sender = extract(lines, MemoParser.FIELD_SENDER)
        if (!sender.isNullOrBlank()) {
            holder.tvSender.text = ctx.getString(R.string.memo_sender, sender)
            holder.tvSender.visibility = View.VISIBLE
        } else {
            holder.tvSender.visibility = View.GONE
        }

        holder.tvTime.text = extract(lines, "时间") ?: ""
        holder.tvSummary.text = ctx.getString(
            R.string.memo_summary,
            extract(lines, "摘要") ?: ctx.getString(R.string.memo_none)
        )
        // 「内容」是多行字段（标题 + 正文），需用 extractMultiline 取完整原文
        holder.tvContent.text = ctx.getString(
            R.string.memo_content,
            MemoParser.extractMultiline(lines, "内容") ?: ""
        )

        // 重要性标签（高/中/低，带背景色）
        val importance = extract(lines, "重要性") ?: "中"
        bindImportance(holder.tvImportance, ctx, importance)

        val keyInfo = extract(lines, "关键信息")
        if (!keyInfo.isNullOrBlank() && keyInfo != "无") {
            holder.tvKeyInfo.text = ctx.getString(R.string.memo_key_info, keyInfo)
            holder.tvKeyInfo.visibility = View.VISIBLE
        } else {
            holder.tvKeyInfo.visibility = View.GONE
        }

        val suggestion = extract(lines, "智能建议")
        if (!suggestion.isNullOrBlank() && suggestion != "无") {
            holder.tvSuggestion.text = ctx.getString(R.string.memo_suggestion, suggestion)
            holder.tvSuggestion.visibility = View.VISIBLE
        } else {
            holder.tvSuggestion.visibility = View.GONE
        }
    }

    override fun getItemCount() = memoList.size

    /**
     * 绑定重要性标签：设置文字、颜色、背景。
     * 高 → 红；中 → 橙；低 → 灰
     */
    private fun bindImportance(view: TextView, ctx: android.content.Context, importance: String) {
        val (labelRes, bgColor) = when (importance.trim()) {
            "高" -> R.string.memo_importance_high to "#D32F2F"
            "低" -> R.string.memo_importance_low to "#9E9E9E"
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

    private fun extract(lines: List<String>, prefix: String): String? =
        MemoParser.extract(lines, prefix)
}
