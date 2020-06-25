package cl.monsoon.star.client;

import com.sun.jna.Pointer;

import static cl.monsoon.star.client.WinInet.*;

final public class WindowsProxy {

    public static void setNet(boolean proxy, String host, int port) {
        var list = new INTERNET_PER_CONN_OPTION_LIST();
        list.dwSize = list.size();
        list.pszConnection = Pointer.NULL;
        list.dwOptionCount = proxy ? 3 : 1;
        list.dwOptionError = 0;
        list.pOptions = new INTERNET_PER_CONN_OPTION.ByReference();

        var options = (INTERNET_PER_CONN_OPTION[]) list.pOptions.toArray(list.dwOptionCount);

        options[0].dwOption = INTERNET_PER_CONN_FLAGS;
        options[0].value.setType(int.class);

        if (proxy) {
            options[0].value.dwValue = PROXY_TYPE_PROXY;

            options[1].dwOption = INTERNET_PER_CONN_PROXY_SERVER;
            options[1].value.setType(String.class);
            options[1].value.pszValue = "http://" + host + ":" + port;

            options[2].dwOption = INTERNET_PER_CONN_PROXY_BYPASS;
            options[2].value.setType(String.class);
            options[2].value.pszValue = host;
        } else {
            options[0].value.dwValue = PROXY_TYPE_DIRECT;
        }

        list.write();
        setOptions(list);
    }

    private static void setOptions(INTERNET_PER_CONN_OPTION_LIST list) {
        INSTANCE.InternetSetOption(
            Pointer.NULL,
            INTERNET_OPTION_PER_CONNECTION_OPTION,
            list.getPointer(),
            list.size());

        INSTANCE.InternetSetOption(
            Pointer.NULL,
            INTERNET_OPTION_PROXY_SETTINGS_CHANGED,
            Pointer.NULL,
            0);

        INSTANCE.InternetSetOption(
            Pointer.NULL,
            INTERNET_OPTION_REFRESH,
            Pointer.NULL, 0);
    }
}
