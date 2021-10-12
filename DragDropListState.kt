@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (Int, Int) -> Unit,
): ReorderableListState {
    return remember { DragDropListState(lazyListState = lazyListState, onMove = onMove) }
}

@Stable
class DragDropListState(
    val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {
    private var draggedDistance by mutableStateOf(0f)

    // used to obtain initial offsets on drag start
    private var draggedItemInfo by mutableStateOf<LazyListItemInfo?>(null)

    private var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)

    private val initialOffsets: Pair<Int, Int>?
        get() = draggedItemInfo?.let { Pair(it.offset, it.offsetEnd) }

    private val elementDisplacement: Float?
        get() = currentIndexOfDraggedItem
            ?.let { lazyListState.getVisibleItemInfoFor(absoluteIndex = it) }
            ?.let { item -> (draggedItemInfo?.offset ?: 0f).toFloat() + draggedDistance - item.offset }

    private val currentItemInfo: LazyListItemInfo?
        get() = currentIndexOfDraggedItem?.let {
            lazyListState.getVisibleItemInfoFor(absoluteIndex = it)
        }

    private var overscrollJob by mutableStateOf<Job?>(null)

    val modifier:Modifier = Modifier
        .pointerInput(kotlin.Unit) {
            detectDragGesturesAfterLongPress(
                onDrag = { change, offset ->
                    change.consumeAllChanges()
                    onDrag(offset)

                    if (overscrollJob?.isActive == true)
                        return@detectDragGesturesAfterLongPress

                    checkForOverScroll()
                        .takeIf { it != 0f }
                        ?.let { overscrollJob = CoroutineScope(Dispatchers.IO).launch { lazyListState.scrollBy(it) } }
                        ?: run { overscrollJob?.cancel() }
                },
                onDragStart = { offset -> onDragStart(offset) },
                onDragEnd = { onDragInterrupted() },
                onDragCancel = { onDragInterrupted() }
            )
        }

    private fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                currentIndexOfDraggedItem = it.index
                draggedItemInfo = it
            }
    }

    private fun onDragInterrupted() {
        draggedDistance = 0f
        currentIndexOfDraggedItem = null
        draggedItemInfo = null
        overscrollJob?.cancel()
    }

    private fun onDrag(offset: Offset) {
        draggedDistance += offset.y

        initialOffsets?.let { (topOffset, bottomOffset) ->
            val startOffset = topOffset + draggedDistance
            val endOffset = bottomOffset + draggedDistance

            currentItemInfo?.let { hovered ->
                lazyListState.layoutInfo.visibleItemsInfo
                    .filterNot { item -> item.offsetEnd < startOffset || item.offset > endOffset || hovered.index == item.index }
                    .firstOrNull { item ->
                        val delta = startOffset - hovered.offset
                        when {
                            delta > 0 -> (endOffset > item.offsetEnd)
                            else -> (startOffset < item.offset)
                        }
                    }
                    ?.also { item ->
                        currentIndexOfDraggedItem?.let { current -> onMove.invoke(current, item.index) }
                        currentIndexOfDraggedItem = item.index
                    }
            }
        }
    }

    private fun checkForOverScroll(): Float {
        return draggedItemInfo?.let {
            val startOffset = it.offset + draggedDistance
            val endOffset = it.offsetEnd + draggedDistance

            return@let when {
                draggedDistance > 0 -> (endOffset - lazyListState.layoutInfo.viewportEndOffset).takeIf { diff -> diff > 0 }
                draggedDistance < 0 -> (startOffset - lazyListState.layoutInfo.viewportStartOffset).takeIf { diff -> diff < 0 }
                else -> null
            }
        } ?: 0f
    }

    fun getOffsetBy(index: Int): Float? {
        return elementDisplacement.takeIf { index == currentIndexOfDraggedItem }
    }
}