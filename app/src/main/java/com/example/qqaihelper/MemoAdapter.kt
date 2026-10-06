package com.example.qqaihelper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 备忘录列表的 RecyclerView 适配器。
 *
 * 输入：字符串列表，每个元素是一条完整的备忘文本（多行）。
 * 输出：卡片式列表项（item_memo.xml 使用 CardView 实现圆角矩形背景）。
 *
 * 单条备忘文本的格式（空行分隔多条目）：
 *   来源: QQ
 *   时间: 2026-10-06 12:00:00
 *   摘要: 快递取件码通知
 *   关键信息: 5621
 *   智能建议: 记得今天下班前去取件
 *   内容: 有一件快递到了取件码是5621
 *
 * 字段说明：
 * - 关键信息：红字加粗显示，通常是验证码/取件码/账号等需要一眼看到的数据
 * - 智能建议：蓝字显示，AI 生成的下一步行动建议
 *
 * 容错：任何字段缺失或格式异常都不会导致崩溃，UI 会隐藏对应的行。
 */
class MemoAdapter(private val memoList: List<String>) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

    class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSource: TextView = view.findViewById(R.id.tvSource)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
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
        val lines = memoList[position].split("\n")

        // 基础字段（缺失时给默认值，不隐藏）
        holder.tvSource.text = "来自：${extract(lines, "来源") ?: "未知"}"
        holder.tvTime.text = extract(lines, "时间") ?: ""
        holder.tvSummary.text = "摘要：${extract(lines, "摘要") ?: "无"}"
        holder.tvContent.text = "原文：${extract(lines, "内容") ?: ""}"

        // 关键信息：有内容且不是"无"才显示（红字加粗）
        val keyInfo = extract(lines, "关键信息")
        if (!keyInfo.isNullOrBlank() && keyInfo != "无") {
            holder.tvKeyInfo.text = "🔑 $keyInfo"
            holder.tvKeyInfo.visibility = View.VISIBLE
        } else {
            holder.tvKeyInfo.visibility = View.GONE
        }

        // 智能建议：有内容且不是"无"才显示（蓝字）
        val suggestion = extract(lines, "智能建议")
        if (!suggestion.isNullOrBlank() && suggestion != "无") {
            holder.tvSuggestion.text = "【AI建议】$suggestion"
            holder.tvSuggestion.visibility = View.VISIBLE
        } else {
            holder.tvSuggestion.visibility = View.GONE
        }
    }

    override fun getItemCount() = memoList.size

    /**
     * 从多行文本中按前缀提取字段值。
     * 例如 extract(lines, "关键信息") 会找 "关键信息: xxx" 这一行并返回 "xxx"。
     *
     * @param lines  备忘文本按行拆分后的数组
     * @param prefix 字段名前缀（不含冒号）
     * @return 字段值（已 trim），找不到返回 null
     */
    private fun extract(lines: List<String>, prefix: String): String? =
        lines.find { it.startsWith("$prefix:") }
            ?.substringAfter(":")
            ?.trim()
}
