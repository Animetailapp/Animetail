package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebGpuShadersTest {
    @Test
    fun shaderPreferencesRoundTripInOrder() {
        val shaders = listOf(
            WebGpuShader("11111111-1111-1111-1111-111111111111.wgsl", "First"),
            WebGpuShader("22222222-2222-2222-2222-222222222222.wgsl", "Second"),
        )

        assertEquals(shaders, WebGpuShaders.deserialize(WebGpuShaders.serialize(shaders)))
    }

    @Test
    fun shaderPreferencesRejectInvalidPathsAndDuplicates() {
        val valid = "11111111-1111-1111-1111-111111111111.wgsl"
        val input = "$valid\tFirst\n../outside.wgsl\tUnsafe\n$valid\tDuplicate\ninvalid"

        assertEquals(listOf(WebGpuShader(valid, "First")), WebGpuShaders.deserialize(input))
    }
}
