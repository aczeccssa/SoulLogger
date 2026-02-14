package com.lestere.opensource.utils

import com.lestere.opensource.models.CodableException
import java.util.UUID

public val fileCreateException = CodableException(9612, "File create failed.")

public val fileNotFoundException = CodableException(33404, "File not found.")

public val autoReleaseCyberNotBeInstalled = CodableException(-90318, "ARC(Auto Release Cyber) not being installed.")

public val autoReleaseCyberTaskOutOffTime = CodableException(97113, "ARC(Auto Release Cyber) Task time stamp is out off time.")

public fun autoReleaseCyberSystemCachedException(e: Exception) = CodableException(51700, "ARC(Auto Release Cyber) cached cause: ${e.message}")

public fun autoReleaseCyberTaskRunningException(key: UUID, e: Exception) = CodableException(51701, "Force task $key failed with exception: ${e.message}")

public fun pathParametersNotFound(key: String) = CodableException(10009, "Path parameter $key not found.")