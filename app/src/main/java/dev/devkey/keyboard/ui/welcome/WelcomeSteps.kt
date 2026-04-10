package dev.devkey.keyboard.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

@Composable
internal fun WelcomeScreen(
    isEnabled: Boolean,
    isDefault: Boolean,
    onEnableClick: () -> Unit,
    onSetDefaultClick: () -> Unit,
    onLanguagesClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevKeyThemeColors.kbBg)
            .padding(DevKeyThemeDimensions.welcomeScreenPad),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.welcomeSpacerLg))

        // Title
        Text(
            text = "DevKey",
            color = DevKeyThemeColors.keyText,
            fontSize = DevKeyThemeTypography.fontWelcomeTitle,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.welcomeSpacerSm))
        Text(
            text = "Power-user keyboard for Android",
            color = DevKeyThemeColors.settingsDescriptionColor,
            fontSize = DevKeyThemeTypography.fontWelcomeSubtitle
        )

        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.welcomeSpacerLg))

        // Setup steps
        SetupStep(
            stepNumber = 1,
            title = "Enable DevKey",
            description = "Turn on DevKey in your device's keyboard settings",
            isComplete = isEnabled,
            onClick = onEnableClick
        )

        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.welcomeSpacerMd))

        SetupStep(
            stepNumber = 2,
            title = "Set as Default",
            description = "Choose DevKey as your active keyboard",
            isComplete = isDefault,
            onClick = onSetDefaultClick
        )

        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.welcomeSpacerMd))

        SetupStep(
            stepNumber = 3,
            title = "Languages (Optional)",
            description = "Configure additional input languages",
            isComplete = false,
            isOptional = true,
            onClick = onLanguagesClick
        )

        Spacer(modifier = Modifier.weight(1f))

        // Settings button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DevKeyThemeDimensions.welcomeCardRadius))
                .background(DevKeyThemeColors.keyBg)
                .clickable { onSettingsClick() }
                .padding(vertical = DevKeyThemeDimensions.welcomeButtonPadV),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Settings",
                color = DevKeyThemeColors.keyText,
                fontSize = DevKeyThemeTypography.fontWelcomeSubtitle,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.welcomeSpacerXl))
    }
}

@Composable
internal fun SetupStep(
    stepNumber: Int,
    title: String,
    description: String,
    isComplete: Boolean,
    isOptional: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DevKeyThemeDimensions.welcomeCardRadius))
            .background(DevKeyThemeColors.keyBg)
            .clickable { onClick() }
            .padding(DevKeyThemeDimensions.welcomeCardPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(DevKeyThemeDimensions.welcomeStepIndicatorSize)
                .clip(CircleShape)
                .background(
                    if (isComplete) DevKeyThemeColors.setupCompleteGreen else DevKeyThemeColors.kbBg
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isComplete) "\u2713" else stepNumber.toString(),
                color = if (isComplete) DevKeyThemeColors.setupCompleteText else DevKeyThemeColors.settingsDescriptionColor,
                fontSize = DevKeyThemeTypography.fontWelcomeStepIndicator,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(DevKeyThemeDimensions.welcomeStepTextSpacerW))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.welcomeTagSpacing)
            ) {
                Text(
                    text = title,
                    color = DevKeyThemeColors.keyText,
                    fontSize = DevKeyThemeTypography.fontWelcomeSubtitle,
                    fontWeight = FontWeight.Medium
                )
                if (isOptional) {
                    Text(
                        text = "Optional",
                        color = DevKeyThemeColors.settingsDescriptionColor,
                        fontSize = DevKeyThemeTypography.fontSettingsSubtitle
                    )
                }
            }
            Text(
                text = description,
                color = DevKeyThemeColors.settingsDescriptionColor,
                fontSize = DevKeyThemeTypography.fontWelcomeDescription
            )
        }

        // Arrow indicator
        Text(
            text = "\u203A",
            color = DevKeyThemeColors.settingsDescriptionColor,
            fontSize = DevKeyThemeTypography.fontWelcomeArrow
        )
    }
}
