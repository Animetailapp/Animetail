package eu.kanade.tachiyomi.ui.player.cache

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

/**
 * Whether the URL points to a server on the device itself, such as the one an extension starts
 * to serve Mega videos. Those URLs carry a port and a token that only exist while it runs.
 */
fun String.isLoopbackUrl(): Boolean = toHttpUrlOrNull()?.host in LOOPBACK_HOSTS
