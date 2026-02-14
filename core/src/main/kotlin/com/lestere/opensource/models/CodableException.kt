package com.lestere.opensource.models

import kotlinx.serialization.Serializable

@Serializable
open class CodableException(val code: Long, override val message: String) : Exception(message)