package com.example.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface BiometricAvailability {
    data object Ready : BiometricAvailability
    data class NotAvailable(val reason: String) : BiometricAvailability
}

object BiometricAuthHelper {

    fun checkAvailability(activity: FragmentActivity, allowPinFallback: Boolean): BiometricAvailability {
        val manager = BiometricManager.from(activity)
        val authenticators = if (allowPinFallback) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Ready
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.NotAvailable("No fingerprint sensor hardware found on this device.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.NotAvailable("Fingerprint sensor is temporarily unavailable.")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.NotAvailable("No fingerprint enrolled in device Security settings.")
            else -> BiometricAvailability.NotAvailable("Biometric authentication unavailable.")
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Connect Live",
        subtitle: String = "Verify your fingerprint to enter the app",
        allowPinFallback: Boolean = true,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Fingerprint did not match. Please try again.")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)

        if (allowPinFallback) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            builder.setNegativeButtonText("Cancel")
        }

        runCatching {
            prompt.authenticate(builder.build())
        }.onFailure { err ->
            onError(err.localizedMessage ?: "Unable to launch fingerprint prompt.")
        }
    }
}
