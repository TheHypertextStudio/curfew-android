package studio.hypertext.curfew.ui

enum class CurfewLayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        fun forWindow(widthDp: Int, heightDp: Int, fontScale: Float): CurfewLayoutMode {
            val mode = forWidthDp(widthDp)
            // A permanent drawer must leave a readable task column beside its labels.
            return if (mode == EXPANDED && (heightDp < 480 || widthDp / fontScale.coerceAtLeast(1f) < 600)) MEDIUM else mode
        }

        fun forWidthDp(widthDp: Int): CurfewLayoutMode = when {
            widthDp < 600 -> COMPACT
            widthDp < 840 -> MEDIUM
            else -> EXPANDED
        }
    }
}
