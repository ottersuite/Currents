package app.otter.client.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.otter.client.ui.theme.otterColors

data class ActionBarItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val emphasized: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun GlassActionBar(
    items: List<ActionBarItem>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    Surface(
        modifier = modifier.shadow(18.dp, RoundedCornerShape(28.dp), clip = false),
        color = colors.surfaceGlass,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .11f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item -> GlassAction(item) }
        }
    }
}

@Composable
private fun RowScope.GlassAction(item: ActionBarItem) {
    val colors = MaterialTheme.otterColors
    val tint by animateColorAsState(
        targetValue = when {
            item.emphasized -> Color.White
            item.selected -> colors.accent
            else -> colors.textSecondary
        },
        label = "action tint",
    )
    val background by animateColorAsState(
        targetValue = when {
            item.emphasized -> colors.accent
            item.selected -> colors.accent.copy(alpha = .14f)
            else -> Color.Transparent
        },
        label = "action background",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(50.dp)
            .background(background, CircleShape)
            .then(
                if (item.emphasized) {
                    Modifier.border(1.dp, Color.White.copy(alpha = .18f), CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = item.label,
                onClick = item.onClick,
            ),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
