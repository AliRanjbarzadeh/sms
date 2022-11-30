package com.github.tmo1.sms_ie.exceptions

import com.github.tmo1.sms_ie.base.HttpErrors
import com.github.tmo1.sms_ie.models.ErrorBody
import com.github.tmo1.sms_ie.models.ErrorMessage
import com.google.gson.Gson
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
@Singleton
class NetworkExceptionHandler @Inject constructor(private val gson: Gson) {
    fun traceErrorException(throwable: Throwable?): ErrorMessage {
        val errorMessage: ErrorMessage? = when (throwable) {

            // if throwable is an instance of HttpException
            // then attempt to parse error data from response body
            is HttpException -> {
                // handle UNAUTHORIZED situation (when token expired)
                if (throwable.code() == 401) {
                    getHttpError(throwable.response()?.errorBody())
                } else if (throwable.code() == 403) {
                    ErrorMessage(
                        status = HttpErrors.Forbidden,
                        message = "You do not have permission to perform this operation"
                    )
                } else if (throwable.code() == 500) {
                    ErrorMessage(
                        status = HttpErrors.ServerError,
                        message = "Sorry, the server has a technical problem, please try again in a few moments"
                    )
                } else {
                    getHttpError(throwable.response()?.errorBody())
                }
            }

            // handle api call timeout error
            is SocketTimeoutException -> {
                ErrorMessage(
                    status = HttpErrors.TimeOut,
                    message = throwable.message.toString()
                )
            }

            // handle connection error
            is IOException -> {
                ErrorMessage(
                    status = HttpErrors.NotDefined,
                    message = throwable.message.toString()
                )
            }
            else -> null
        }
        return errorMessage ?: ErrorMessage(
            status = HttpErrors.NotDefined,
            message = throwable?.message.toString()
        )
    }

    /**
     * attempts to parse http response body and get error data from it
     *
     * @param body retrofit response body
     * @return returns an instance of [ErrorModel] with parsed data or NOT_DEFINED status
     */
    private fun getHttpError(body: ResponseBody?): ErrorMessage {
        return try {
            val error = gson.fromJson(body?.string(), ErrorBody::class.java)
            error.toDomain()
        } catch (e: Throwable) {
            e.printStackTrace()
            ErrorMessage(status = HttpErrors.BadResponse, message = e.message.toString())
        }

    }
}