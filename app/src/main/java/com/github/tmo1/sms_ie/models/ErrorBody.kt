package com.github.tmo1.sms_ie.models

import com.github.tmo1.sms_ie.base.HttpErrors
import com.github.tmo1.sms_ie.base.ResponseObject

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
data class ErrorBody(
    val message: String?,
    val status: Int?,
) : ResponseObject<ErrorMessage> {
    override fun toDomain(): ErrorMessage {
        return ErrorMessage(
            message = message,
            status = when (status) {
                401 -> HttpErrors.Unauthorized
                403 -> HttpErrors.Forbidden
                400 -> HttpErrors.BadRequest
                500 -> HttpErrors.ServerError
                409 -> HttpErrors.Conflict
                else -> HttpErrors.NotDefined
            }
        )
    }
}