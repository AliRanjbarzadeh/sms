/*
 * SMS Import / Export: a simple Android app for importing and exporting SMS messages from and to JSON files.
 * Copyright (c) 2021-2022 Thomas More
 *
 * This file is part of SMS Import / Export.
 *
 * SMS Import / Export is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMS Import / Export is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMS Import / Export.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.tmo1.sms_ie.base

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Runnable
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileInputStream

/**
 * Created by Anonymous on 11/30/2022 AD.
 */

class FileUploader(
    retrofit: Retrofit,
    private val fileUploaderCallback: FileUploaderCallback
) {

    private var uploadRepository: UploadRepository

    init {
        uploadRepository = retrofit.create(UploadRepository::class.java)
    }

    fun uploadFile(file: File) {
        val prRequestBody = PRRequestBody(file)
        val filePart = MultipartBody.Part.createFormData("backup", file.name, prRequestBody)
        uploadRepository.uploadFile(filePart)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    if (response.isSuccessful) {
                        val body = response.body()?.string()
                        body?.also {
                            try {
                                val jsonObject = JSONObject(body)
                                fileUploaderCallback.onFinish(
                                    jsonObject.getString("msg") ?: "Upload success"
                                )
                            } catch (_: Exception) {
                                fileUploaderCallback.onFinish(
                                    "Upload success"
                                )
                            }
                        } ?: kotlin.run {
                            fileUploaderCallback.onFinish(
                                "Upload success"
                            )
                        }
                    } else {
                        fileUploaderCallback.onError(Exception("server error"))
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.d("FILE_UPLOADER", "onFailure: $call")
                    fileUploaderCallback.onError(Exception(t))
                }
            })
    }

    private interface UploadRepository {
        @Multipart
        @POST("upload.php")
        fun uploadFile(@Part file: MultipartBody.Part, @Part("folder_name") name: RequestBody? = null): Call<ResponseBody>
    }

    interface FileUploaderCallback {
        fun onError(e: Exception)
        fun onFinish(message: String)
        fun onProgressUpdate(currentPercent: Int)
    }

    inner class PRRequestBody(private val mFile: File) : RequestBody() {
        private val DEFAULT_BUFFER_SIZE = 2048

        override fun contentType(): MediaType? {
            return "*/*".toMediaTypeOrNull()
        }

        override fun contentLength(): Long {
            return mFile.length()
        }

        override fun writeTo(sink: BufferedSink) {
            val fileLength = mFile.length()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val inputStream = FileInputStream(mFile)
            var uploaded = 0L

            try {
                var read: Int
                val handler = Handler(Looper.getMainLooper())

                while (inputStream.read(buffer).also { read = it } != -1) {
                    handler.post(ProgressUpdater(uploaded, fileLength))
                    uploaded += read.toLong()
                    sink.write(buffer, 0, read)
                }
            } catch (e: Exception) {
                Log.e("UPLOAD_FILE_LOG", "writeTo: ${e.message}")
            } finally {
                inputStream.close()
            }
        }
    }

    inner class ProgressUpdater(private val uploaded: Long, private val total: Long) : Runnable {
        override fun run() {
            val currentPercent = (100 * uploaded / total).toInt() + 1
            fileUploaderCallback.onProgressUpdate(currentPercent)
        }

    }
}