package com.piercingxx.nopemode.enforce

/**
 * Packages that are in our tables but no longer installed (design §14).
 * Pure so the empty-installed-set guard can be tested without PackageManager.
 */
object PackagePruner {

    /**
     * Names in [known] that are absent from [installed].
     *
     * Callers must not invoke this with an empty [installed] produced by a
     * failed PackageManager read — that would look like "everything uninstalled"
     * and wipe the blocked list.
     */
    fun gone(known: Set<String>, installed: Set<String>): Set<String> = known - installed
}
