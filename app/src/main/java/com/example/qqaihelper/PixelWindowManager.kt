package com.example.qqaihelper

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 1 像素透明悬浮窗管理器。
 *
 * 原理：
 * Android 系统中，拥有「可见窗口」的进程优先级会明显高于纯后台进程。
 * 我们创建一个 1x1 像素的透明悬浮窗，占据屏幕左上角，视觉上完全不可见，
 * 但让系统认为 App 有前台 UI，从而大幅降低被回收的概率。
 *
 * 这是一个业内常见的「保活技巧」，多个知名 App 都有类似实现。
 *
 * 注意：
 * - 需要 SYSTEM_ALERT_WINDOW 权限（悬浮窗权限）
 * - Android 8.0+ 使用 TYPE_APPLICATION_OVERLAY
 * - 使用 object 单例模式，全局只有一个悬浮窗
 */
object PixelWindowManager {

    private var pixelView: View? = null
    private var windowManager: WindowManager? = null

    /**
     * 显示 1 像素悬浮窗。
     * 若已显示则直接返回，不重复创建。
     */
    fun show(context: Context) {
        if (pixelView != null) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 一个空的、透明的小 View
        val view = View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 悬浮窗类型：Android 8.0 起必须用 TYPE_APPLICATION_OVERLAY
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            1, 1, // 宽高均为 1 像素
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 固定在左上角
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            wm.addView(view, params)
            pixelView = view
            windowManager = wm
            AppLogger.d("✅ 1 像素悬浮窗已启动")
        } catch (e: Exception) {
            AppLogger.e("悬浮窗启动失败: ${e.message}")
        }
    }

    /** 关闭悬浮窗 */
    fun hide() {
        val view = pixelView ?: return
        try {
            windowManager?.removeView(view)
            AppLogger.d("⏸️ 1 像素悬浮窗已关闭")
        } catch (e: Exception) {
            AppLogger.e("悬浮窗关闭失败: ${e.message}")
        } finally {
            pixelView = null
            windowManager = null
        }
    }

    /** 当前是否已显示 */
    fun isShowing(): Boolean = pixelView != null
}
