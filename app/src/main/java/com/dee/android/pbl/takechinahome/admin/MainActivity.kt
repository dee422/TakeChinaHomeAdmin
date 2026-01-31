package com.dee.android.pbl.takechinahome.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dee.android.pbl.takechinahome.admin.ui.screens.AuditDashboardScreen
import com.dee.android.pbl.takechinahome.admin.ui.theme.TakeChinaHomeAdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 使用你项目生成的 Theme
            TakeChinaHomeAdminTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuditDashboardScreen()
                }
            }
        }
    }
}