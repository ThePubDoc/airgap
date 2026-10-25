package app.airgap.nfc

/**
 * A minimal BER-TLV parser, the encoding EMV cards use for every reply.
 * Tags are kept as ints (0x5F24, 0x57, ...); constructed tags are parsed
 * into children so lookups can search the whole tree.
 */
internal class TlvNode(val tag: Int, val value: ByteArray, val children: List<TlvNode>)

internal object Tlv {

    fun parse(bytes: ByteArray): List<TlvNode> {
        val nodes = mutableListOf<TlvNode>()
        var i = 0
        while (i < bytes.size) {
            val first = bytes[i].toInt() and 0xFF
            // Padding between objects.
            if (first == 0x00 || first == 0xFF) {
                i++
                continue
            }
            i++
            var tag = first
            if (first and 0x1F == 0x1F) {
                // Multi-byte tag: continue while the high bit is set.
                while (i < bytes.size) {
                    val next = bytes[i++].toInt() and 0xFF
                    tag = (tag shl 8) or next
                    if (next and 0x80 == 0) break
                }
            }
            if (i >= bytes.size) break
            var length = bytes[i++].toInt() and 0xFF
            if (length and 0x80 != 0) {
                val lengthBytes = length and 0x7F
                if (lengthBytes > 3 || i + lengthBytes > bytes.size) break
                length = 0
                repeat(lengthBytes) { length = (length shl 8) or (bytes[i++].toInt() and 0xFF) }
            }
            if (i + length > bytes.size) break
            val value = bytes.copyOfRange(i, i + length)
            i += length
            val constructed = first and 0x20 != 0
            nodes += TlvNode(tag, value, if (constructed) parse(value) else emptyList())
        }
        return nodes
    }

    fun find(nodes: List<TlvNode>, tag: Int): TlvNode? {
        for (node in nodes) {
            if (node.tag == tag) return node
            find(node.children, tag)?.let { return it }
        }
        return null
    }

    fun findAll(nodes: List<TlvNode>, tag: Int): List<TlvNode> =
        nodes.flatMap { node -> (if (node.tag == tag) listOf(node) else emptyList()) + findAll(node.children, tag) }

    /** A data object list (tag + length pairs, no values), as in a card's PDOL. */
    fun parseDol(bytes: ByteArray): List<Pair<Int, Int>> {
        val entries = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < bytes.size) {
            val first = bytes[i++].toInt() and 0xFF
            var tag = first
            if (first and 0x1F == 0x1F) {
                while (i < bytes.size) {
                    val next = bytes[i++].toInt() and 0xFF
                    tag = (tag shl 8) or next
                    if (next and 0x80 == 0) break
                }
            }
            if (i >= bytes.size) break
            entries += tag to (bytes[i++].toInt() and 0xFF)
        }
        return entries
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

internal fun String.hexToBytes(): ByteArray =
    replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
