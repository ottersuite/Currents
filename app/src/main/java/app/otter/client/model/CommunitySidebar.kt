package app.otter.client.model

data class CommunityRule(
    val title: String,
    val description: String = "",
)

data class CommunitySidebar(
    val communityName: String,
    val title: String,
    val description: String,
    val memberCount: Int = 0,
    val activeUserCount: Int = 0,
    val rules: List<CommunityRule> = emptyList(),
) {
    init {
        require(communityName.isNotBlank()) { "Sidebar community cannot be blank" }
    }
}
