package com.example.ui

import com.example.ui.theme.BottomBarPresets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * Reusable Animated Bottom Navigation Bar with a sliding floating circle (PDF Reader Pro style).
 */
@Composable
fun AnimatedBottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    barBackgroundColor: Color = MaterialTheme.colorScheme.surface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    unselectedContentColor: Color = Color(0xFF8E8E93)
) {
    val selectedIndex = remember(currentRoute, items) {
        val idx = items.indexOfFirst { it.route == currentRoute }
        if (idx >= 0) idx else 0
    }

    // Scale and vertical bounce animatables for jump effect
    val circleScale = remember { Animatable(1f) }
    val circleOffsetY = remember { Animatable(0f) }

    LaunchedEffect(selectedIndex) {
        launch {
            circleScale.animateTo(0.82f, animationSpec = tween(90))
            circleScale.animateTo(1.12f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            circleScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
        }
        launch {
            circleOffsetY.animateTo(-8f, animationSpec = tween(90))
            circleOffsetY.animateTo(0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val totalWidth = maxWidth
        val itemCount = items.size.coerceAtLeast(1)
        val itemWidth = totalWidth / itemCount
        val circleSize = 52.dp

        // Calculate horizontal position of floating circle
        val targetOffset = (itemWidth * selectedIndex) + ((itemWidth - circleSize) / 2)
        val animatedOffsetX by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "floating_circle_offset"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Background bar surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = barBackgroundColor,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .width(itemWidth)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onItemSelected(item.route) }
                                )
                                .testTag("nav_tab_${item.route}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (!isSelected) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = unselectedContentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = unselectedContentColor,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    // Empty space placeholder when item is floating above
                                    Spacer(modifier = Modifier.height(36.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Floating Animated Purple Circle
            Surface(
                modifier = Modifier
                    .offset(
                        x = animatedOffsetX,
                        y = (-14).dp + circleOffsetY.value.dp
                    )
                    .size(circleSize)
                    .scale(circleScale.value)
                    .align(Alignment.TopStart),
                shape = CircleShape,
                color = primaryColor,
                shadowElevation = 8.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Crossfade(
                        targetState = selectedIndex,
                        animationSpec = tween(200),
                        label = "floating_icon_crossfade"
                    ) { targetIdx ->
                        val currentItem = items.getOrNull(targetIdx) ?: items[0]
                        Icon(
                            imageVector = currentItem.selectedIcon,
                            contentDescription = currentItem.label,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convenience wrapper for DashboardTab enum routing in PDF ViewModel context.
 */
@Composable
fun DashboardAnimatedBottomNavBar(
    selectedTab: DashboardTab,
    showTools: Boolean,
    bottomBarColorIndex: Int,
    appLanguage: String = "ar",
    onTabSelected: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val preset = BottomBarPresets.getOrElse(bottomBarColorIndex) { BottomBarPresets[0] }

    val barColor = if (bottomBarColorIndex == 0) {
        MaterialTheme.colorScheme.surface
    } else {
        if (isDark) preset.darkBg else preset.lightBg
    }

    val primaryColor = if (bottomBarColorIndex == 0) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isDark) preset.darkOnSelected else preset.lightOnSelected
    }

    val unselectedColor = if (bottomBarColorIndex == 0) {
        Color(0xFF8E8E93)
    } else {
        if (isDark) preset.darkUnselected else preset.lightUnselected
    }

    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    val (homeLabel, docsLabel, toolsLabel, settingsLabel) = Quadruple(
        strings.home,
        strings.documents,
        strings.tools,
        strings.settings
    )

    val allNavItems = remember(showTools, appLanguage) {
        buildList {
            add(
                BottomNavItem(
                    route = DashboardTab.Home.name,
                    label = homeLabel,
                    icon = Icons.Outlined.Home,
                    selectedIcon = Icons.Filled.Home
                )
            )
            add(
                BottomNavItem(
                    route = DashboardTab.Folders.name,
                    label = docsLabel,
                    icon = Icons.Outlined.Description,
                    selectedIcon = Icons.Filled.Description
                )
            )
            if (showTools) {
                add(
                    BottomNavItem(
                        route = DashboardTab.Tools.name,
                        label = toolsLabel,
                        icon = Icons.Outlined.Widgets,
                        selectedIcon = Icons.Filled.Widgets
                    )
                )
            }
            add(
                BottomNavItem(
                    route = DashboardTab.Settings.name,
                    label = settingsLabel,
                    icon = Icons.Outlined.Settings,
                    selectedIcon = Icons.Filled.Settings
                )
            )
        }
    }

    AnimatedBottomNavBar(
        items = allNavItems,
        currentRoute = selectedTab.name,
        onItemSelected = { routeName ->
            val tab = DashboardTab.values().firstOrNull { it.name == routeName }
            if (tab != null) {
                onTabSelected(tab)
            }
        },
        modifier = modifier,
        barBackgroundColor = barColor,
        primaryColor = primaryColor,
        unselectedContentColor = unselectedColor
    )
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
