#include "stdafx.h"

#include <windows.h>
#include <wincred.h>
#include <tchar.h>
#pragma hdrstop

BOOL StoreCredential(const _TCHAR* target_address, const _TCHAR* target_password, const _TCHAR* target_name) {

    _TCHAR* salted_password = new _TCHAR[wcslen(target_password)+wcslen(target_address)+1];
    wsprintf(salted_password, L"%s%s", target_password, target_address);

    CREDENTIALW cred = {0};
    cred.Type = CRED_TYPE_GENERIC;
    cred.TargetName = (LPWSTR)target_address;
    // Set the blob size to the size of the actual password (i.e. 2 bytes per character), even 
    // though the actual blob we store in the credential manager has the address appended
    // as a salt e.g. <password><username>@@<url>
    cred.CredentialBlobSize = (DWORD) (wcslen(target_password)*2);
    cred.CredentialBlob = (LPBYTE) salted_password;
    cred.Persist = CRED_PERSIST_LOCAL_MACHINE;
    cred.UserName = (LPWSTR)target_name;

    return ::CredWriteW(&cred, 0);
}

int _tmain(int argc, _TCHAR* argv[]) {
    const _TCHAR* url = NULL;
    const _TCHAR* username = NULL;
    const _TCHAR* password = NULL;
    
    if (argc == 4) {
        url = argv[1];
        username = argv[2];
        password = argv[3];
    } else {
        wprintf(L"Usage: <Mercurial URL> <username> <password>\n");
        exit(1);
    }

    _TCHAR* target_name = new _TCHAR[wcslen(url)+wcslen(username)+3];
    wsprintf(target_name, L"%s@@%s", username, url);
    //wprintf(L"Target: %s\n", target_name);

    _TCHAR* target_address = new _TCHAR[wcslen(url)+wcslen(username)+20];
    wsprintf(target_address, L"%s@@%s@Mercurial", username, url);
    //wprintf(L"Address: %s\n", target_address);

    { //--- SAVE
        BOOL ok = StoreCredential(L"Mercurial", password, target_name);
        if (ok) {
            ok = StoreCredential(target_address, password, target_name);
        }
        if (ok) {
            wprintf(L"    Cached Mercurial credentials for %s, %s.\n", username, url);
        } else {
            DWORD err = ::GetLastError();
            wprintf(L"Failed to cache Mercurial credentials for %s, %s. Errno: %d.\n", username, url, err);
            exit(err);
        }
    }
    /*{ //--- RETRIEVE
        PCREDENTIALW pcred;
        BOOL ok = ::CredReadW(target_address, CRED_TYPE_GENERIC, 0, &pcred);
        wprintf (L"CredRead() - errno %d\n", ok ? 0 : ::GetLastError());
        if (!ok) exit(1);
        wprintf (L"Read username = '%s', password='%s' (%d bytes)\n", 
                 pcred->UserName, (char*)pcred->CredentialBlob, pcred->CredentialBlobSize);
        // must free memory allocated by CredRead()!
        ::CredFree (pcred);
    }

    Sleep(500000);*/
}
