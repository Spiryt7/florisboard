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

package dev.patrickgold.florisboard.ime.text.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.prefs.AppPrefs
import dev.patrickgold.florisboard.app.prefs.florisPreferenceModel
import dev.patrickgold.florisboard.common.Pointer
import dev.patrickgold.florisboard.common.PointerMap
import dev.patrickgold.florisboard.common.isOrientationPortrait
import dev.patrickgold.florisboard.common.observeAsTransformingState
import dev.patrickgold.florisboard.common.toIntOffset
import dev.patrickgold.florisboard.debug.LogTopic
import dev.patrickgold.florisboard.debug.flogDebug
import dev.patrickgold.florisboard.ime.core.InputKeyEvent
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.onehanded.OneHandedMode
import dev.patrickgold.florisboard.ime.popup.PopupManagerStub
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingGesture
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.gestures.SwipeGesture
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.theme.FlorisImeTheme
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.snygg.ui.SnyggSurface
import dev.patrickgold.florisboard.snygg.ui.solidColor
import dev.patrickgold.florisboard.snygg.ui.spSize
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TextKeyboardLayout(
    modifier: Modifier = Modifier,
    keyboard: TextKeyboard,
    isPreview: Boolean,
): Unit = with(LocalDensity.current) {
    val context = LocalContext.current
    val prefs by florisPreferenceModel()

    val controller = remember(context) { TextKeyboardLayoutController(context) }
    controller.keyboard = keyboard
    val touchEventChannel = remember { Channel<MotionEvent>(64) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardRowBaseHeight * keyboard.rowCount)
            .onGloballyPositioned { coords -> controller.offset = coords.positionInWindow() }
            .pointerInteropFilter { event ->
                if (isPreview) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        touchEventChannel.trySendBlocking(event)
                        return@pointerInteropFilter true
                    }
                }
                return@pointerInteropFilter false
            },
    ) {
        val keyMarginH by prefs.keyboard.keySpacingHorizontal.observeAsTransformingState { it.dp.toPx() }
        val keyMarginV by prefs.keyboard.keySpacingVertical.observeAsTransformingState { it.dp.toPx() }
        val desiredKey = remember { TextKey(data = TextKeyData.UNSPECIFIED) }
        val keyboardWidth = constraints.maxWidth.toFloat()
        val keyboardHeight = constraints.maxHeight.toFloat()
        desiredKey.touchBounds.apply {
            width = keyboardWidth / 10.0f
            height = FlorisImeSizing.keyboardRowBaseHeight.toPx()
        }
        desiredKey.visibleBounds.applyFrom(desiredKey.touchBounds).deflateBy(keyMarginH, keyMarginV)
        keyboard.layout(keyboardWidth, keyboardHeight, desiredKey)

        val fontSizeMultiplier = prefs.keyboard.fontSizeMultiplier()
        for ((n, textKey) in keyboard.keys().withIndex()) {
            key(keyboard.uniqueComposeUuid, n) {
                TextKeyButton(textKey, fontSizeMultiplier)
            }
        }
    }

    LaunchedEffect(Unit) {
        for (event in touchEventChannel) {
            if (!isActive) break
            controller.onTouchEventInternal(event)
        }
    }
}

@Composable
private fun TextKeyButton(
    key: TextKey,
    fontSizeMultiplier: Float,
) = with(LocalDensity.current) {
    val keyboardManager by LocalContext.current.keyboardManager()
    val keyStyle = FlorisImeTheme.style.get(
        element = FlorisImeUi.Key,
        code = key.computedData.code,
        mode = keyboardManager.activeState.inputMode.value,
        isPressed = key.isPressed,
    )
    val fontSize = keyStyle.fontSize.spSize() * fontSizeMultiplier * when (key.computedData.code) {
        KeyCode.VIEW_CHARACTERS,
        KeyCode.VIEW_SYMBOLS,
        KeyCode.VIEW_SYMBOLS2 -> 0.80f
        KeyCode.VIEW_NUMERIC,
        KeyCode.VIEW_NUMERIC_ADVANCED -> 0.55f
        else -> 1.0f
    }
    SnyggSurface(
        modifier = Modifier
            .requiredSize(key.visibleBounds.size.toDpSize())
            .absoluteOffset { key.visibleBounds.topLeft.toIntOffset() },
        background = keyStyle.background,
        shape = keyStyle.shape,
    ) {
        key.label?.let { label ->
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.Center),
                text = label,
                color = keyStyle.foreground.solidColor(),
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                overflow = when (key.computedData.code) {
                    KeyCode.SPACE -> TextOverflow.Ellipsis
                    else -> TextOverflow.Visible
                },
            )
        }
        key.foregroundDrawableId?.let { drawableId ->
            Icon(
                modifier = Modifier
                    .requiredSize(fontSize.toDp() * 1.1f)
                    .align(Alignment.Center),
                painter = painterResource(drawableId),
                contentDescription = null,
                tint = keyStyle.foreground.solidColor(),
            )
        }
    }
}

private class TextKeyboardLayoutController(
    context: Context,
) : SwipeGesture.Listener, GlideTypingGesture.Listener {
    private val prefs by florisPreferenceModel()
    private val keyboardManager by context.keyboardManager()
    private val popupManager = PopupManagerStub()

    private val activeEditorInstance get() = FlorisImeService.activeEditorInstance()
    private val activeState get() = keyboardManager.activeState
    private val inputEventDispatcher get() = keyboardManager.inputEventDispatcher
    private val inputFeedbackController get() = FlorisImeService.inputFeedbackController()
    private val keyHintConfiguration = prefs.keyboard.keyHintConfiguration()
    private val pointerMap: PointerMap<TouchPointer> = PointerMap { TouchPointer() }
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var initSelectionStart: Int = 0
    private var initSelectionEnd: Int = 0
    private var isGliding: Boolean = false

    private val glideTypingDetector = GlideTypingGesture.Detector(context)
    private val glideDataForDrawing: MutableList<Pair<GlideTypingGesture.Detector.Position, Long>> = mutableListOf()
    private var glideRefreshJob: Job? = null
    private val fadingGlide: MutableList<Pair<GlideTypingGesture.Detector.Position, Long>> = mutableListOf()
    private var fadingGlideRadius: Float = 0.0f
    private val swipeGestureDetector = SwipeGesture.Detector(context, this)

    lateinit var keyboard: TextKeyboard
    var offset = Offset.Zero

    fun onTouchEventInternal(event: MotionEvent) {
        flogDebug { "event=$event" }
        swipeGestureDetector.onTouchEvent(event)

        if (prefs.glide.enabled.get() && keyboard.mode == KeyboardMode.CHARACTERS) {
            val glidePointer = pointerMap.findById(0)
            if (glideTypingDetector.onTouchEvent(event, glidePointer?.initialKey)) {
                for (pointer in pointerMap) {
                    if (pointer.activeKey != null) {
                        onTouchCancelInternal(event, pointer)
                    }
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pointerMap.clear()
                }
                isGliding = true
                return
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val oldPointer = pointerMap.findById(pointerId)
                if (oldPointer != null) {
                    swipeGestureDetector.onTouchCancel(event, oldPointer)
                    onTouchCancelInternal(event, oldPointer)
                    pointerMap.removeById(oldPointer.id)
                }
                // Search for active character keys and cancel them
                for (pointer in pointerMap) {
                    val activeKey = pointer.activeKey
                    if (activeKey != null && popupManager.isSuitableForPopups(activeKey)) {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchUpInternal(event, pointer)
                    }
                }
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (pointerIndex in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(pointerIndex)
                    val pointer = pointerMap.findById(pointerId)
                    if (pointer != null) {
                        pointer.index = pointerIndex
                        val alwaysTriggerOnMove = (pointer.hasTriggeredGestureMove
                            && (pointer.initialKey?.computedData?.code == KeyCode.DELETE
                            && prefs.gestures.deleteKeySwipeLeft.get() == SwipeAction.DELETE_CHARACTERS_PRECISELY
                            || pointer.initialKey?.computedData?.code == KeyCode.SPACE
                            || pointer.initialKey?.computedData?.code == KeyCode.CJK_SPACE))
                        if (swipeGestureDetector.onTouchMove(
                                event,
                                pointer,
                                alwaysTriggerOnMove
                            ) || pointer.hasTriggeredGestureMove
                        ) {
                            pointer.longPressJob?.cancel()
                            pointer.longPressJob = null
                            pointer.hasTriggeredGestureMove = true
                            pointer.activeKey?.let { activeKey ->
                                activeKey.isPressed = false
                                if (inputEventDispatcher.isPressed(activeKey.computedData.code)) {
                                    inputEventDispatcher.send(InputKeyEvent.cancel(activeKey.computedData))
                                }
                            }
                        } else {
                            onTouchMoveInternal(event, pointer)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.findById(pointerId)
                if (pointer != null) {
                    pointer.index = pointerIndex
                    if (swipeGestureDetector.onTouchUp(
                            event,
                            pointer
                        ) || pointer.hasTriggeredGestureMove || pointer.shouldBlockNextUp
                    ) {
                        if (pointer.hasTriggeredGestureMove && pointer.initialKey?.computedData?.code == KeyCode.DELETE) {
                            activeEditorInstance?.apply {
                                if (selection.isSelectionMode) {
                                    deleteBackwards()
                                }
                            }
                        }
                        onTouchCancelInternal(event, pointer)
                    } else {
                        onTouchUpInternal(event, pointer)
                    }
                    pointerMap.removeById(pointer.id)
                }
            }
            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                for (pointer in pointerMap) {
                    if (pointer.id == pointerId) {
                        pointer.index = pointerIndex
                        if (swipeGestureDetector.onTouchUp(
                                event,
                                pointer
                            ) || pointer.hasTriggeredGestureMove || pointer.shouldBlockNextUp
                        ) {
                            if (pointer.hasTriggeredGestureMove && pointer.initialKey?.computedData?.code == KeyCode.DELETE) {
                                activeEditorInstance?.apply {
                                    if (selection.isSelectionMode) {
                                        deleteBackwards()
                                    }
                                }
                            }
                            onTouchCancelInternal(event, pointer)
                        } else {
                            onTouchUpInternal(event, pointer)
                        }
                    } else {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchCancelInternal(event, pointer)
                    }
                }
                pointerMap.clear()
            }
            MotionEvent.ACTION_CANCEL -> {
                for (pointer in pointerMap) {
                    swipeGestureDetector.onTouchCancel(event, pointer)
                    onTouchCancelInternal(event, pointer)
                }
                pointerMap.clear()
            }
        }
    }

    private fun onTouchDownInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val key = keyboard.getKeyForPos(event.getLocalX(pointer.index), event.getLocalY(pointer.index))
        flogDebug { key.toString() }
        if (key != null && key.isEnabled) {
            inputEventDispatcher.send(InputKeyEvent.down(key.computedData))
            if (prefs.keyboard.popupEnabled.get() && popupManager.isSuitableForPopups(key)) {
                popupManager.show(key, keyHintConfiguration)
            }
            inputFeedbackController?.keyPress(key.computedData)
            key.isPressed = true
            if (pointer.initialKey == null) {
                pointer.initialKey = key
            }
            pointer.activeKey = key
            pointer.longPressJob = scope.launch {
                val delayMillis = prefs.keyboard.longPressDelay.get().toLong()
                when (key.computedData.code) {
                    KeyCode.SPACE, KeyCode.CJK_SPACE -> {
                        activeEditorInstance?.run {
                            initSelectionStart = selection.start
                            initSelectionEnd = selection.end
                        }
                        delay((delayMillis * 2.5f).toLong())
                        when (prefs.gestures.spaceBarLongPress.get()) {
                            SwipeAction.NO_ACTION,
                            SwipeAction.INSERT_SPACE -> {
                            }
                            else -> {
                                keyboardManager.executeSwipeAction(prefs.gestures.spaceBarLongPress.get())
                                pointer.shouldBlockNextUp = true
                            }
                        }
                    }
                    KeyCode.SHIFT -> {
                        delay((delayMillis * 2.5f).toLong())
                        inputEventDispatcher.send(InputKeyEvent.downUp(TextKeyData.CAPS_LOCK))
                        inputFeedbackController?.keyLongPress(key.computedData)
                    }
                    KeyCode.LANGUAGE_SWITCH -> {
                        delay((delayMillis * 2.0f).toLong())
                        pointer.shouldBlockNextUp = true
                        inputEventDispatcher.send(InputKeyEvent.downUp(TextKeyData.SHOW_INPUT_METHOD_PICKER))
                    }
                    else -> {
                        delay(delayMillis)
                        if (popupManager.isSuitableForPopups(key) && key.computedPopups.getPopupKeys(
                                keyHintConfiguration
                            ).isNotEmpty()
                        ) {
                            popupManager.extend(key, keyHintConfiguration)
                            inputFeedbackController?.keyLongPress(key.computedData)
                        }
                    }
                }
            }
        } else {
            pointer.activeKey = null
        }
    }

    private fun onTouchMoveInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            if (popupManager.isShowingExtendedPopup) {
                if (!popupManager.propagateMotionEvent(activeKey, event, pointer.index)) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            } else {
                if ((event.getLocalX(pointer.index) < activeKey.visibleBounds.left - 0.1f * activeKey.visibleBounds.width)
                    || (event.getLocalX(pointer.index) > activeKey.visibleBounds.right + 0.1f * activeKey.visibleBounds.width)
                    || (event.getLocalY(pointer.index) < activeKey.visibleBounds.top - 0.35f * activeKey.visibleBounds.height)
                    || (event.getLocalY(pointer.index) > activeKey.visibleBounds.bottom + 0.35f * activeKey.visibleBounds.height)
                ) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
        }
    }

    private fun onTouchUpInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.longPressJob?.cancel()
        pointer.longPressJob = null

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            activeKey.isPressed = false
            if (popupManager.isSuitableForPopups(activeKey)) {
                val retData = popupManager.getActiveKeyData(activeKey, keyHintConfiguration)
                if (retData != null && !pointer.hasTriggeredGestureMove) {
                    if (retData == activeKey.computedData) {
                        inputEventDispatcher.send(InputKeyEvent.up(activeKey.computedData))
                    } else {
                        if (inputEventDispatcher.isPressed(activeKey.computedData.code)) {
                            inputEventDispatcher.send(InputKeyEvent.cancel(activeKey.computedData))
                        }
                        inputEventDispatcher.send(InputKeyEvent.downUp(retData))
                    }
                } else {
                    if (inputEventDispatcher.isPressed(activeKey.computedData.code)) {
                        inputEventDispatcher.send(InputKeyEvent.cancel(activeKey.computedData))
                    }
                }
                popupManager.hide()
            } else {
                if (pointer.hasTriggeredGestureMove) {
                    inputEventDispatcher.send(InputKeyEvent.cancel(activeKey.computedData))
                } else {
                    inputEventDispatcher.send(InputKeyEvent.up(activeKey.computedData))
                }
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
        pointer.shouldBlockNextUp = false
    }

    private fun onTouchCancelInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.longPressJob?.cancel()
        pointer.longPressJob = null

        val activeKey = pointer.activeKey
        if (activeKey != null) {
            activeKey.isPressed = false
            inputEventDispatcher.send(InputKeyEvent.cancel(activeKey.computedData))
            if (popupManager.isSuitableForPopups(activeKey)) {
                popupManager.hide()
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
        pointer.shouldBlockNextUp = false
    }

    override fun onSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false
        val initialKey = pointer.initialKey ?: return false
        val activeKey = pointer.activeKey
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW)

        return when (initialKey.computedData.code) {
            KeyCode.DELETE -> handleDeleteSwipe(event)
            KeyCode.SPACE, KeyCode.CJK_SPACE -> handleSpaceSwipe(event)
            else -> when {
                (initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.SPACE ||
                    initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.CJK_SPACE) &&
                    event.type == SwipeGesture.Type.TOUCH_MOVE -> handleSpaceSwipe(event)
                initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code != KeyCode.SHIFT &&
                    event.type == SwipeGesture.Type.TOUCH_UP -> {
                    activeKey?.let {
                        inputEventDispatcher.send(
                            InputKeyEvent.up(popupManager.getActiveKeyData(it, keyHintConfiguration) ?: it.computedData)
                        )
                    }
                    inputEventDispatcher.send(InputKeyEvent.cancel(TextKeyData.SHIFT))
                    true
                }
                initialKey.computedData.code > KeyCode.SPACE && !popupManager.isShowingExtendedPopup -> when {
                    !prefs.glide.enabled.get() && !pointer.hasTriggeredGestureMove -> when (event.type) {
                        SwipeGesture.Type.TOUCH_UP -> {
                            val swipeAction = when (event.direction) {
                                SwipeGesture.Direction.UP -> prefs.gestures.swipeUp.get()
                                SwipeGesture.Direction.DOWN -> prefs.gestures.swipeDown.get()
                                SwipeGesture.Direction.LEFT -> prefs.gestures.swipeLeft.get()
                                SwipeGesture.Direction.RIGHT -> prefs.gestures.swipeRight.get()
                                else -> SwipeAction.NO_ACTION
                            }
                            if (swipeAction != SwipeAction.NO_ACTION) {
                                keyboardManager.executeSwipeAction(swipeAction)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                    else -> false
                }
                else -> false
            }
        }
    }

    private fun handleDeleteSwipe(event: SwipeGesture.Event): Boolean {
        if (activeState.isRawInputEditor) return false
        val pointer = pointerMap.findById(event.pointerId) ?: return false

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (prefs.gestures.deleteKeySwipeLeft.get()) {
                SwipeAction.DELETE_CHARACTERS_PRECISELY -> {
                    activeEditorInstance?.apply {
                        if (abs(event.relUnitCountX) > 0) {
                            inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                        }
                        markComposingRegion(null)
                        if (selection.isValid) {
                            selection.updateAndNotify(
                                (selection.end + event.absUnitCountX + 1).coerceIn(0, selection.end),
                                selection.end
                            )
                        }
                    }
                    pointer.shouldBlockNextUp = true
                    true
                }
                SwipeAction.DELETE_WORDS_PRECISELY -> {
                    activeEditorInstance?.apply {
                        if (abs(event.relUnitCountX) > 0) {
                            inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                        }
                        markComposingRegion(null)
                        if (selection.isValid) {
                            selectionSetNWordsLeft(abs(event.absUnitCountX / 2) - 1)
                        }
                    }
                    pointer.shouldBlockNextUp = true
                    true
                }
                else -> false
            }
            SwipeGesture.Type.TOUCH_UP -> {
                if (event.direction == SwipeGesture.Direction.LEFT &&
                    prefs.gestures.deleteKeySwipeLeft.get() == SwipeAction.DELETE_WORD
                ) {
                    keyboardManager.executeSwipeAction(prefs.gestures.deleteKeySwipeLeft.get())
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handleSpaceSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    if (prefs.gestures.spaceBarSwipeLeft.get() == SwipeAction.MOVE_CURSOR_LEFT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) {
                                it - 1
                            } else {
                                it
                            }
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                inputEventDispatcher.send(
                                    InputKeyEvent.downUp(
                                        TextKeyData.ARROW_LEFT, count
                                    )
                                )
                            }
                        }
                    }
                    true
                }
                SwipeGesture.Direction.RIGHT -> {
                    if (prefs.gestures.spaceBarSwipeRight.get() == SwipeAction.MOVE_CURSOR_RIGHT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) {
                                it - 1
                            } else {
                                it
                            }
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                inputEventDispatcher.send(
                                    InputKeyEvent.downUp(
                                        TextKeyData.ARROW_RIGHT, count
                                    )
                                )
                            }
                        }
                    }
                    true
                }
                else -> true // To prevent the popup display of nearby keys
            }
            SwipeGesture.Type.TOUCH_UP -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    prefs.gestures.spaceBarSwipeLeft.get().let {
                        if (it != SwipeAction.MOVE_CURSOR_LEFT) {
                            keyboardManager.executeSwipeAction(it)
                            true
                        } else {
                            false
                        }
                    }
                }
                SwipeGesture.Direction.RIGHT -> {
                    prefs.gestures.spaceBarSwipeRight.get().let {
                        if (it != SwipeAction.MOVE_CURSOR_RIGHT) {
                            keyboardManager.executeSwipeAction(it)
                            true
                        } else {
                            false
                        }
                    }
                }
                else -> {
                    if (event.absUnitCountY < -6) {
                        keyboardManager.executeSwipeAction(prefs.gestures.spaceBarSwipeUp.get())
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        if (prefs.glide.enabled.get()) {
            glideDataForDrawing.add(Pair(point, System.currentTimeMillis()))
            if (glideRefreshJob == null) {
                glideRefreshJob = scope.launch(Dispatchers.Main) {
                    while (true) {
                        delay(10)
                    }
                }
            }
        }
    }

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        onGlideCancelled()
    }

    override fun onGlideCancelled() {
        if (prefs.glide.showTrail.get()) {
            fadingGlide.clear()
            fadingGlide.addAll(glideDataForDrawing)

            val animator = ValueAnimator.ofFloat(20.0f, 0.0f)
            animator.interpolator = AccelerateInterpolator()
            animator.duration = prefs.glide.trailDuration.get().toLong()
            animator.addUpdateListener {
                fadingGlideRadius = it.animatedValue as Float
            }
            animator.start()

            glideDataForDrawing.clear()
            isGliding = false
            glideRefreshJob?.cancel()
            glideRefreshJob = null
        }
    }

    fun MotionEvent.getLocalX(index: Int): Float {
        return this.getX(index) - offset.x
    }

    fun MotionEvent.getLocalY(index: Int): Float {
        return this.getY(index) - offset.y
    }

    private class TouchPointer : Pointer() {
        var initialKey: TextKey? = null
        var activeKey: TextKey? = null
        var longPressJob: Job? = null
        var hasTriggeredGestureMove: Boolean = false
        var shouldBlockNextUp: Boolean = false

        override fun reset() {
            super.reset()
            initialKey = null
            activeKey = null
            longPressJob?.cancel()
            longPressJob = null
            hasTriggeredGestureMove = false
            shouldBlockNextUp = false
        }

        override fun toString(): String {
            return "${TouchPointer::class.simpleName} { id=$id, index=$index, initialKey=$initialKey, activeKey=$activeKey }"
        }
    }
}

@Composable
private fun AppPrefs.Keyboard.fontSizeMultiplier(): Float {
    val configuration = LocalConfiguration.current
    val oneHandedMode by oneHandedMode.observeAsState()
    val oneHandedModeFactor by oneHandedModeScaleFactor.observeAsTransformingState { it / 100.0f }
    val fontSizeMultiplierBase by if (configuration.isOrientationPortrait()) {
        fontSizeMultiplierPortrait
    } else {
        fontSizeMultiplierLandscape
    }.observeAsTransformingState { it / 100.0f }
    val fontSizeMultiplier = fontSizeMultiplierBase * if (oneHandedMode != OneHandedMode.OFF && configuration.isOrientationPortrait()) {
        oneHandedModeFactor
    } else {
        1.0f
    }
    return fontSizeMultiplier
}