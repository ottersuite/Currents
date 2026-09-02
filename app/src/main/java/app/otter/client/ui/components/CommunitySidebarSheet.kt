package app.otter.client.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.otter.client.model.CommunitySidebar
import app.otter.client.ui.theme.otterColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitySidebarSheet(
    communityName: String,
    sidebar: CommunitySidebar?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            Text(
                sidebar?.title ?: "r/$communityName",
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "r/${sidebar?.communityName ?: communityName}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (loading) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp),
                )
            } else if (sidebar != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Text(
                        "${compactNumber(sidebar.memberCount)} members",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (sidebar.activeUserCount > 0) {
                        Text(
                            "${compactNumber(sidebar.activeUserCount)} online",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                sidebar.description.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 20.dp))
                Text(
                    "RULES",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (sidebar.rules.isEmpty()) {
                    Text(
                        "No rules were published through Reddit.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                } else {
                    sidebar.rules.forEachIndexed { index, rule ->
                        Text(
                            "${index + 1}. ${rule.title}",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 15.dp),
                        )
                        rule.description.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
