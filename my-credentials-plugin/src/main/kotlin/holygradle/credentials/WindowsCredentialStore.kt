package holygradle.credentials

import holygradle.custom_gradle.plugin_apis.CredentialStore
import holygradle.custom_gradle.plugin_apis.Credentials
import holygradle.jna.platform.win32.W32Errors
import holygradle.jna.platform.win32.Win32Exception
import holygradle.jna.platform.win32.WinCred
import holygradle.jna.platform.win32.WinCredUtil
import holygradle.jna.platform.win32.WinError

class WindowsCredentialStore : CredentialStore {
    override fun readCredential(key: String): Credentials? {
        val credential: WinCredUtil.Credential
        try {
            credential = WinCredUtil.readCredential(key)
        } catch (e: Win32Exception) {
            if (e.hr.toInt() == W32Errors.HRESULT_FROM_WIN32(WinError.ERROR_NOT_FOUND).toInt()) {
                return null
            }
            throw e
        }
        return Credentials(credential.username, credential.password)
    }

    override fun writeCredential(key: String, credentials: Credentials) {
        WinCredUtil.writeCredential(key, credentials.username, credentials.password, WinCred.CRED_PERSIST_ENTERPRISE)
    }
}
