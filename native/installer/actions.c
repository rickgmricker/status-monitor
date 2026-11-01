#ifndef UNICODE
#define UNICODE
#endif
#include <windows.h>
#include <msi.h>

/* Commit with user impersonation: failed/rolled-back uninstalls keep startup unchanged. */
__declspec(dllexport) UINT __stdcall ClearUserStartup(MSIHANDLE install) {
    (void)install;
    HKEY current_user = NULL, run = NULL;
    /* RegOpenCurrentUser respects the MSI thread's impersonated user, unlike cached HKCU. */
    if (RegOpenCurrentUser(KEY_WRITE, &current_user) == ERROR_SUCCESS) {
        if (RegOpenKeyExW(current_user, L"Software\\Microsoft\\Windows\\CurrentVersion\\Run", 0,
                KEY_SET_VALUE | KEY_WOW64_64KEY, &run) == ERROR_SUCCESS) {
            RegDeleteValueW(run, L"Status Monitor");
            RegCloseKey(run);
        }
        RegCloseKey(current_user);
    }
    return ERROR_SUCCESS;
}
