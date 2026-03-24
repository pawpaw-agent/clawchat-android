package com.openclaw.clawchat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 配对底部抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingBottomSheet(
    onDismiss: () -> Unit,
    onPairingSuccess: () -> Unit
) {
    var gatewayUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "连接到 Gateway",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Gateway URL 输入
            OutlinedTextField(
                value = gatewayUrl,
                onValueChange = { gatewayUrl = it },
                label = { Text("Gateway URL") },
                placeholder = { Text("例如：http://192.168.1.100:18789") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 状态显示
            if (isLoading) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("正在连接...")
                }
            }
            
            error?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                
                Button(
                    onClick = {
                        isLoading = true
                        error = null
                        // TODO: 实现实际连接逻辑
                        // 暂时模拟成功
                        onPairingSuccess()
                    },
                    enabled = gatewayUrl.isNotBlank() && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("连接")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}