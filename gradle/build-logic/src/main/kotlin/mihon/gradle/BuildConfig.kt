package mihon.gradle

import org.gradle.api.Project

interface BuildConfig {
    val enableUpdater: Boolean
    val includeDependencyInfo: Boolean
}

val Project.Config: BuildConfig get() = object : BuildConfig {
    override val enableUpdater: Boolean = project.hasProperty("enable-updater")
    override val includeDependencyInfo: Boolean = project.hasProperty("include-dependency-info")
}
