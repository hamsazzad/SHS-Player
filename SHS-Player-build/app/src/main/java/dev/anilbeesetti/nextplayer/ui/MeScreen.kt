package dev.anilbeesetti.nextplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

sealed class MeDestination {
    object PrivacyFolder : MeDestination()
    object FileTransfer : MeDestination()
    object AboutUs : MeDestination()
    object Settings : MeDestination()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    modifier: Modifier = Modifier,
    onNavigate: (MeDestination) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Me") })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MeMenuCard(
                    icon = NextIcons.Lock,
                    title = "Privacy Folder",
                    subtitle = "Hide and protect your private files",
                    onClick = { onNavigate(MeDestination.PrivacyFolder) },
                )
            }
            item {
                MeMenuCard(
                    icon = NextIcons.Wifi,
                    title = "File Transfer",
                    subtitle = "Send and receive files on your local network",
                    onClick = { onNavigate(MeDestination.FileTransfer) },
                )
            }
            item {
                MeMenuCard(
                    icon = NextIcons.Info,
                    title = "About Us",
                    subtitle = "Developer info, social links and donations",
                    onClick = { onNavigate(MeDestination.AboutUs) },
                )
            }
            item {
                MeMenuCard(
                    icon = NextIcons.Settings,
                    title = "Settings",
                    subtitle = "Appearance, player, audio and more",
                    onClick = { onNavigate(MeDestination.Settings) },
                )
            }
        }
    }
}

@Composable
fun MeMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = NextIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
