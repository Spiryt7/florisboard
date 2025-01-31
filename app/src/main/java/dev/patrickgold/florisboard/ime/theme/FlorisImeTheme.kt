/*
 * Copyright (C) 2021 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.text.key.InputMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.snygg.SnyggStylesheet

private val LocalConfig = staticCompositionLocalOf<ThemeExtensionConfig> { error("not init") }
private val LocalStyle = staticCompositionLocalOf<SnyggStylesheet> { error("not init") }

val FlorisImeThemeBaseStyle = SnyggStylesheet {
    FlorisImeUi.Keyboard {
        background = rgbaColor(33, 33, 33)
    }
    FlorisImeUi.Key {
        background = rgbaColor(66, 66, 66)
        foreground = rgbaColor(255, 255, 255)
        fontSize = size(22.sp)
        shape = roundedCornerShape(20)
    }
    FlorisImeUi.Key(pressedSelector = true) {
        background = rgbaColor(97, 97, 97)
        foreground = rgbaColor(255, 255, 255)
    }
    FlorisImeUi.Key(codes = listOf(KeyCode.ENTER)) {
        background = rgbaColor(76, 175, 80)
        foreground = rgbaColor(255, 255, 255)
    }
    FlorisImeUi.Key(codes = listOf(KeyCode.ENTER), pressedSelector = true) {
        background = rgbaColor(56, 142, 60)
        foreground = rgbaColor(255, 255, 255)
    }
    FlorisImeUi.Key(
        codes = listOf(KeyCode.SHIFT),
        modes = listOf(InputMode.CAPS_LOCK.value),
    ) {
        foreground = rgbaColor(255, 152, 0)
    }
    FlorisImeUi.Key(codes = listOf(KeyCode.SPACE)) {
        background = rgbaColor(66, 66, 66)
        foreground = rgbaColor(144, 144, 144)
        fontSize = size(12.sp)
    }
    FlorisImeUi.KeyHint {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(184, 184, 184)
        fontSize = size(12.sp)
    }
    FlorisImeUi.KeyPopup {
        background = rgbaColor(117, 117, 117)
        foreground = rgbaColor(255, 255, 255)
        fontSize = size(22.sp)
        shape = roundedCornerShape(20)
    }
    FlorisImeUi.KeyPopup(focusSelector = true) {
        background = rgbaColor(189, 189, 189)
        foreground = rgbaColor(255, 255, 255)
        fontSize = size(22.sp)
        shape = roundedCornerShape(20)
    }

    FlorisImeUi.ClipboardHeader {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(255, 255, 255)
        fontSize = size(16.sp)
    }
    FlorisImeUi.ClipboardItem {
        background = rgbaColor(66, 66, 66)
        foreground = rgbaColor(255, 255, 255)
        fontSize = size(14.sp)
        shape = roundedCornerShape(12.dp)
    }
    FlorisImeUi.ClipboardItemPopup {
        background = rgbaColor(117, 117, 117)
        foreground = rgbaColor(255, 255, 255)
        fontSize = size(14.sp)
        shape = roundedCornerShape(12.dp)
    }

    FlorisImeUi.OneHandedPanel {
        background = rgbaColor(27, 94, 32)
        foreground = rgbaColor(238, 238, 238)
    }

    FlorisImeUi.SmartbarPrimaryRow {
        background = rgbaColor(0, 0, 0, 0f)
    }
    FlorisImeUi.SmartbarPrimaryActionRowToggle {
        background = rgbaColor(66, 66, 66)
        foreground = rgbaColor(255, 255, 255)
        shape = roundedCornerShape(50)
    }
    FlorisImeUi.SmartbarPrimarySecondaryRowToggle {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(144, 144, 144)
        shape = roundedCornerShape(50)
    }

    FlorisImeUi.SmartbarSecondaryRow {
        background = rgbaColor(33, 33, 33)
    }

    FlorisImeUi.SmartbarActionRow {
        background = rgbaColor(0, 0, 0, 0f)
    }
    FlorisImeUi.SmartbarActionButton {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        shape = roundedCornerShape(50)
    }

    FlorisImeUi.SmartbarCandidateRow {
        background = rgbaColor(0, 0, 0, 0f)
    }
    FlorisImeUi.SmartbarCandidateWord {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        fontSize = size(14.sp)
        shape = rectangleShape()
    }
    FlorisImeUi.SmartbarCandidateWord(pressedSelector = true) {
        background = rgbaColor(66, 66, 66)
        foreground = rgbaColor(220, 220, 220)
    }
    FlorisImeUi.SmartbarCandidateClip {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        fontSize = size(14.sp)
        shape = roundedCornerShape(8)
    }
    FlorisImeUi.SmartbarCandidateClip(pressedSelector = true) {
        background = rgbaColor(66, 66, 66)
        foreground = rgbaColor(220, 220, 220)
    }
    FlorisImeUi.SmartbarCandidateSpacer {
        foreground = rgbaColor(255, 255, 255, 0.25f)
    }

    FlorisImeUi.SmartbarKey {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        fontSize = size(18.sp)
        shape = roundedCornerShape(20)
    }
    FlorisImeUi.SmartbarKey(pressedSelector = true) {
        background = rgbaColor(66, 66, 66)
        foreground = rgbaColor(220, 220, 220)
    }
    FlorisImeUi.SmartbarKey(disabledSelector = true) {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(66, 66, 66)
    }

    FlorisImeUi.SystemNavBar {
        background = rgbaColor(33, 33, 33)
    }
}

object FlorisImeTheme {
    val config: ThemeExtensionConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalConfig.current

    val style: SnyggStylesheet
        @Composable
        @ReadOnlyComposable
        get() = LocalStyle.current
}

@Composable
fun FlorisImeTheme(content: @Composable () -> Unit) {
    // TODO: this should be dynamically selected for user themes
    val activeConfig = remember {
        ThemeExtensionConfig(stylesheet = "")
    }
    val activeStyle = remember {
        FlorisImeThemeBaseStyle.compileToFullyQualified(FlorisImeUiSpec)
    }
    CompositionLocalProvider(LocalConfig provides activeConfig, LocalStyle provides activeStyle) {
        content()
    }
}
