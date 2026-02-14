package com.lestere.opensource.arc

import com.lestere.opensource.utils.autoReleaseCyberNotBeInstalled
import com.lestere.opensource.utils.autoReleaseCyberSystemCachedException
import com.lestere.opensource.utils.autoReleaseCyberTaskOutOffTime
import com.lestere.opensource.utils.autoReleaseCyberTaskRunningException
import java.util.UUID

internal object ARCException {
    val PLUGIN_NOT_INSTALLED = autoReleaseCyberNotBeInstalled

    val OUT_OF_TIME = autoReleaseCyberTaskOutOffTime

    fun arcSystemCached(e: Exception) =
        autoReleaseCyberSystemCachedException(e)

    fun arcTaskRunningException(key: UUID, e: Exception) =
        autoReleaseCyberTaskRunningException(key, e)
}