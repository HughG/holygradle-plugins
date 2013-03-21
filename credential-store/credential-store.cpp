#include "stdafx.h"

#include <windows.h>
#include <wincred.h>
#include <tchar.h>
#include <locale>
#include <iostream>
#include <string>
#include <algorithm>
#pragma hdrstop

using namespace std;

BOOL StoreCredential(wstring& target_address, wstring& target_password, wstring& target_user) {
    CREDENTIALW cred = {0};
    cred.Type = CRED_TYPE_GENERIC;
    cred.TargetName = (LPWSTR)target_address.c_str();
    cred.CredentialBlobSize = (DWORD) (target_password.size()*2);
    cred.CredentialBlob = (LPBYTE) target_password.c_str();
    cred.Persist = CRED_PERSIST_LOCAL_MACHINE;
    cred.UserName = (LPWSTR)target_user.c_str();

    return ::CredWriteW(&cred, 0);
}

BOOL DeleteCredential(LPWSTR target_address) {
    return ::CredDelete(target_address, CRED_TYPE_GENERIC, 0);
}

void echo(bool on = true) {
    DWORD  mode;
    HANDLE hConIn = GetStdHandle( STD_INPUT_HANDLE );
    GetConsoleMode( hConIn, &mode );
    mode = on
        ? (mode |   ENABLE_ECHO_INPUT )
        : (mode & ~(ENABLE_ECHO_INPUT));
    SetConsoleMode( hConIn, mode );
}

void PrintCredential(PCREDENTIAL pCredential) {
    //Write the Credential information into the standard output.
    wcout << "*********************************************" << endl;
    printf(	"Flags:   %d\r\n"\
        "Type:    %d\r\n"\
        "Name:    %ls\r\n"\
        "Comment: %ls\r\n"\
        "Persist: %d\r\n"\
        "User:    %ls\r\n",
        pCredential->Flags,
        pCredential->Type,
        pCredential->TargetName, 
        pCredential->Comment,
        pCredential->Persist,
        pCredential->UserName);

    wcout << "Data: " << endl;

    char szHexBuffer[256] = "";
    char szAsciiBuffer[256] = "";
    char szHex[16];
    char szAscii[2];
    DWORD dwByte;

    //Write the credential's data as Hex Dump.
    for (dwByte = 0; dwByte < pCredential->CredentialBlobSize; dwByte++) {
        BYTE byte1 = pCredential->CredentialBlob[dwByte];
        sprintf(szHex, "%2.2X ", byte1);
        szAscii[1] = '\0';

        if (byte1 >= 32 && byte1 < 128)
            szAscii[0] = (UCHAR)byte1;
        else
            szAscii[0] = ' ';

        strcat(szHexBuffer, szHex);
        strcat(szAsciiBuffer, szAscii);

        if (dwByte == pCredential->CredentialBlobSize - 1 
            || dwByte % 16 == 15)
        {
            printf("%-50s %s\r\n", szHexBuffer, szAsciiBuffer);
            szHexBuffer[0] = '\0';
            szAsciiBuffer[0] = '\0';
        }
    }

    wcout << "*********************************************" << endl << endl << endl;
}

void ReadAndPrintCredential(wstring& target_key) {
    PCREDENTIALW pcred;
    BOOL ok = ::CredReadW(target_key.c_str(), CRED_TYPE_GENERIC, 0, &pcred);
    if (!ok) {
        wcout << "CredRead() - errno " << (ok ? 0 : ::GetLastError()) << endl;
        exit(1);
    }
    wcout << pcred->UserName << "&&&" << wstring((LPWSTR)pcred->CredentialBlob, pcred->CredentialBlobSize/2);

    // must free memory allocated by CredRead()!
    ::CredFree (pcred);
}

// templated version of my_equal so it could work with both char and wchar_t
template<typename charT>
struct my_equal {
    my_equal( const std::locale& loc ) : loc_(loc) {}
    bool operator()(charT ch1, charT ch2) {
        return std::toupper(ch1, loc_) == std::toupper(ch2, loc_);
    }
private:
    const std::locale& loc_;
};

// find substring (case insensitive)
template<typename T>
int ci_find_substr( const T& str1, const T& str2, const std::locale& loc = std::locale() )
{
    T::const_iterator it = std::search( str1.begin(), str1.end(), 
        str2.begin(), str2.end(), my_equal<T::value_type>(loc) );
    if ( it != str1.end() ) return it - str1.begin();
    else return -1; // not found
}

BOOL IsMercurialCredential(wstring& target_name, wstring& username) {
    BOOL is_mercurial = FALSE;
    if (target_name.size() > username.size() &&
        ci_find_substr(target_name, username) == 0 &&
        target_name.find(L"@@") == username.size() &&
        target_name.find(L"@Mercurial") == target_name.size() - 10
    ) {
        // A Mercurial credential should:
        //  begin with username
        //  be followed by "@@"
        //  end with "@Mercurial"
        is_mercurial = true;
    }
    return is_mercurial;
}

BOOL IsIntrepidCredential(wstring& target_name) {
    return target_name.find(L"Intrepid - ") == 0;
}

int UpdateAllCredentials(wstring& username, wstring& password) {
    PCREDENTIAL *pCredArray = NULL;
    DWORD dwCount = 0;

    DeleteCredential(L"Mercurial");

    int update_count = 0;

    //Load all credentials into array.
    if (::CredEnumerate(NULL, 0, &dwCount, &pCredArray)) {
        for (DWORD dwIndex = 0; dwIndex < dwCount; dwIndex++) {
            PCREDENTIAL pCredential = pCredArray[dwIndex];

            wstring target_name(pCredential->TargetName);

            //PrintCredential(pCredential);

            if (IsMercurialCredential(target_name, username)) {
                wcout << "Updated: " << target_name << endl;

                StoreCredential(target_name, password, wstring(pCredential->UserName));
                update_count++;
            } else if (IsIntrepidCredential(target_name)) {
                // An Intrepid credential starts with "Intrepid - "

                // Ask user for password
                wcout << "Update '" << target_name << "' with the password you supplied? If so, type 'y': ";
                string confirm;
                std::getline(std::cin, confirm);
                if (confirm[0] == 'y' || confirm[0] == 'Y') {
                    wcout << "Updated: " << target_name << endl;
                    StoreCredential(target_name, password, wstring(pCredential->UserName));
                    update_count++;
                } else {
                    wcout << "Skipped: " << target_name << endl;
                }
            }
        }

        //Free the credentials array.
        ::CredFree(pCredArray);
    }

    return update_count;
}

int _tmain(int argc, _TCHAR* argv[]) {

    if (argc == 1) {
        cout << "This program will update all of your Mercurial and Intrepid credentials ";
        cout << "in the Windows Credential Manager. You will be prompted to confirm ";
        cout << "updating any Intrepid credentials." << endl << endl;
        cout << "Usage for storing: <key> <username> <value>" << endl;
        cout << "Usage for retrieving: <key>" << endl << endl;

        DWORD usernameLen = 100;
        TCHAR usernameBuffer[100];
        GetUserName(usernameBuffer, &usernameLen);
        wstring username(usernameBuffer);
        wcout << "Using username: " << username << endl;

        wstring password;
        while (password.size() == 0) {
            cout << "Enter your password: ";
            string pw;
            echo(false);
            std::getline(std::cin, pw);
            echo(true);
            wcout << endl;
            password = wstring(pw.begin(), pw.end());
        }

        int update_count = UpdateAllCredentials(username, password);
        wcout << "---------------------------------------------------------\n";
        wcout << "Update complete - " << update_count << " credentials modified. Press enter...";
        cin.get();
    } else if (argc == 2) {
        wstring readKey(argv[1]);
        ReadAndPrintCredential(readKey);
    } else if (argc == 4) {
        wstring writeKey(argv[1]);
        wstring username(argv[2]);
        wstring password(argv[3]);
        StoreCredential(writeKey, password, username);
    } else {
        exit(1);
    }
}
