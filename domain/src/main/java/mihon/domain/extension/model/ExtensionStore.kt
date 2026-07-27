package mihon.domain.extension.model

data class ExtensionStore(
    val indexUrl: String,
    val name: String,
    val badgeLabel: String,
    val signingKey: String,
    val contact: Contact,
    val isLegacy: Boolean,
    val extensionListUrl: String? = null,
) {
    data class Contact(
        val website: String,
        val discord: String?,
    )

    companion object {
        const val ANIMETAIL_SIGNATURE = "14clc5e350c873d4ec438790ee24272db148a65057941c25391515ac8194f7d29c9"
        const val KEIYOUSHI_SIGNATURE = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
    }
}
