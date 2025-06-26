package substratum.theme.template

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import substratum.theme.template.AdvancedConstants.OTHER_THEME_SYSTEMS
import substratum.theme.template.AdvancedConstants.ORGANIZATION_THEME_SYSTEMS
import substratum.theme.template.AdvancedConstants.SHOW_DIALOG_REPEATEDLY
import substratum.theme.template.AdvancedConstants.SHOW_LAUNCH_DIALOG
import substratum.theme.template.ThemeFunctions.getSelfSignature
import substratum.theme.template.ThemeFunctions.isCallingPackageAllowed
import substratum.theme.template.ThemeFunctions.checkApprovedSignature
import com.github.javiersantos.piracychecker.allow
import com.github.javiersantos.piracychecker.callback
import com.github.javiersantos.piracychecker.doNotAllow
import com.github.javiersantos.piracychecker.enums.InstallerID
import com.github.javiersantos.piracychecker.onError
import com.github.javiersantos.piracychecker.piracyChecker
import com.github.javiersantos.piracychecker.utils.apkSignatures
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton

class SubstratumLauncher : Activity() {

    private val debug = false
    private val tag = "SubstratumThemeReport"
    private val substratumIntentData = "projekt.substratum.THEME"
    private val getKeysIntent = "projekt.substratum.GET_KEYS"
    private val receiveKeysIntent = "projekt.substratum.RECEIVE_KEYS"

    private val themePiracyCheck by lazy {
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* STEP 1: Block hijackers */
        val caller = callingActivity!!.packageName
        val organizationsSystem = ORGANIZATION_THEME_SYSTEMS.contains(caller)
        val supportedSystem = organizationsSystem || OTHER_THEME_SYSTEMS.contains(caller)
        if (!BuildConfig.SUPPORTS_THIRD_PARTY_SYSTEMS && !supportedSystem) {
            Log.e(tag, "This theme does not support the launching theme system. [HIJACK] ($caller)")
            Toast.makeText(this,
                String.format(getString(R.string.unauthorized_theme_client_hijack), caller),
                Toast.LENGTH_LONG).show()
            finish()
        }
        if (debug) {
            Log.d(tag, "'$caller' has been authorized to launch this theme. (Phase 1)")
        }

        /* STEP 2: Ensure that our support is added where it belongs */
        val action = intent.action
        val sharedPref = getPreferences(MODE_PRIVATE)
        var verified = false
        if ((action == substratumIntentData) or (action == getKeysIntent)) {
            // Assume this called from organization's app
            if (organizationsSystem) {
                verified = when {
                    BuildConfig.ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS -> true
                    else -> checkApprovedSignature(this, caller)
                }
            }
        } else {
            OTHER_THEME_SYSTEMS
                .filter { action?.startsWith(prefix = it, ignoreCase = true) ?: false }
                .forEach { _ -> verified = true }
        }
        if (!verified) {
            Log.e(tag, "This theme does not support the launching theme system. ($action)")
            Toast.makeText(this, R.string.unauthorized_theme_client, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (debug) {
            Log.d(tag, "'$action' has been authorized to launch this theme. (Phase 2)")
        }


        /* STEP 3: Do da thang */
        if (SHOW_LAUNCH_DIALOG) run {
            if (SHOW_DIALOG_REPEATEDLY) {
                showDialog()
                sharedPref.edit { remove("dialog_showed") }
            } else if (!sharedPref.getBoolean("dialog_showed", false)) {
                showDialog()
                sharedPref.edit { putBoolean("dialog_showed", true) }
            } else {
                startAntiPiracyCheck()
            }
        } else {
            startAntiPiracyCheck()
        }
    }

    private fun startAntiPiracyCheck() {
        if (BuildConfig.BASE_64_LICENSE_KEY.isEmpty() && debug && !BuildConfig.DEBUG) {
            Log.e(tag, apkSignatures.toString())
        }

        if (!themePiracyCheck) {
            piracyChecker {
                if (BuildConfig.ENFORCE_GOOGLE_PLAY_INSTALL) {
                    enableInstallerId(InstallerID.GOOGLE_PLAY)
                }
                if (BuildConfig.BASE_64_LICENSE_KEY.isNotEmpty()) {
                    enableGooglePlayLicensing(BuildConfig.BASE_64_LICENSE_KEY)
                }
                if (BuildConfig.APK_SIGNATURE_PRODUCTION.isNotEmpty()) {
                    enableSigningCertificates(BuildConfig.APK_SIGNATURE_PRODUCTION)
                }
                callback {
                    allow {
                        val returnIntent = if (intent.action == getKeysIntent) {
                            Intent(receiveKeysIntent)
                        } else {
                            Intent()
                        }

                        val themeName = getString(R.string.ThemeName)
                        val themeAuthor = getString(R.string.ThemeAuthor)
                        val themePid = packageName
                        returnIntent.putExtra("theme_name", themeName)
                        returnIntent.putExtra("theme_author", themeAuthor)
                        returnIntent.putExtra("theme_pid", themePid)
                        returnIntent.putExtra("theme_debug", BuildConfig.DEBUG)
                        returnIntent.putExtra("theme_piracy_check", themePiracyCheck)
                        returnIntent.putExtra("encryption_key", BuildConfig.DECRYPTION_KEY)
                        returnIntent.putExtra("iv_encrypt_key", BuildConfig.IV_KEY)

                        val callingPackage = intent.getStringExtra("calling_package_name")
                        if (!callingPackage?.let { isCallingPackageAllowed(it) }!!) {
                            finish()
                        } else {
                            returnIntent.`package` = callingPackage
                        }

                        if (intent.action == substratumIntentData) {
                            setResult(getSelfSignature(applicationContext), returnIntent)
                        } else if (intent.action == getKeysIntent) {
                            returnIntent.action = receiveKeysIntent
                            sendBroadcast(returnIntent)
                        }
                        destroy()
                        finish()
                    }
                    doNotAllow { _, _ ->
                        val parse = String.format(
                            getString(R.string.toast_unlicensed),
                            getString(R.string.ThemeName))
                        Toast.makeText(this@SubstratumLauncher, parse, Toast.LENGTH_SHORT).show()
                        destroy()
                        finish()
                    }
                    onError { error ->
                        Toast.makeText(this@SubstratumLauncher, error.toString(), Toast.LENGTH_LONG).show()
                        destroy()
                        finish()
                    }
                }
                // Save result to shared preferences, remove this line if you don't want to save the result locally
                saveResultToSharedPreferences("piracy_checker", "piracy_checker_result")
            }.start()
        } else {
            Toast.makeText(this, R.string.unauthorized, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun showDialog() {

        val dialogInset = (48 * resources.displayMetrics.density).toInt()
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_LaunchDialog)
            .setBackground(getDrawable(R.drawable.dialog_background))
            .setBackgroundInsetStart(dialogInset)
            .setBackgroundInsetEnd(dialogInset)
            .setCancelable(false)

        val view = layoutInflater.inflate(R.layout.launch_dialog, null)
        dialog.setView(view)

        // Set up the buttons
        val rateButton = view.findViewById<MaterialButton>(R.id.rate)
        rateButton.setOnClickListener {
            urlButtonOnClick(R.string.rate_url)
        }

        val emailButton = view.findViewById<MaterialButton>(R.id.email)
        emailButton.setOnClickListener {
            urlButtonOnClick(R.string.email_url)
        }

        val websiteButton = view.findViewById<MaterialButton>(R.id.website)
        websiteButton.setOnClickListener {
            urlButtonOnClick(R.string.website_url)
        }

        val negativeButton = view.findViewById<Button>(R.id.btn_cancel)
        val negativeText = getString(R.string.launch_dialog_negative)

        negativeButton.visibility = if (negativeText.isEmpty()) View.GONE else View.VISIBLE
        negativeButton.setOnClickListener {
            // Open the URL if it exists, otherwise finish the activity
            val url = getString(R.string.launch_dialog_negative_url)
            if (url.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = url.toUri()
                startActivity(intent)
            } else {
                finish()
            }
        }

        val positiveButton = view.findViewById<Button>(R.id.btn_continue)
        positiveButton.setOnClickListener {
            startAntiPiracyCheck()
        }

        dialog.show()
    }

    private fun urlButtonOnClick(urlId: Int) {
        val url = getString(urlId)
        if (url.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.missing_url_toast, Toast.LENGTH_SHORT).show()
        }
    }
}