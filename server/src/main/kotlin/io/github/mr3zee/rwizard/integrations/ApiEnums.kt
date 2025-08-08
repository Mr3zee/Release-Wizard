package io.github.mr3zee.rwizard.integrations

enum class MediaType(val value: String) {
    APPLICATION_JSON("application/json"),
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    GITHUB_V3_JSON("application/vnd.github.v3+json")
}

enum class UserAgent(val value: String) {
    RELEASE_WIZARD("Release-Wizard")
}

enum class GitHubRepoVisibility(val api: String) {
    ALL("all"),
    OWNER("owner"),
    PUBLIC("public"),
    PRIVATE("private")
}

enum class GitHubRepoAffiliation(val api: String) {
    OWNER("owner"),
    COLLABORATOR("collaborator"),
    ORGANIZATION_MEMBER("organization_member")
}

enum class GitHubRepoSort(val api: String) {
    CREATED("created"),
    UPDATED("updated"),
    PUSHED("pushed"),
    FULL_NAME("full_name")
}

enum class SlackConversationType(val api: String) {
    PUBLIC_CHANNEL("public_channel"),
    PRIVATE_CHANNEL("private_channel"),
    MPIM("mpim"),
    IM("im")
}
