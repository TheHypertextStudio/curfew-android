package studio.hypertext.curfew.ui

enum class CurfewLayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        fun forWidthDp(widthDp: Int): CurfewLayoutMode = when {
            widthDp < 600 -> COMPACT
            widthDp < 840 -> MEDIUM
            else -> EXPANDED
        }
    }
}
