package com.dee.android.pbl.takechinahome.admin.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.view.View
import android.webkit.*
import com.dee.android.pbl.takechinahome.admin.data.model.Order
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper

class ScrollGenerator(private val context: Context) {

    fun generateFormalScroll(order: Order, onComplete: (File) -> Unit) {
        // 1. 开启全内容绘制支持
        WebView.enableSlowWholeDocumentDraw()

        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        // 开启软件渲染模式，这是后台抓取 Canvas 的关键
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: ""
                Log.d("AuditFlow", "JS信号: $msg")

                if (msg == "RENDER_COMPLETE") {
                    Log.d("AuditFlow", "收到信号，物理延迟 200ms 等待渲染落地...")

                    // 使用主线程 Handler 确保任务在连续点击时依然能稳定触发
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val file = executeCapture(webView, order.id)
                            onComplete(file)
                        } catch (e: Exception) {
                            Log.e("AuditFlow", "截图过程异常: ${e.message}")
                        }
                    }, 200)
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                Log.d("AuditFlow", "页面加载完成: ${order.id}")

                // 严格提取 6 个参数，处理单引号转义
                val id = order.id.toString()
                val gift = (order.targetGiftName ?: "待定").replace("'", "\\'")
                val qty = order.targetQty.toString()
                val date = (order.deliveryDate ?: "待定").replace("'", "\\'")
                val contact = (order.contactMethod ?: "待定").replace("'", "\\'")
                val manager = (order.managerName ?: "未分配").replace("'", "\\'")

                // 参数顺序必须严格对应 HTML 里的 setData 定义
                val js = "setData('$id', '$gift', '$qty', '$date', '$contact', '$manager')"
                Log.d("AuditFlow", "注入JS: $js")
                view.evaluateJavascript(js, null)
            }
        }

        // 预设初始尺寸
        webView.layout(0, 0, 1080, 2000)
        webView.loadUrl("file:///android_asset/formal_template.html")
    }

    private fun executeCapture(webView: WebView, orderId: Int): File {
        // 测量内容真实高度以支撑长截图
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val h = if (webView.measuredHeight <= 0) 2500 else webView.measuredHeight
        webView.layout(0, 0, 1080, h)

        Log.d("AuditFlow", ">>>>> 开始物理绘制 Bitmap: 1080 x $h <<<<<")

        val bitmap = Bitmap.createBitmap(1080, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE) // 强制白底

        webView.draw(canvas)

        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val folder = File(imagesDir, "TakeChinaHome/formal_orders")
        if (!folder.exists()) folder.mkdirs()

        val file = File(folder, "order_${orderId}_formal_${System.currentTimeMillis()}.jpg")

        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }

        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        Log.d("AuditFlow", "✅ 图片保存完成: ${file.absolutePath}")

        // 延迟释放内存，确保导出彻底完成
        Handler(Looper.getMainLooper()).postDelayed({
            if (!bitmap.isRecycled) bitmap.recycle()
        }, 1000)

        return file
    }
}