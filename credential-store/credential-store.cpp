#include "stdafx.h"

#include <windows.h>
#include <wincred.h>
#include <lmcons.h>
#include <tchar.h>
#include <locale>
#include <iostream>
#include <sstream>
#include <fstream>
#include <string>
#include <map>
#include <list>
#include <algorithm>
#include <functional>
#include <cctype>

#pragma hdrstop

#define INPUT_LENGTH_LIMIT 256
#define GRADLE_USER_HOME_ENV_VAR_NAME L"GRADLE_USER_HOME"
#define USERPROFILE_ENV_VAR_NAME L"USERPROFILE"
#define HOLY_GRADLE_DIR_NAME L"holygradle"
#define CREDENTIAL_BASIS_FILE_NAME L"credential-bases.txt"
#define HOLY_GRADLE_CREDENTIAL_PREFIX L"Intrepid - "

using namespace std;

void echo(bool on) {
    DWORD  mode;
    HANDLE hConIn = ::GetStdHandle(STD_INPUT_HANDLE);
    ::GetConsoleMode(hConIn, &mode);
    mode = on
        ? (mode | ENABLE_ECHO_INPUT)
        : (mode & ~(ENABLE_ECHO_INPUT));
    ::SetConsoleMode(hConIn, mode);
}

// Trim whitespace from both ends of the string.  This function intentionally copies its input because it
// internally erases characters in place.  Based on http://stackoverflow.com/a/217605.
wstring trim(wstring s) {
    s.erase(s.begin(), find_if(s.begin(), s.end(),
        not1(ptr_fun<int, int>(isspace))));
    s.erase(find_if(s.rbegin(), s.rend(),
        not1(ptr_fun<int, int>(isspace))).base(), s.end());
    return s;
}

void StoreCredential(wstring& target_address, wstring& target_user, wstring& target_password) {
    CREDENTIALW cred = {0};
    cred.Type = CRED_TYPE_GENERIC;
    cred.TargetName = (LPWSTR)target_address.c_str();
    cred.CredentialBlobSize = (DWORD) (target_password.size()*2);
    cred.CredentialBlob = (LPBYTE) target_password.c_str();
    cred.Persist = CRED_PERSIST_ENTERPRISE;
    cred.UserName = (LPWSTR)target_user.c_str();

    bool result = (::CredWriteW(&cred, 0) != FALSE);
    if (result) {
        wcout << "Updated: " << target_address << endl;
    } else {
        wcout << "ERROR: Failed to update: " << target_address << endl;
    }
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
        sprintf_s(szHex, 16, "%2.2X ", byte1);
        szAscii[1] = '\0';

        if (byte1 >= 32 && byte1 < 128)
            szAscii[0] = (UCHAR)byte1;
        else
            szAscii[0] = ' ';

        strcat_s(szHexBuffer, 256, szHex);
        strcat_s(szAsciiBuffer, 256, szAscii);

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

void ReadAndPrintCredential(const wstring& target_key) {
    PCREDENTIALW pcred;
    BOOL ok = ::CredReadW(target_key.c_str(), CRED_TYPE_GENERIC, 0, &pcred);
    if (!ok) {
        wcout << "CredRead() - errno " << (ok ? 0 : ::GetLastError()) << endl;
        exit(1);
    }
    wcout << pcred->UserName << "&&&" << wstring((LPWSTR)pcred->CredentialBlob, pcred->CredentialBlobSize / 2);

    // must free memory allocated by CredRead()!
    ::CredFree(pcred);
}

wstring GetCredentialUsername(const wstring& target_key) {
    PCREDENTIALW pcred;
    BOOL ok = ::CredReadW(target_key.c_str(), CRED_TYPE_GENERIC, 0, &pcred);
    if (!ok) {
        wcout << "CredRead() - errno " << (ok ? 0 : ::GetLastError()) << endl;
        exit(1);
    }
    wstring result(pcred->UserName);

    // Must free memory allocated by CredRead()!
    ::CredFree(pcred);

    return result;
}

// templated version of my_equal so it could work with both char and wchar_t
template<typename charT>
struct my_equal {
    my_equal( const locale& loc ) : loc_(loc) {}
    bool operator()(charT ch1, charT ch2) {
        return toupper(ch1, loc_) == toupper(ch2, loc_);
    }
private:
    const locale& loc_;
};

// find substring (case insensitive)
template<typename T>
int ci_find_substr( const T& str1, const T& str2, const locale& loc = locale() )
{
    T::const_iterator it = search( str1.begin(), str1.end(), 
        str2.begin(), str2.end(), my_equal<T::value_type>(loc) );
    if ( it != str1.end() ) return it - str1.begin();
    else return -1; // not found
}

bool IsMercurialCredential(const wstring& target_name, const wstring& username) {
    // A Mercurial credential name should have the following components in order:
    //  a non-zero-length username
    //  "@@"
    //  a non-zero-length repo URL
    //  "@Mercurial"
    //
    // Mercurial accepts, and older versions store, the username data in the credential as just the username,
    // whereas newer versions store it as "<username>@@<repo_url>" (without a trailing "@Mercurial").

    size_t double_at_pos = target_name.find(L"@@");
    size_t at_mercurial_pos = target_name.find(L"@Mercurial");
    bool matches_format = (
        double_at_pos > 0 &&
        double_at_pos <= (at_mercurial_pos - 3) &&
        at_mercurial_pos == target_name.size() - 10
    );
    // Need to check matches_format is true before calling GetCredentialUsername or we may get
    // ERROR_NOT_FOUND from CredRead, if the target_name is not for a CRED_TYPE_GENERIC credential.
    return matches_format &&
        (wcsncmp(GetCredentialUsername(target_name).c_str(), username.c_str(), double_at_pos) == 0);
}

bool IsGitCredential(const wstring& target_name, const wstring& username) {
    // git://<scheme>://<username>@<hostname> or git:<scheme>://<hostname> 
    return (target_name.find(L"git:") == 0) && (GetCredentialUsername(target_name) == username);
}

bool IsIntrepidCredential(const wstring& target_name, const wstring& username) {
    return (target_name.find(HOLY_GRADLE_CREDENTIAL_PREFIX) == 0) && (GetCredentialUsername(target_name) == username);
}

wstring GetIntrepidCredentialName(const wstring& target_name) {
    return target_name.substr(11);
}

bool HasBasis(const map<wstring, list<wstring>>& bases, wstring credential_name) {
    for (auto b = bases.begin(); b != bases.end(); ++b) {
        auto basis_credentials = b->second;
        auto found_credential_it = find(basis_credentials.begin(), basis_credentials.end(), credential_name);
        if (found_credential_it != basis_credentials.end()) {
            return true;
        }
    }
    return false;
}

void RequestUsernameAndPassword(wstring& username, wstring& password)
{
    DWORD usernameLen = UNLEN;
    TCHAR usernameBuffer[UNLEN];
    GetUserName(usernameBuffer, &usernameLen);
    wcout << L"Username [ENTER to accept default '" << usernameBuffer << L"']: ";
    getline(wcin, username);
    if (username.empty()) {
        username = usernameBuffer;
    }
    if (username.empty()) {
        wcerr << L"ERROR: Empty username" << endl;
        exit(1);
    }

    cout << "Password: ";
    echo(false);
    getline(wcin, password);
    echo(true);
    wcout << endl;
    if (password.empty()) {
        wcerr << L"ERROR: Empty password" << endl;
        exit(1);
    }
}

void UpdateAllCredentials(wstring& username, wstring& password) {
    PCREDENTIAL *pCredArray = NULL;
    DWORD dwCount = 0;

    //Load all credentials into array.
    if (::CredEnumerate(NULL, 0, &dwCount, &pCredArray)) {
        for (DWORD dwIndex = 0; dwIndex < dwCount; dwIndex++) {
            PCREDENTIAL pCredential = pCredArray[dwIndex];

            wstring target_name(pCredential->TargetName);

            //PrintCredential(pCredential);

            if (IsMercurialCredential(target_name, username) ||
                IsGitCredential(target_name, username) ||
                IsIntrepidCredential(target_name, username)
            ) {
                // Note: We use the existing credential username here, not the username parameter, because
                // some (Mercurial) credentials have the username parameter value as a substring rather than
                // the full string.
                wstring credential_user_name = wstring(pCredential->UserName);
                StoreCredential(target_name, credential_user_name, password);
            }
        }

        //Free the credentials array.
        ::CredFree(pCredArray);
    }
}

wstring GetCredentialBasisFileName() {
    bool found_home = false;
    wchar_t gradle_user_home[MAX_PATH];
    if (SUCCEEDED(::GetEnvironmentVariable(L"GRADLE_USER_HOME", gradle_user_home, MAX_PATH))) {
        found_home = true;
    } else {
        if (::GetLastError() == ERROR_ENVVAR_NOT_FOUND) {
            if (SUCCEEDED(::GetEnvironmentVariable(L"USERPROFILE", gradle_user_home, MAX_PATH))) {
                found_home = true;
            }
        }
    }

    if (!found_home) {
        wcerr << L"Failed to read environment variable " GRADLE_USER_HOME_ENV_VAR_NAME L" or "
            USERPROFILE_ENV_VAR_NAME L"." << endl;
        wcerr << L"One of these must be set to locate the credential basis file." << endl;
    }

    wstring home(gradle_user_home);
    wstringstream basis_file_name;
    basis_file_name << home;
    wchar_t last_char = home[home.size() - 1];
    if (!(last_char == L'/' || last_char == L'\\')) {
        basis_file_name << L'\\';
    }
    basis_file_name << HOLY_GRADLE_DIR_NAME << L'\\' << CREDENTIAL_BASIS_FILE_NAME;
    return basis_file_name.str();
}

map<wstring, list<wstring>> ReadBases(const wstring& credentialBasisFileName) {
    map<wstring, list<wstring>> bases;
    wifstream in(credentialBasisFileName);
    wstring current_basis;
    wstring line;
    unsigned long line_index = 0;
    while (getline(in, line)) {
        ++line_index;
        wstring trimmed_line(trim(line));
        if (trimmed_line.empty() || line[0] == L'#') {
            // Ignore blank and comment lines.
            continue;
        } else if (isspace(line[0])) {
            if (current_basis.empty()) {
                wcerr << L"WARNING: Ignoring entry '" << trimmed_line
                    << L"' on line " << to_wstring(line_index) << L" of " << credentialBasisFileName
                    << L" because no basis line has been encountered yet." << endl;
            } else {
                bases[current_basis].push_back(trimmed_line);
            }
        } else {
            // Warn the user if we haven't seen any entries under the current basis, before we set the next.
            if (!current_basis.empty() && bases[current_basis].empty()) {
                wcerr << L"WARNING: Basis credential " << current_basis << L" in " << credentialBasisFileName
                    << L" has no credentials listed under it." << endl;
            }
            current_basis = line;
            bases[current_basis]; // Access the key to create the value.
        }
    }

    // Warn the user if we haven't seen any entries under the last basis.
    if (!current_basis.empty() && bases[current_basis].empty()) {
        wcerr << L"WARNING: Basis credential " << current_basis << L" in " << credentialBasisFileName
            << L" has no credentials listed under it." << endl;
    }

    return bases;
}

list<wstring> GetDefaultCredentials(const wstring& username) {
    wstring credentialBasisFileName = GetCredentialBasisFileName();
    auto bases = ReadBases(credentialBasisFileName);
    list<wstring> defaultCredentials;

    PCREDENTIAL *pCredArray = NULL;
    DWORD dwCount = 0;
    if (::CredEnumerate(NULL, 0, &dwCount, &pCredArray)) {
        for (DWORD dwIndex = 0; dwIndex < dwCount; dwIndex++) {
            PCREDENTIAL pCredential = pCredArray[dwIndex];

            wstring target_name(pCredential->TargetName);

            if (IsMercurialCredential(target_name, username) ||
                IsGitCredential(target_name, username)
            ) {
                if (!HasBasis(bases, target_name)) {
                    defaultCredentials.push_back(target_name);
                }
            } else if (IsIntrepidCredential(target_name, username)) {
                wstring credential_name = GetIntrepidCredentialName(target_name);

                if (bases.find(credential_name) == bases.end()) {
                    defaultCredentials.push_back(target_name);
                }
            }
        }

        //Free the credentials array.
        ::CredFree(pCredArray);
    }

    return defaultCredentials;
}

void UpdateCredentialsFromDefault()
{
    wstring username;
    wstring password;
    RequestUsernameAndPassword(username, password);
    UpdateAllCredentials(username, password);
}

/*
    Update the username & password for the basis credential and for all credentials listed under it
    in the credential-bases.txt file.
*/
void UpdateCredentialsFromBasis(const wstring& basis)
{
    wstring username;
    wstring password;
    RequestUsernameAndPassword(username, password);

    wstring credentialBasisFileName = GetCredentialBasisFileName();
    auto bases = ReadBases(credentialBasisFileName);
    auto basisCredentials = bases[basis];
    if (basisCredentials.empty()) {
        wcout << L"There are no credentials for basis '" << basis << L"' listed in " << endl
            << L"  " << credentialBasisFileName << L"." << endl;
        exit(1);
    }

    wstringstream basisCredentialNameStream;
    basisCredentialNameStream << HOLY_GRADLE_CREDENTIAL_PREFIX << basis;
    auto basisCredentialName = basisCredentialNameStream.str();
    StoreCredential(basisCredentialName, username, password);
    for (auto it = basisCredentials.begin(); it != basisCredentials.end(); ++it) {
        StoreCredential(*it, username, password);
    }
}

void ListBases() {
    wstring credentialBasisFileName = GetCredentialBasisFileName();
    auto bases = ReadBases(credentialBasisFileName);
    wcout << L"The following basis credentials exist in " << credentialBasisFileName << L":" << endl;
    for (auto it = bases.begin(); it != bases.end(); ++it) {
        wcout << it->first << endl;
    }
}

void ListDefaults(const wstring& username) {
    wstring credentialBasisFileName = GetCredentialBasisFileName();
    auto defaultCredentials = GetDefaultCredentials(username);
    if (defaultCredentials.empty()) {
        wcout << L"There are no Git, Mercurial, Subversion, and Holy Gradle credentials are listed in " << endl
            << L"  " << credentialBasisFileName << L"." << endl;
    } else {
        wcout << L"The following Git, Mercurial, Subversion, and Holy Gradle credentials are not listed in " << endl
            << L"  " << credentialBasisFileName << L":" << endl;
        for (auto it = defaultCredentials.begin(); it != defaultCredentials.end(); ++it) {
            wcout << *it << endl;
        }
    }
}

void ShowUsage(wchar_t* program_name) {
    wcout << L"Usage: " << program_name << L" <command> <arguments>" << endl;
    wcout << endl;
    wcout << L"  " << program_name << L" get <credential_name>" << endl;
    wcout << L"    Outputs the content of the named credential; normally \"<username>&&&<password>\"." << endl;
    wcout << endl;
    wcout << L"  " << program_name << L" set <credential_name> <username> <password>" << endl;
    wcout << L"    Sets the content of the named credential to \"<username>&&&<password>\"." << endl;
    wcout << endl;
    wcout << L"  " << program_name << L" from-basis <credential_name>" << endl;
    wcout << L"    Prompts for a username and password, then sets them as the content of" << endl;
    wcout << L"    credential \"" << HOLY_GRADLE_CREDENTIAL_PREFIX << L"<credential_name>\", and" << endl;
    wcout << L"    all credentials listed under <credential_name> in" << endl;
    wcout << L"    " << GetCredentialBasisFileName() << endl;
    wcout << endl;
    wcout << L"  " << program_name << L" from-default" << endl;
    wcout << L"    Prompts for a username and password, then sets them as the content of" << endl;
    wcout << L"    credential \"" << HOLY_GRADLE_CREDENTIAL_PREFIX L"Domain Credentials\"," << endl;
    wcout << L"    all Git and Mercurial credentials not listed under any name in" << endl;
    wcout << L"    " << GetCredentialBasisFileName() << L", and" << endl;
    wcout << L"    all \""<< HOLY_GRADLE_CREDENTIAL_PREFIX
        << L"<credential_name>\" credentials not listed as bases there." << endl;
    wcout << endl;
    wcout << L"  " << program_name << L" list-bases" << endl;
    wcout << L"    Lists all the basis <credential_name> values in" << endl;
    wcout << L"    " << GetCredentialBasisFileName() << endl;
    wcout << endl;
    wcout << L"  " << program_name << L" list-defaults <username>" << endl;
    wcout << L"    Lists all the credentials which would be updated by the from-default command" << endl;
    wcout << L"    for the given <username>." << endl;
    wcout << endl;
}

int _tmain(int argc, wchar_t* argv[]) {

    if (argc == 1) {
        ShowUsage(argv[0]);
        exit(1);
    }

    const size_t command_length = wcsnlen_s(argv[1], INPUT_LENGTH_LIMIT);

    if (_wcsnicmp(argv[1], L"get", command_length) == 0 && argc == 3) {
        wstring readKey(argv[2]);
        ReadAndPrintCredential(readKey);
    } else if (_wcsnicmp(argv[1], L"set", command_length) == 0 && argc == 5) {
        wstring writeKey(argv[2]);
        wstring username(argv[3]);
        wstring password(argv[4]);
        StoreCredential(writeKey, username, password);
    } else if (_wcsnicmp(argv[1], L"from-basis", command_length) == 0 && argc == 3) {
        UpdateCredentialsFromBasis(argv[2]);
    } else if (_wcsnicmp(argv[1], L"from-default", command_length) == 0 && argc == 2) {
        UpdateCredentialsFromDefault();
    } else if (_wcsnicmp(argv[1], L"list-bases", command_length) == 0 && argc == 2) {
        ListBases();
    } else if (_wcsnicmp(argv[1], L"list-defaults", command_length) == 0 && argc == 3) {
        ListDefaults(argv[2]);
    } else {
        ShowUsage(argv[0]);
        exit(1);
    }
}
