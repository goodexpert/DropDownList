@Composable
fun <T : Any > DragDropList(
    onMove: (Int, Int) -> Unit,
    items: List<T>,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (T) -> Unit
) {
    val dragDropListState = rememberDragDropListState(onMove = onMove)

    LazyColumn(
        modifier = modifier
            .composed {
                dragDropListState.modifier
            },
        state = dragDropListState.lazyListState,
        contentPadding = contentPadding
    ) {
        itemsIndexed(items) { index, item ->
            Surface(
                modifier = Modifier
                    .composed {
                        val offsetOrNull = dragDropListState.getOffsetBy(index)

                        Modifier
                           .graphicsLayer {
                               translationY = offsetOrNull ?: 0f
                           }
                    }
                    .wrapContentSize(),
                shape = RoundedCornerShape(4.dp)
                color = contentColor,
                content = { content(item) }
            )
        }
    }
}