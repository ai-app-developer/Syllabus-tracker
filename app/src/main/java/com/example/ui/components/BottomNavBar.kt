package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmbientShadow
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletGradientBottom
import com.example.ui.theme.VioletGradientTop

enum class BottomTab {
  HOME,
  BOOKS,
  PROGRESS,
  MORE,
}

@Composable
fun SyllabusBottomBar(
  selectedTab: BottomTab,
  onSelectTab: (BottomTab) -> Unit,
  onAddClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
    contentAlignment = Alignment.BottomCenter,
  ) {
    val barShape = RoundedCornerShape(28.dp)
    Surface(
      modifier =
        Modifier.fillMaxWidth()
          .height(64.dp)
          .shadow(
            elevation = 12.dp,
            shape = barShape,
            clip = false,
            ambientColor = AmbientShadow,
            spotColor = AmbientShadow,
          ),
      shape = barShape,
      color = CardSurface,
      border = BorderStroke(1.dp, HairlineBorder),
    ) {}

    Row(
      modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BottomNavItem(
        label = stringResource(R.string.nav_home),
        selected = selectedTab == BottomTab.HOME,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home",
        onClick = { onSelectTab(BottomTab.HOME) },
        modifier = Modifier.weight(1f),
      )
      BottomNavItem(
        label = stringResource(R.string.nav_books),
        selected = selectedTab == BottomTab.BOOKS,
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        testTag = "nav_books",
        onClick = { onSelectTab(BottomTab.BOOKS) },
        modifier = Modifier.weight(1f),
      )

      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          onClick = onAddClick,
          shape = CircleShape,
          color = Color.Transparent,
          modifier =
            Modifier.offset(y = (-14).dp)
              .size(56.dp)
              .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = AmbientShadow,
                spotColor = AmbientShadow,
              )
              .testTag("nav_add_chapter"),
        ) {
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(VioletGradientTop, VioletGradientBottom)
                  )
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = stringResource(R.string.add_chapter),
              tint = Color.White,
              modifier = Modifier.size(28.dp),
            )
          }
        }
      }

      BottomNavItem(
        label = stringResource(R.string.nav_progress),
        selected = selectedTab == BottomTab.PROGRESS,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_progress",
        onClick = { onSelectTab(BottomTab.PROGRESS) },
        modifier = Modifier.weight(1f),
      )
      BottomNavItem(
        label = stringResource(R.string.nav_more),
        selected = selectedTab == BottomTab.MORE,
        selectedIcon = Icons.Filled.GridView,
        unselectedIcon = Icons.Outlined.GridView,
        testTag = "nav_more",
        onClick = { onSelectTab(BottomTab.MORE) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun BottomNavItem(
  label: String,
  selected: Boolean,
  selectedIcon: ImageVector,
  unselectedIcon: ImageVector,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tint = if (selected) PrimaryIndigo else TextSecondary
  val interactionSource = remember { MutableInteractionSource() }
  Column(
    modifier =
      modifier
        .height(56.dp)
        .clip(MaterialTheme.shapes.small)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true),
          role = Role.Tab,
          onClick = onClick,
        )
        .testTag(testTag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = if (selected) selectedIcon else unselectedIcon,
      contentDescription = label,
      tint = tint,
      modifier = Modifier.size(23.dp),
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
      text = label,
      color = tint,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      maxLines = 1,
    )
  }
}
