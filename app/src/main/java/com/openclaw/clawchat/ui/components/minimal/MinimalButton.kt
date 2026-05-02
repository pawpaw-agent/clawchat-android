package com.openclaw.clawchat.ui.components.minimal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openclaw.clawchat.ui.theme.MinimalTokens

@Composable
fun MinimalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(MinimalTokens.inputBarHeight),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(MinimalTokens.radiusMd),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = MinimalTokens.elevationNone),
        contentPadding = PaddingValues(horizontal = MinimalTokens.space4)
    ) {
        if (icon != null) {
            icon()
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(MinimalTokens.space2))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun MinimalTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(MinimalTokens.radiusSm),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = MinimalTokens.elevationNone),
        contentPadding = PaddingValues(horizontal = MinimalTokens.space3)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}