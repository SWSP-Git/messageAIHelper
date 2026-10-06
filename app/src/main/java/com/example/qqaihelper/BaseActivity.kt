package com.example.qqaihelper

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 所有 Activity 的基类，统一处理页面切换动画。
 *
 * 动效设计：
 * - 打开新页面时：新页面从右滑入 + 淡入，旧页面向左滑出 + 淡出
 * - 返回时：反向 —— 前页从左侧滑入，当前页向右滑出
 *
 * 兼容性：
 * Android 14 (API 34) 起 overridePendingTransition 被废弃，
 * 改用 overrideActivityTransition；以下版本继续用旧 API。
 */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOpenTransition()
    }

    override fun finish() {
        super.finish()
        applyCloseTransition()
    }

    private fun applyOpenTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.activity_enter,
                R.anim.activity_exit
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.activity_enter, R.anim.activity_exit)
        }
    }

    private fun applyCloseTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.activity_pop_enter,
                R.anim.activity_pop_exit
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.activity_pop_enter, R.anim.activity_pop_exit)
        }
    }
}
