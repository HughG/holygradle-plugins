package holygradle.jna.platform.win32;

import com.sun.jna.*;
import com.sun.jna.ptr.*;

import java.io.*;

import static holygradle.jna.platform.win32.WinCred.*;

public abstract class WinCredUtil {
    public static Credential readCredential(String target) throws Exception {
        PointerByReference pptr = new PointerByReference() ;
        if (!WinCred.INSTANCE.CredRead(target, CRED_TYPE_GENERIC, 0, pptr)) {
            throw new Win32Exception(Native.getLastError());
        }
        try (CREDENTIAL cred = new CREDENTIAL(pptr.getValue())) {
            String username = cred.UserName.toString();
            String password = new String(cred.CredentialBlob.getByteArray(0,cred.CredentialBlobSize), "UTF-16LE");
            return new Credential(username, password);
        }
    }

    public static void writeCredential(
        String target,
        String username,
        String password,
        int persistence
    ) throws UnsupportedEncodingException {
        // prepare the credential blob
        byte[] credBlob = password.getBytes("UTF-16LE");
        Memory credBlobMem = new Memory(credBlob.length);
        credBlobMem.write(0 , credBlob , 0 , credBlob.length);

        // Create the credential.  Note that we don't wrap this in a try-with-resources because we're allocating the
        // CREDENTIAL structure, not Windows.
        CREDENTIAL.ByReference cred = new CREDENTIAL.ByReference();
        cred.Type = CRED_TYPE_GENERIC;
        cred.TargetName = new WString(target);
        cred.CredentialBlobSize = (int) credBlobMem.size();
        cred.CredentialBlob = credBlobMem;
        cred.Persist = persistence;
        cred.UserName = new WString(username);

        // save the credential
        if (!WinCred.INSTANCE.CredWrite(cred, 0)) {
            throw new Win32Exception(Native.getLastError());
        }
    }

    public static class Credential {
        public String username;
        public String password;

        public Credential(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
