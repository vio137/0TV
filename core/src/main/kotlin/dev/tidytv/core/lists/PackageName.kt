package dev.tidytv.core.lists

/**
 * Package names end up inside shell commands on the TV, so nothing reaches a command unless it
 * passes this check. Same pattern as upstream cleanyourtv (tools/disable.sh, #165).
 */
object PackageName {
    private val PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun isValid(name: String): Boolean = PATTERN.matches(name)

    fun require(name: String): String {
        require(isValid(name)) { "not a package name: ${name.take(80)}" }
        return name
    }
}
