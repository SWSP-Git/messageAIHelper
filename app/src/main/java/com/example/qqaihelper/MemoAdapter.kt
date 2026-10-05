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
 * 每条备忘文本的格式：
 *   来源: QQ
 *   时间: 2026-10-06 12:00:00
 *   摘要: xxx
 *   内容: xxx
 *
 * 这里没有做严格的字段校验，任何格式异常都退化成「未知」或空字符串，
 * 保证列表渲染永不崩溃。
 */
class MemoAdapter(private val memoList: List<String>) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

    /**
     * ViewHolder 缓存单个列表项的控件引用，避免重复 findViewById。
     */
    class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSource: TextView = view.findViewById(R.id.tvSource)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvSummary: TextView = view.findViewById(R.id.tvSummary)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memo, parent, false)
        return MemoViewHolder(view)
    }

    /**
     * 将一条备忘文本绑定到 ViewHolder。
     * 通过行前缀（"来源:"、"时间:" 等）从多行文本中提取字段。
     */
    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val memoText = memoList[position]
        val lines = memoText.split("\n")

        // 按前缀查找对应字段，找不到时给默认值
        holder.tvSource.text = lines.find { it.startsWith("来源:") }?.replace("来源:", "")?.trim() ?: "未知"
        holder.tvTime.text = lines.find { it.startsWith("时间:") }?.replace("时间:", "")?.trim() ?: ""
        holder.tvSummary.text = lines.find { it.startsWith("摘要:") }?.replace("摘要:", "")?.trim() ?: ""
        holder.tvContent.text = lines.find { it.startsWith("内容:") }?.replace("内容:", "")?.trim() ?: ""
    }

    override fun getItemCount() = memoList.size
}
