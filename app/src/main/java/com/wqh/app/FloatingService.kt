package com.wqh.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: MyOverlayView

    private var startX = 0f
    private var startY = 0f
    private val touchSlop = 15f // 滑动判定阈值
    private var actionDownTime = 0L

    private var simulatedFlag = false

    override fun onCreate() {
        super.onCreate()

        floatingView = MyOverlayView(this)
        floatingView.setBackgroundColor(Color.TRANSPARENT) // 完全透明
//        floatingView.setBackgroundColor(0x22000000) // 半透明
        /*floatingView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke(1, Color.BLACK)  // 边框
        }*/

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            width = 160 // 宽度：边缘触发区域
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN /*or //全屏
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS //全屏*/
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        floatingView.setOnTouchListener { v, e ->
            if (simulatedFlag) {
                floatingView.setBackgroundColor(Color.BLACK)
                Handler(Looper.getMainLooper()).postDelayed({floatingView.setBackgroundColor(Color.TRANSPARENT)}, 500)
                return@setOnTouchListener true
            }
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX
                    startY = e.rawY
                    actionDownTime = SystemClock.uptimeMillis()
                }

                MotionEvent.ACTION_UP -> {
                    val dx = abs(e.rawX - startX)
                    val dy = abs(e.rawY - startY)
                    if (SystemClock.uptimeMillis() - actionDownTime < 200 && dx < touchSlop && dy < touchSlop) {
                        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        windowManager.updateViewLayout(floatingView, params)

                        //发送点击事件结束后恢复悬浮窗
                        Handler(Looper.getMainLooper()).postDelayed({
                            simulatedFlag = true
                            MyAccessibilityService.instance?.dispatchGesture(
                                GestureDescription.Builder()
                                    .addStroke(GestureDescription.StrokeDescription(
                                        Path().apply { moveTo(e.rawX, e.rawY) }, 0, 50)).build(),
                                object : AccessibilityService.GestureResultCallback() { // Kotlin 中匿名类使用 object 关键字
                                    override fun onCompleted(gestureDescription: GestureDescription?) {
                                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                        windowManager.updateViewLayout(floatingView, params)
                                        simulatedFlag = false
                                    }
                                }, null
                            )}, 50)
                        floatingView.performClick()
                    } else if(dy > touchSlop && dy > dx) {
                        MyAccessibilityService.instance?.scrollApp(e.rawY <= startY)
Toast.makeText(this, "无障碍实例空？ " + (MyAccessibilityService.instance == null), Toast.LENGTH_SHORT).show()
                        MyAccessibilityService.instance?.appCfg?.apply {
                            floatingView.redraw(y1, if (!mark.isNaN()) mark else y2)
                        }
                    } else if (SystemClock.uptimeMillis() - actionDownTime >= 500L) {
                        copyTextToClipboard(MyAccessibilityService.instance?.currentClassName + "\n" + e.rawY.toInt())
                        floatingView.redraw(e.y, null)
                    }

                    /*if(abs(e.rawX - startX) > abs(e.rawY - startY)) {
                        return@setOnTouchListener true;
                    }
                    //滑动松开
                    if (abs(e.rawY - startY) >= threshold) {
                        MyAccessibilityService.instance?.scrollApp(e.rawY <= startY)

                        floatingView.redraw(MyAccessibilityService.instance?.appCfg?.y1, MyAccessibilityService.instance?.appCfg?.y2)

                        return@setOnTouchListener true;
                    }

                    //点击松开
                    if (SystemClock.uptimeMillis() - actionDownTime < 500L) {
                        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        windowManager.updateViewLayout(floatingView, params)

                        //发送点击事件结束后恢复悬浮窗
                        Handler(Looper.getMainLooper()).postDelayed({
                            simulatedFlag = true
                            MyAccessibilityService.instance?.dispatchGesture(
                                    GestureDescription.Builder()
                                        .addStroke(GestureDescription.StrokeDescription(
                                            Path().apply { moveTo(e.rawX, e.rawY) }, 0, 50)).build(),
                            object : AccessibilityService.GestureResultCallback() { // Kotlin 中匿名类使用 object 关键字
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                    windowManager.updateViewLayout(floatingView, params)
                                    simulatedFlag = false
                                }
                            }, null
                            )}, 60)


                        *//*Handler(Looper.getMainLooper()).postDelayed({
                           params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                           windowManager.updateViewLayout(floatingView, params)
                       }, 1000)*//*
                        floatingView.performClick()
                    } else { //长按松开
                        copyTextToClipboard(MyAccessibilityService.instance?.currentClassName + " " + e.rawY.toInt())
                        floatingView.redraw(e.y, null)
                    }*/
                }
            }

            return@setOnTouchListener true;
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingView)
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun copyTextToClipboard(textToCopy: String, label: String = "文本内容") {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(label, textToCopy)
        clipboardManager.setPrimaryClip(clipData)
    }
}
