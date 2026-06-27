package com.alitycs.sdk

import java.util.Locale
import java.util.TimeZone

private const val SDK_VERSION = "1.0.0"

fun collectContext(): EventContext {
    return EventContext(
        sdkVersion = SDK_VERSION,
        sdkLanguage = "kotlin",
        locale = getLocale(),
        timezone = getTimezone(),
        osName = getOsName(),
        osVersion = getOsVersion(),
        jvmVersion = getJvmVersion()
    )
}

private fun getLocale(): String? = try {
    Locale.getDefault().toLanguageTag()
} catch (_: Exception) {
    null
}

private fun getTimezone(): String? = try {
    TimeZone.getDefault().id
} catch (_: Exception) {
    null
}

private fun getOsName(): String? = System.getProperty("os.name")
private fun getOsVersion(): String? = System.getProperty("os.version")
private fun getJvmVersion(): String? = System.getProperty("java.version")
