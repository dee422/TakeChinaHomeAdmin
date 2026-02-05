package com.dee.android.pbl.takechinahome.admin.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dee.android.pbl.takechinahome.admin.data.model.Order
import java.io.File
import java.io.FileOutputStream
import android.view.View // 确保可以访问 View 相关属性
import android.view.View.MeasureSpec // 修复 image_dc3fe4.jpg 中的 MeasureSpec 报错

class ScrollGenerator(private val context: Context) {

    fun generateFormalScroll(order: Order, onComplete: (File) -> Unit) {
        val webView = WebView(context)
        // 必须开启 JS 以便注入数据
        webView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // 1. 注入数据
                val js = "setData('${order.id}', '${order.targetGiftName}', '${order.targetQty}', '${order.deliveryDate}', '${order.contactName}', '${order.managerName}')"
                view.evaluateJavascript(js) {
                    // 2. 给一点渲染时间后截图
                    view.postDelayed({
                        val bitmap = captureWebView(view)
                        val file = saveBitmapToFile(bitmap, "order_${order.id}_formal.jpg")
                        onComplete(file)
                    }, 500) // 延迟500ms确保图片/字体加载完成
                }
            }
        }

        // 加载本地模板
        webView.loadUrl("file:///android_asset/formal_template.html")
    }

    private fun captureWebView(webView: WebView): Bitmap {
        // ✨ 使用全路径引用，彻底无视 Import 报错
        webView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            if (webView.measuredWidth > 0) webView.measuredWidth else 400, // 兜底宽度
            if (webView.measuredHeight > 0) webView.measuredHeight else 600, // 兜底高度
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        webView.draw(canvas)
        return bitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File {
        val file = File(context.getExternalFilesDir("formal_orders"), fileName)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        return file
    }
}