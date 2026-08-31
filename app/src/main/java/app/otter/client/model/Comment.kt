package app.otter.client.model

/**
 * A comment in display order. Threads are represented as a flat list using
 * [parentId] and [depth], which makes a lazy scrolling UI inexpensive.
 */
data class Comment(
    val id: String,
    val postId: String,
    val parentId: String? = null,
    val depth: Int = 0,
    val author: String,
    val body: String,
    val score: Int,
    val createdAtEpochSeconds: Long,
    val voteState: VoteState = VoteState.NONE,
    /**
     * The author's flair in this community, when they have one.
     *
     * Set by the subreddit rather than by the author, so it carries the context a comment is
     * being made in — a flair reading "Cardiologist" changes how its comment is read.
     */
    val authorFlair: String? = null,
    val isSubmitter: Boolean = false,
    val isDistinguished: Boolean = false,
    val isEdited: Boolean = false,
    /**
     * A placeholder Reddit returns in place of replies it did not send. Tapping it fetches
     * [moreChildren]; it is not a comment anyone wrote and carries no author or score.
     */
    val isMoreStub: Boolean = false,
    val moreChildren: List<String> = emptyList(),
    val moreCount: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "Comment id cannot be blank" }
        require(postId.isNotBlank()) { "Comment post id cannot be blank" }
        require(depth >= 0) { "Comment depth cannot be negative" }
        require(author.isNotBlank()) { "Comment author cannot be blank" }
        require(body.isNotBlank()) { "Comment body cannot be blank" }
        require(createdAtEpochSeconds >= 0L) { "Created timestamp cannot be negative" }
        require(parentId != id) { "A comment cannot be its own parent" }
    }
}
