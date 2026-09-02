package app.otter.client.model

/** One item returned by Reddit's inbox listing. */
data class RedditMessage(
    val id: String,
    val fullname: String,
    val author: String,
    val subject: String,
    val body: String,
    val createdAtEpochSeconds: Long,
    val isUnread: Boolean,
    val isCommentReply: Boolean,
    val context: String? = null,
)
