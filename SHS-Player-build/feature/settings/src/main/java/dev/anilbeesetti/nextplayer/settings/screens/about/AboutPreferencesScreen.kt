package dev.anilbeesetti.nextplayer.settings.screens.about

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anilbeesetti.nextplayer.core.common.extensions.appIcon
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutPreferencesScreen(
    onLibrariesClick: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.about_name),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            AboutApp(
                onLibrariesClick = onLibrariesClick,
            )
        }
    }
}

@Composable
fun AboutApp(
    modifier: Modifier = Modifier,
    onLibrariesClick: () -> Unit,
) {
    val context = LocalContext.current
    val appVersion = remember { context.appVersion() }
    val appIcon = remember { context.appIcon()?.asImageBitmap() }

    val colorPrimary = MaterialTheme.colorScheme.primaryContainer
    val colorTertiary = MaterialTheme.colorScheme.tertiaryContainer

    val transition = rememberInfiniteTransition()
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val cornerRadius = 24.dp

    Column(
        modifier = modifier
            .padding(
                vertical = 16.dp,
                horizontal = 8.dp,
            )
            .drawWithCache {
                val cx = size.width - size.width * fraction
                val cy = size.height * fraction

                val gradient = Brush.radialGradient(
                    colors = listOf(colorPrimary, colorTertiary),
                    center = Offset(cx, cy),
                    radius = 800f,
                )

                onDrawBehind {
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(
                            cornerRadius.toPx(),
                            cornerRadius.toPx(),
                        ),
                    )
                }
            }
            .padding(all = 24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            appIcon?.let {
                Image(
                    bitmap = it,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
            }
            Column {
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onLibrariesClick,
                colors = ButtonDefaults.buttonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Text(text = stringResource(R.string.libraries))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        val ctx = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/aamoviesofficial"))
                    ctx.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    containerColor = androidx.compose.ui.graphics.Color(0xFF0088CC),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(text = "Join us on Telegram")
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:shsjadinfo@gmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, "SHS Player - Contact")
                    }
                    ctx.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(text = "Contact Us")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("bKash", "01310211442"))
                    android.widget.Toast.makeText(ctx, "bKash number copied!", android.widget.Toast.LENGTH_SHORT).show()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Support Development via bKash",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Personal: 01310211442",
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFFE91E8C),
            )
            Text(
                text = "(Tap to copy)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Context.appVersion(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)

    @Suppress("DEPRECATION")
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode
    }

    return "${packageInfo.versionName} ($versionCode)"
}

internal fun UriHandler.openUriOrShowToast(uri: String, context: Context) {
    try {
        openUri(uri = uri)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show()
    }
}
