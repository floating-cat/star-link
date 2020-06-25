package cl.monsoon.star.client;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface WinInet extends StdCallLibrary {

    WinInet INSTANCE = Native.load("wininet", WinInet.class, W32APIOptions.UNICODE_OPTIONS);

    int PROXY_TYPE_DIRECT = 0x00000001;
    int PROXY_TYPE_PROXY = 0x00000002;

    int INTERNET_PER_CONN_FLAGS = 1;
    int INTERNET_PER_CONN_PROXY_SERVER = 2;
    int INTERNET_PER_CONN_PROXY_BYPASS = 3;

    int INTERNET_OPTION_REFRESH = 37;
    int INTERNET_OPTION_PER_CONNECTION_OPTION = 75;
    int INTERNET_OPTION_PROXY_SETTINGS_CHANGED = 95;

    void InternetSetOption(Pointer hInternet, int dwOption, Pointer lpBuffer, int dwBufferLength) throws LastErrorException;

    @Structure.FieldOrder({"dwOption", "value"})
    class INTERNET_PER_CONN_OPTION extends Structure {

        public static class ByReference extends INTERNET_PER_CONN_OPTION implements Structure.ByReference {
            public ByReference() {
            }

            @SuppressWarnings("unused")
            public ByReference(Pointer memory) {
                super(memory);
            }
        }

        public INTERNET_PER_CONN_OPTION() {
        }

        public INTERNET_PER_CONN_OPTION(Pointer memory) {
            super(memory);
            read();
        }

        public static class Value extends Union {
            public int dwValue;
            public String pszValue;
            @SuppressWarnings("unused")
            public FILETIME ftValue;
        }

        public int dwOption;
        public Value value;
    }

    @Structure.FieldOrder({"dwSize", "pszConnection", "dwOptionCount", "dwOptionError", "pOptions"})
    class INTERNET_PER_CONN_OPTION_LIST extends Structure {

        public int dwSize;
        public Pointer pszConnection;
        public int dwOptionCount;
        public int dwOptionError;
        public INTERNET_PER_CONN_OPTION.ByReference pOptions;
    }

    @Structure.FieldOrder({"dwLowDateTime", "dwHighDateTime"})
    @SuppressWarnings("unused")
    class FILETIME extends Structure {

        public int dwLowDateTime;
        public int dwHighDateTime;
    }
}
