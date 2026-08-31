package app.otter.client.model

/**
 * What a new post carries.
 *
 * Mirrors Reddit's `kind` field on `/api/submit`, narrowed to the kinds this client can build a
 * submission for. Reddit also accepts `image`, `video` and `crosspost`, each of which needs an
 * upload or a source post rather than a form field, so none of them appear here yet.
 */
enum class SubmissionKind {
    /** A self post: the composer's text is the body. Reddit calls this `self`. */
    TEXT,

    /** A link post: the composer's web address is the destination and no body text is sent. */
    LINK,
    ;

    /** The value Reddit expects in the `kind` form field. */
    val formValue: String
        get() = when (this) {
            TEXT -> "self"
            LINK -> "link"
        }
}
