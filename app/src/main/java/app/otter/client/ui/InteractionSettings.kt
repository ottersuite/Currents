package app.otter.client.ui

import app.otter.client.ui.components.SwipeAction

enum class FeedAction(val label: String) {
    Search("Search"),
    Refresh("Refresh"),
    Saved("Saved"),
    Compose("Create post"),
    MarkAboveRead("Mark above read"),
    Menu("Communities"),
}

data class SwipeActionConfig(
    val rightShort: SwipeAction = SwipeAction.Upvote,
    val rightLong: SwipeAction = SwipeAction.Downvote,
    val leftShort: SwipeAction = SwipeAction.Save,
    val leftLong: SwipeAction = SwipeAction.Hide,
)

val DefaultCommentSwipeActions = SwipeActionConfig(
    leftShort = SwipeAction.Reply,
    leftLong = SwipeAction.Collapse,
)
