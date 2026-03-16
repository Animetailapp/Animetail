package eu.kanade.tachiyomi.data.track.ehentai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class EHentaiApi(private val client: OkHttpClient) {

    /**
     * Logs into E-Hentai forums using [username] and [password].
     *
     * Returns a Pair of (ipb_member_id, ipb_pass_hash) cookies on success.
     * Throws an exception if the credentials are invalid or the request fails.
     */
    suspend fun login(username: String, password: String): Pair<String, String> {
        // Use a client that does NOT follow redirects so we can read Set-Cookie from the 302
        val noRedirectClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val formBody = FormBody.Builder()
            .add("b", "d")
            .add("bt", "Login")
            .add("UserName", username)
            .add("PassWord", password)
            .add("CookieDate", "1")
            .add("ipb_login_submit", "Login!")
            .build()

        val request = Request.Builder()
            .url("https://forums.e-hentai.org/index.php?act=Login&CODE=01")
            .post(formBody)
            .build()

        val response = withContext(Dispatchers.IO) {
            noRedirectClient.newCall(request).execute()
        }

        val setCookieHeaders = response.headers("Set-Cookie")

        val memberId = setCookieHeaders
            .firstOrNull { it.startsWith("ipb_member_id=") }
            ?.substringAfter("ipb_member_id=")
            ?.substringBefore(";")
            ?: throw Exception("Login failed: could not retrieve member ID")

        val passHash = setCookieHeaders
            .firstOrNull { it.startsWith("ipb_pass_hash=") }
            ?.substringAfter("ipb_pass_hash=")
            ?.substringBefore(";")
            ?: throw Exception("Login failed: could not retrieve pass hash")

        if (memberId == "0" || memberId.isBlank()) {
            throw Exception("Login failed: invalid credentials")
        }

        return memberId to passHash
    }
}
