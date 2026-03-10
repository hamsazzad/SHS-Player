package dev.anilbeesetti.nextplayer.ui

  import android.content.Intent
  import android.net.Uri
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.rememberScrollState
  import androidx.compose.foundation.verticalScroll
  import androidx.compose.material3.Button
  import androidx.compose.material3.ButtonDefaults
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.Icon
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.OutlinedButton
  import androidx.compose.material3.Text
  import androidx.compose.material3.TopAppBar
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.res.painterResource
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.unit.dp
  import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

  private const val FACEBOOK_URL = "https://www.facebook.com/profile.php?id=61580853950299"
  private const val TELEGRAM_URL = "https://t.me/aamoviesofficial"
  private const val EMAIL_ADDRESS = "jdvijay878@gmail.com"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun TelegramScreen(
      modifier: Modifier = Modifier,
  ) {
      val context = LocalContext.current

      Column(modifier = modifier.fillMaxSize()) {
          TopAppBar(
              title = { Text(stringResource(coreUiR.string.about_name)) },
          )

          Column(
              modifier = Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(24.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
              Icon(
                  painter = painterResource(coreUiR.drawable.ic_info),
                  contentDescription = null,
                  modifier = Modifier.size(80.dp),
                  tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                  text = "SHS Shobuj",
                  style = MaterialTheme.typography.headlineMedium,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                  text = "SHS Player Developer",
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(32.dp))
              Button(
                  onClick = {
                      runCatching {
                          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FACEBOOK_URL)))
                      }
                  },
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
              ) {
                  Icon(
                      painter = painterResource(coreUiR.drawable.ic_facebook),
                      contentDescription = null,
                      modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Facebook")
              }
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                  onClick = {
                      runCatching {
                          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
                      }
                  },
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE)),
              ) {
                  Icon(
                      painter = painterResource(coreUiR.drawable.ic_telegram),
                      contentDescription = null,
                      modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Telegram (@aamoviesofficial)")
              }
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedButton(
                  onClick = {
                      runCatching {
                          val intent = Intent(Intent.ACTION_SENDTO).apply {
                              data = Uri.parse("mailto:$EMAIL_ADDRESS")
                          }
                          context.startActivity(intent)
                      }
                  },
                  modifier = Modifier.fillMaxWidth(),
              ) {
                  Icon(
                      painter = painterResource(coreUiR.drawable.ic_email),
                      contentDescription = null,
                      modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(EMAIL_ADDRESS)
              }
          }
      }
  }
  