package eu.kanade.tachiyomi.ui.player.cache

import java.io.IOException
import java.io.InputStream

/** Response body of a resource, starting at byte [offset]. */
class UpstreamBody(val stream: InputStream, val offset: Long) : AutoCloseable by stream

/**
 * Bytes `[start, endInclusive]` of a resource of [length] bytes. They're read from cached blocks
 * until the first missing one; from there on they come from the network and every completed block
 * is stored, so playing the same range again doesn't download anything.
 *
 * @param openUpstream opens the resource at the given offset. It must fail rather than return
 * a body of a different size or one starting elsewhere.
 * @param upstream a body already opened by the caller at the start of a block.
 * @param onEndReached called once the last byte of the resource was read.
 */
class CachedRangeStream(
    private val resource: VideoCacheStore.Resource,
    private val length: Long,
    start: Long,
    private val endInclusive: Long,
    private val openUpstream: (offset: Long) -> UpstreamBody,
    private var upstream: UpstreamBody? = null,
    private val onEndReached: () -> Unit = {},
) : InputStream() {

    private val blockSize = resource.blockSize
    private var position = start

    private var blockIndex = -1L
    private var block = ByteArray(0)
    private var blockFilled = 0

    // Offset of the next byte the upstream body returns.
    private var upstreamPosition = upstream?.offset ?: -1L

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (count == 0) return 0
        if (position > endInclusive) return -1

        val index = position / blockSize
        if (index != blockIndex) moveToBlock(index)

        val positionInBlock = (position - index * blockSize).toInt()
        if (positionInBlock >= blockFilled) fillBlock(positionInBlock + 1)

        val available = minOf(
            (blockFilled - positionInBlock).toLong(),
            endInclusive - position + 1,
            count.toLong(),
        ).toInt()
        System.arraycopy(block, positionInBlock, buffer, offset, available)
        position += available
        if (position == length) onEndReached()
        return available
    }

    override fun close() {
        // The player stops reading mid-block when closed or seeking; what arrived is kept.
        if (upstream != null && blockFilled in 1 until block.size) {
            resource.writePartialBlock(blockIndex, block, blockFilled)
        }
        upstream?.close()
        upstream = null
    }

    private fun moveToBlock(index: Long) {
        blockIndex = index
        val blockStart = index * blockSize
        block = ByteArray(resource.blockLength(index, length))
        blockFilled = 0
        // Once downloading, keep reading the same connection instead of reopening one per block.
        if (upstream != null && upstreamPosition == blockStart) return

        val cached = resource.readBlock(index, length)
        if (cached != null) {
            block = cached
            blockFilled = cached.size
            return
        }

        resource.readPartialBlock(index, length)?.let { partial ->
            partial.copyInto(block)
            blockFilled = partial.size
        }
        openUpstreamAt(blockStart + blockFilled)
    }

    private fun openUpstreamAt(offset: Long) {
        upstream?.close()
        upstream = null
        val body = openUpstream(offset)
        if (body.offset != offset) {
            body.close()
            throw IOException("Expected the resource at $offset, got ${body.offset}")
        }
        upstream = body
        upstreamPosition = offset
    }

    private fun fillBlock(minFilled: Int) {
        val stream = upstream?.stream ?: throw IOException("Block $blockIndex is not cached")
        while (blockFilled < minFilled) {
            val read = stream.read(block, blockFilled, block.size - blockFilled)
            if (read == -1) throw IOException("Resource ended before byte ${upstreamPosition + 1}")
            blockFilled += read
            upstreamPosition += read
        }
        if (blockFilled == block.size && !resource.hasBlock(blockIndex, length)) {
            resource.writeBlock(blockIndex, block, blockFilled)
        }
    }
}
