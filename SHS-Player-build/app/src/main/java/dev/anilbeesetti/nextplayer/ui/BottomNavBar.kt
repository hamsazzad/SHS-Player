package dev.anilbeesetti.nextplayer.ui

  import androidx.compose.material3.Icon
  import androidx.compose.material3.NavigationBar
  import androidx.compose.material3.NavigationBarItem
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.res.painterResource
  import androidx.compose.ui.res.stringResource
  import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

  enum class BottomNavTab(val iconRes: Int, val labelRes: Int) {
      VIDEOS(coreUiR.drawable.ic_video, coreUiR.string.video),
      MUSIC(coreUiR.drawable.ic_music_note, coreUiR.string.music),
      ME(coreUiR.drawable.ic_person, coreUiR.string.me),
      TELEGRAM(coreUiR.drawable.ic_info, coreUiR.string.about_name),
  }

  @Composable
  fun BottomNavBar(
      currentTab: BottomNavTab,
      onTabSelected: (BottomNavTab) -> Unit,
  ) {
      NavigationBar {
          BottomNavTab.entries.forEach { tab ->
              NavigationBarItem(
                  selected = currentTab == tab,
                  onClick = { onTabSelected(tab) },
                  icon = {
                      Icon(
                          painter = painterResource(tab.iconRes),
                          contentDescription = stringResource(tab.labelRes),
                      )
                  },
                  label = { Text(stringResource(tab.labelRes)) },
              )
          }
      }
  }
  