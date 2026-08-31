package app.otter.client.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.otter.client.model.Comment
import app.otter.client.model.Community
import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind
import app.otter.client.model.Post
import app.otter.client.model.PostMedia
import app.otter.client.model.PostPreview
import app.otter.client.model.PostType
import app.otter.client.model.VoteState
import org.json.JSONArray
import org.json.JSONObject

data class CachedFeed(
    val posts: List<Post>,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val sort: String,
    val timeframe: String,
    val updatedAtMillis: Long,
)

/** A stored string alongside the moment it was written. */
data class CachedValue(val payload: String, val updatedAtMillis: Long)

data class CachedComments(
    val comments: List<Comment>,
    val sort: String,
    val updatedAtMillis: Long,
)

/**
 * Small private SQLite cache used for instant restoration and offline reading.
 *
 * Reddit objects are stored as versioned JSON payloads. That keeps network model changes out of
 * the database schema while the table metadata remains queryable and easy to evict.
 */
class OfflineCacheStore(context: Context) {
    private val helper = CacheDatabase(context.applicationContext)

    fun feed(key: String): CachedFeed? = helper.readableDatabase.query(
        FEEDS,
        arrayOf(PAYLOAD, SCROLL_INDEX, SCROLL_OFFSET, SORT, TIMEFRAME, UPDATED_AT),
        "$KEY = ?",
        arrayOf(key),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        runCatching {
            CachedFeed(
                posts = JsonCodec.posts(cursor.getString(0)),
                scrollIndex = cursor.getInt(1),
                scrollOffset = cursor.getInt(2),
                sort = cursor.getString(3),
                timeframe = cursor.getString(4),
                updatedAtMillis = cursor.getLong(5),
            )
        }.getOrNull()
    }

    fun putFeed(key: String, value: CachedFeed) {
        helper.writableDatabase.insertWithOnConflict(
            FEEDS,
            null,
            ContentValues().apply {
                put(KEY, key)
                put(PAYLOAD, JsonCodec.posts(value.posts))
                put(SCROLL_INDEX, value.scrollIndex)
                put(SCROLL_OFFSET, value.scrollOffset)
                put(SORT, value.sort)
                put(TIMEFRAME, value.timeframe)
                put(UPDATED_AT, value.updatedAtMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        trim(FEEDS, MAX_FEEDS)
    }

    fun comments(postId: String): CachedComments? = helper.readableDatabase.query(
        COMMENTS,
        arrayOf(PAYLOAD, SORT, UPDATED_AT),
        "$KEY = ?",
        arrayOf(postId),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        runCatching {
            CachedComments(
                comments = JsonCodec.comments(cursor.getString(0)),
                sort = cursor.getString(1),
                updatedAtMillis = cursor.getLong(2),
            )
        }.getOrNull()
    }

    fun putComments(postId: String, value: CachedComments) {
        helper.writableDatabase.insertWithOnConflict(
            COMMENTS,
            null,
            ContentValues().apply {
                put(KEY, postId)
                put(PAYLOAD, JsonCodec.comments(value.comments))
                put(SORT, value.sort)
                put(UPDATED_AT, value.updatedAtMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        trim(COMMENTS, MAX_COMMENT_THREADS)
    }

    fun draft(key: String): String? = storedValue(key)?.payload

    /**
     * A stored string and when it was written.
     *
     * The same table backs saved drafts and anything else small the app wants to keep between
     * launches; the timestamp is what lets a caller decide whether its copy has gone stale.
     */
    fun storedValue(key: String): CachedValue? = helper.readableDatabase.query(
        DRAFTS,
        arrayOf(PAYLOAD, UPDATED_AT),
        "$KEY = ?",
        arrayOf(key),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getString(0)?.let { payload -> CachedValue(payload, cursor.getLong(1)) }
    }

    fun putDraft(key: String, payload: String?) {
        if (payload.isNullOrBlank()) {
            helper.writableDatabase.delete(DRAFTS, "$KEY = ?", arrayOf(key))
            return
        }
        helper.writableDatabase.insertWithOnConflict(
            DRAFTS,
            null,
            ContentValues().apply {
                put(KEY, key)
                put(PAYLOAD, payload)
                put(UPDATED_AT, System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun clearContent() {
        helper.writableDatabase.apply {
            delete(FEEDS, null, null)
            delete(COMMENTS, null, null)
            delete(DRAFTS, null, null)
        }
    }

    private fun trim(table: String, limit: Int) {
        helper.writableDatabase.execSQL(
            "DELETE FROM $table WHERE $KEY NOT IN " +
                "(SELECT $KEY FROM $table ORDER BY $UPDATED_AT DESC LIMIT $limit)",
        )
    }

    private class CacheDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE $FEEDS (" +
                    "$KEY TEXT PRIMARY KEY, $PAYLOAD TEXT NOT NULL, " +
                    "$SCROLL_INDEX INTEGER NOT NULL, $SCROLL_OFFSET INTEGER NOT NULL, " +
                    "$SORT TEXT NOT NULL, $TIMEFRAME TEXT NOT NULL, $UPDATED_AT INTEGER NOT NULL)",
            )
            database.execSQL(
                "CREATE TABLE $COMMENTS (" +
                    "$KEY TEXT PRIMARY KEY, $PAYLOAD TEXT NOT NULL, " +
                    "$SORT TEXT NOT NULL, $UPDATED_AT INTEGER NOT NULL)",
            )
            database.execSQL(
                "CREATE TABLE $DRAFTS (" +
                    "$KEY TEXT PRIMARY KEY, $PAYLOAD TEXT NOT NULL, $UPDATED_AT INTEGER NOT NULL)",
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            database.execSQL("DROP TABLE IF EXISTS $FEEDS")
            database.execSQL("DROP TABLE IF EXISTS $COMMENTS")
            database.execSQL("DROP TABLE IF EXISTS $DRAFTS")
            onCreate(database)
        }
    }

    private companion object {
        const val DATABASE_NAME = "otter_offline.db"
        const val DATABASE_VERSION = 1
        const val FEEDS = "feed_cache"
        const val COMMENTS = "comment_cache"
        const val DRAFTS = "draft_cache"
        const val KEY = "cache_key"
        const val PAYLOAD = "payload"
        const val SCROLL_INDEX = "scroll_index"
        const val SCROLL_OFFSET = "scroll_offset"
        const val SORT = "sort_name"
        const val TIMEFRAME = "timeframe_name"
        const val UPDATED_AT = "updated_at"
        const val MAX_FEEDS = 12
        const val MAX_COMMENT_THREADS = 40
    }
}

private object JsonCodec {
    fun posts(posts: List<Post>): String = JSONArray().apply {
        posts.forEach { put(it.toJson()) }
    }.toString()

    fun posts(payload: String): List<Post> = JSONArray(payload).objects().map(::post)

    fun comments(comments: List<Comment>): String = JSONArray().apply {
        comments.forEach { put(it.toJson()) }
    }.toString()

    fun comments(payload: String): List<Comment> = JSONArray(payload).objects().map(::comment)

    private fun Post.toJson() = JSONObject().apply {
        put("id", id)
        put("community", community.toJson())
        put("title", title)
        put("author", author)
        put("type", type.name)
        put("score", score)
        put("commentCount", commentCount)
        put("createdAt", createdAtEpochSeconds)
        putNullable("domain", domain)
        putNullable("flair", flairText)
        putNullable("destination", destinationUrl)
        putNullable("body", body)
        putNullable("preview", preview?.toJson())
        putNullable("media", media?.toJson())
        put("vote", voteState.name)
        put("saved", isSaved)
        put("read", isRead)
        put("stickied", isStickied)
        put("nsfw", isNsfw)
        put("spoiler", isSpoiler)
    }

    private fun post(value: JSONObject) = Post(
        id = value.getString("id"),
        community = community(value.getJSONObject("community")),
        title = value.getString("title"),
        author = value.getString("author"),
        type = enumValueOr(value.optString("type"), PostType.TEXT),
        score = value.optInt("score"),
        commentCount = value.optInt("commentCount"),
        createdAtEpochSeconds = value.optLong("createdAt"),
        domain = value.stringOrNull("domain"),
        flairText = value.stringOrNull("flair"),
        destinationUrl = value.stringOrNull("destination"),
        body = value.stringOrNull("body"),
        preview = value.objectOrNull("preview")?.let(::preview),
        media = value.objectOrNull("media")?.let(::media),
        voteState = enumValueOr(value.optString("vote"), VoteState.NONE),
        isSaved = value.optBoolean("saved"),
        isRead = value.optBoolean("read"),
        isStickied = value.optBoolean("stickied"),
        isNsfw = value.optBoolean("nsfw"),
        isSpoiler = value.optBoolean("spoiler"),
    )

    private fun Community.toJson() = JSONObject().apply {
        put("name", name)
        put("displayName", displayName)
        put("members", memberCount)
        put("favorite", isFavorite)
        put("accentStart", accentStartArgb)
        put("accentEnd", accentEndArgb)
        putNullable("icon", iconUrl)
    }

    private fun community(value: JSONObject) = Community(
        name = value.getString("name"),
        displayName = value.getString("displayName"),
        memberCount = value.optInt("members"),
        isFavorite = value.optBoolean("favorite"),
        accentStartArgb = value.optLong("accentStart"),
        accentEndArgb = value.optLong("accentEnd"),
        iconUrl = value.optString("icon").takeIf(String::isNotBlank),
    )

    private fun PostPreview.toJson() = JSONObject().apply {
        put("assetKey", assetKey)
        put("label", label)
        put("start", startColorArgb)
        put("end", endColorArgb)
        putNullable("image", imageUrl)
        putNullable("thumbnail", thumbnailUrl)
        putNullable("card", cardImageUrl)
        put("ratio", aspectRatio.toDouble())
        put("alt", altText)
    }

    private fun preview(value: JSONObject) = PostPreview(
        assetKey = value.getString("assetKey"),
        label = value.getString("label"),
        startColorArgb = value.optLong("start"),
        endColorArgb = value.optLong("end"),
        imageUrl = value.stringOrNull("image"),
        thumbnailUrl = value.stringOrNull("thumbnail"),
        cardImageUrl = value.stringOrNull("card"),
        aspectRatio = value.optDouble("ratio", 16.0 / 9.0).toFloat(),
        altText = value.optString("alt", value.getString("label")),
    )

    private fun PostMedia.toJson() = JSONObject().put(
        "assets",
        JSONArray().apply { assets.forEach { put(it.toJson()) } },
    )

    private fun media(value: JSONObject): PostMedia? {
        val assets = value.optJSONArray("assets")?.objects()?.map(::asset).orEmpty()
        return assets.takeIf(List<*>::isNotEmpty)?.let(::PostMedia)
    }

    private fun MediaAsset.toJson() = JSONObject().apply {
        put("kind", kind.name)
        put("url", url)
        putNullable("fallback", fallbackUrl)
        putNullable("preview", previewUrl)
        put("ratio", aspectRatio.toDouble())
        putNullable("caption", caption)
        put("audio", hasAudio)
        put("duration", durationSeconds)
    }

    private fun asset(value: JSONObject) = MediaAsset(
        kind = enumValueOr(value.optString("kind"), MediaKind.IMAGE),
        url = value.getString("url"),
        fallbackUrl = value.stringOrNull("fallback"),
        previewUrl = value.stringOrNull("preview"),
        aspectRatio = value.optDouble("ratio", 16.0 / 9.0).toFloat(),
        caption = value.stringOrNull("caption"),
        hasAudio = value.optBoolean("audio"),
        durationSeconds = value.optInt("duration"),
    )

    private fun Comment.toJson() = JSONObject().apply {
        put("id", id)
        put("postId", postId)
        putNullable("parentId", parentId)
        put("depth", depth)
        put("author", author)
        put("body", body)
        put("score", score)
        put("createdAt", createdAtEpochSeconds)
        put("vote", voteState.name)
        putNullable("flair", authorFlair)
        put("submitter", isSubmitter)
        put("distinguished", isDistinguished)
        put("edited", isEdited)
        put("moreStub", isMoreStub)
        put("moreChildren", JSONArray(moreChildren))
        put("moreCount", moreCount)
    }

    private fun comment(value: JSONObject) = Comment(
        id = value.getString("id"),
        postId = value.getString("postId"),
        parentId = value.stringOrNull("parentId"),
        depth = value.optInt("depth"),
        author = value.getString("author"),
        body = value.getString("body"),
        score = value.optInt("score"),
        createdAtEpochSeconds = value.optLong("createdAt"),
        voteState = enumValueOr(value.optString("vote"), VoteState.NONE),
        authorFlair = value.stringOrNull("flair"),
        isSubmitter = value.optBoolean("submitter"),
        isDistinguished = value.optBoolean("distinguished"),
        isEdited = value.optBoolean("edited"),
        isMoreStub = value.optBoolean("moreStub"),
        moreChildren = value.optJSONArray("moreChildren")?.strings().orEmpty(),
        moreCount = value.optInt("moreCount"),
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { !isNull(key) && it.isNotBlank() }

    private fun JSONObject.objectOrNull(key: String): JSONObject? =
        takeUnless { isNull(key) }?.optJSONObject(key)

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private inline fun <reified T : Enum<T>> enumValueOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
