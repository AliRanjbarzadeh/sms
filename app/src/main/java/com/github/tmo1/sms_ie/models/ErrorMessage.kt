package com.github.tmo1.sms_ie.models

import com.github.tmo1.sms_ie.base.HttpErrors

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
data class ErrorMessage(
	val message: String?,
	val status: HttpErrors,
)