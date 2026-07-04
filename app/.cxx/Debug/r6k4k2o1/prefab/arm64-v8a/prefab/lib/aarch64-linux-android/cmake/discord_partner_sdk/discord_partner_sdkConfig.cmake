if(NOT TARGET discord_partner_sdk::discord_partner_sdk)
add_library(discord_partner_sdk::discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk::discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION "C:/Users/amaur/.gradle/caches/9.5.1/transforms/51caf4f0bbded7d8012d9d33f3590ba7/transformed/discord_partner_sdk/prefab/modules/discord_partner_sdk/libs/android.arm64-v8a/libdiscord_partner_sdk.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/amaur/.gradle/caches/9.5.1/transforms/51caf4f0bbded7d8012d9d33f3590ba7/transformed/discord_partner_sdk/prefab/modules/discord_partner_sdk/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

