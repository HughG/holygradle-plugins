package holygradle.jna.platform.win32;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.*;

import java.util.*;

import static holygradle.jna.platform.win32.WinBase.*;

/**
 * Based on http://stackoverflow.com/questions/38404517/how-to-map-windows-api-credwrite-credread-in-jna.
 */
public interface WinCred extends StdCallLibrary {
    WinCred INSTANCE = (WinCred) Native.loadLibrary("Advapi32", WinCred.class, W32APIOptions.UNICODE_OPTIONS);

    int CRED_TYPE_GENERIC = 1;

    int CRED_PERSIST_NONE = 0;
    int CRED_PERSIST_SESSION = 1;
    int CRED_PERSIST_LOCAL_MACHINE = 2;
    int CRED_PERSIST_ENTERPRISE = 3;

    boolean CredWrite(
            CREDENTIAL.ByReference Credential,
            int Flags
    );

    boolean CredRead(
            String TargetName,
            int Type,
            int Flags,
            PointerByReference Credential
    );

    void CredFree(Pointer cred);

    class CREDENTIAL extends Structure implements AutoCloseable {
        public int Flags;
        public int Type;
        public WString TargetName;
        public WString Comment;
        public FILETIME LastWritten;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public WString TargetAlias;
        public WString UserName;
        private Pointer RawMemBlock;

        public static class ByReference extends CREDENTIAL implements Structure.ByReference {
            public ByReference() {
            }

            public ByReference(Pointer memory) {
                super(memory);
            }
        }

        public CREDENTIAL() { }

        public CREDENTIAL(Pointer ptr) {
            // initialize from the raw memory block returned to us by ADVAPI32
            super(ptr) ;
            RawMemBlock = ptr;
            read() ;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "Flags",
                    "Type",
                    "TargetName",
                    "Comment",
                    "LastWritten",
                    "CredentialBlobSize",
                    "CredentialBlob",
                    "Persist",
                    "AttributeCount",
                    "Attributes",
                    "TargetAlias",
                    "UserName"
            );
        }

        @Override
        public void close() throws Exception {
            WinCred.INSTANCE.CredFree(RawMemBlock);
        }
    }

//    public static class CREDENTIAL_ATTRIBUTE extends Structure {
//        public String Keyword;
//        public int Flags;
//        public int ValueSize;
//        public byte[] Value = new byte[128];
//
//        public static class ByReference extends CREDENTIAL_ATTRIBUTE implements Structure.ByReference {
//        }
//
//        @Override
//        protected List<String> getFieldOrder() {
//            return Arrays.asList(
//                    "Keyword",
//                    "Flags",
//                    "ValueSize",
//                    "Value"
//            );
//        }
//    }
}
