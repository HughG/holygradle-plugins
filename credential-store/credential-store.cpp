#include "stdafx.h"

#include <windows.h>
#include <wincred.h>
#include <tchar.h>
#pragma hdrstop

int _tmain(int argc, _TCHAR* argv[]) {
    const _TCHAR* username = NULL;
    const _TCHAR* key = NULL;
    const _TCHAR* value = NULL;
    bool storing = true;
    
    if (argc == 3) {
        storing = false;
        username = argv[1];
        key = argv[2];
    } else if (argc == 4) {
        storing = true;
        username = argv[1];
        key = argv[2];
        value = argv[3];
    } else {
        wprintf(L"Usage for storing: <username> <key> <value>\n");
        wprintf(L"Usage for retrieving: <username> <key>\n");
        exit(1);
    }
    
    _TCHAR* target_username = new _TCHAR[wcslen(username)+1];
    wsprintf(target_username, L"%s", username);
    //wprintf(L"Address: %s\n", target_address);
    
    _TCHAR* target_key  = new _TCHAR[wcslen(key)+1];
    wsprintf(target_key, L"%s", key);
    //wprintf(L"Target: %s\n", target_name);
    
    if (storing) { //--- SAVE
        _TCHAR* target_value = new _TCHAR[wcslen(value)+1];
        wsprintf(target_value, L"%s", value);

        CREDENTIALW cred = {0};
        cred.Type = CRED_TYPE_GENERIC;
        cred.TargetName = target_key;
        cred.CredentialBlobSize = (DWORD) (wcslen(target_value)*2)+1;
        cred.CredentialBlob = (LPBYTE) target_value;
        cred.Persist = CRED_PERSIST_LOCAL_MACHINE;
        cred.UserName = target_username;

        BOOL ok = ::CredWriteW(&cred, 0);
        if (ok) {
            wprintf(L"    Cached key for %s, %s.\n", username, key);
        } else {
            DWORD err = ::GetLastError();
            wprintf(L"Failed to cache key for %s, %s. Errno: %d.\n", username, key, err);
            exit(err);
        }
    } else { //--- RETRIEVE
        PCREDENTIALW pcred;
        BOOL ok = ::CredReadW(target_key, CRED_TYPE_GENERIC, 0, &pcred);
        if (!ok) {
            wprintf (L"CredRead() - errno %d\n", ok ? 0 : ::GetLastError());
            exit(1);
        }
        wprintf (L"%s", (char*)pcred->CredentialBlob);
        // must free memory allocated by CredRead()!
        ::CredFree (pcred);
    }

    //Sleep(500000);
}
