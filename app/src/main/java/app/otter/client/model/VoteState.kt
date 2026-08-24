package app.otter.client.model

/** The user's current vote and its contribution to an item's displayed score. */
enum class VoteState(val scoreDelta: Int) {
    DOWNVOTED(-1),
    NONE(0),
    UPVOTED(1),
}
