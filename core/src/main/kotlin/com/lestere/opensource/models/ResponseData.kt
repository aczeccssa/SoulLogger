package com.lestere.opensource.models

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponseData<T>(
    @SerialName("status") val status: Int,
    @SerialName("quota") val quota: Quota?,
    @SerialName("error") val error: CodableException?,
    @SerialName("main") @Serializable val main: T?
) {
    /**
     * Represents request quota/limit information.
     * @property limited The maximum allowed count for the request.
     * @TODO: Implement user force quota feature for rate limiting.
     */
    @Serializable
    data class Quota(val limited: Int)

    class ResponseBuilder<T>(
        var status: HttpStatusCode = HttpStatusCode.OK,
        var quota: Quota? = null,
        var error: CodableException? = null,
        var main: T? = null
    )
}
