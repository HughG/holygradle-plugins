package holygradle.credentials

import holygradle.custom_gradle.plugin_apis.CredentialStore
import holygradle.custom_gradle.plugin_apis.Credentials
import holygradle.jna.platform.win32.W32Errors
import holygradle.jna.platform.win32.Win32Exception
import holygradle.jna.platform.win32.WinCred
import holygradle.jna.platform.win32.WinCredUtil
import holygradle.jna.platform.win32.WinError

class WindowsCredentialStore implements CredentialStore {
    @Override
    public Credentials readCredential(String key) {
        final credential
        try {
            credential = WinCredUtil.readCredential(key)
        } catch (Win32Exception e) {
            if (e.HR.intValue() == W32Errors.HRESULT_FROM_WIN32(WinError.ERROR_NOT_FOUND).intValue()) {
                return null
            }
            throw e
        }
        return new Credentials(credential.username, credential.password)
    }

    @Override
    public Credentials writeCredential(String key, Credentials credentials) {
        WinCredUtil.writeCredential(key, credentials.userName, credentials.password, WinCred.CRED_PERSIST_ENTERPRISE)
    }
}
