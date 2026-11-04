// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 bundles its own Kotlin (built-in Kotlin); declaring the plugin here picks this newer version
    alias(libs.plugins.kotlin.android) apply false
}