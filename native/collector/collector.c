#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0602
#endif
#include <windows.h>
#include <evntrace.h>
#include <sddl.h>
#include <stdio.h>
#include <wchar.h>
#include <stdint.h>
#include <string.h>

#define SERVICE_NAME L"GmrickerMonitorCollector"
#define PIPE_NAME L"\\\\.\\pipe\\GmrickerMonitorCollector.v1"
#define MAX_CLIENTS 8

static HANDLE stopping;
static SERVICE_STATUS_HANDLE status_handle;
static SERVICE_STATUS status;
static volatile LONG clients;
static volatile LONG64 last_activity;
static volatile LONG sequence;
static WCHAR presentmon[MAX_PATH];

static void report(DWORD state, DWORD error) {
    status.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
    status.dwCurrentState = state;
    status.dwControlsAccepted = state == SERVICE_RUNNING ? SERVICE_ACCEPT_STOP | SERVICE_ACCEPT_SHUTDOWN : 0;
    status.dwWin32ExitCode = error;
    status.dwWaitHint = state == SERVICE_STOP_PENDING ? 10000 : 0;
    status.dwCheckPoint = state == SERVICE_STOP_PENDING ? status.dwCheckPoint + 1 : 0;
    if (status_handle) SetServiceStatus(status_handle, &status);
}

static DWORD WINAPI control(DWORD code, DWORD type, void *data, void *context) {
    (void)type; (void)data; (void)context;
    if (code == SERVICE_CONTROL_STOP || code == SERVICE_CONTROL_SHUTDOWN) SetEvent(stopping);
    return NO_ERROR;
}

/* Every pipe operation has a deadline and is interrupted by service shutdown. */
static BOOL transfer(HANDLE pipe, void *buffer, DWORD length, BOOL write, DWORD *count, DWORD timeout) {
    OVERLAPPED operation = {0};
    operation.hEvent = CreateEventW(NULL, TRUE, FALSE, NULL);
    if (!operation.hEvent) return FALSE;
    BOOL result = write ? WriteFile(pipe, buffer, length, count, &operation)
                        : ReadFile(pipe, buffer, length, count, &operation);
    if (!result && GetLastError() == ERROR_IO_PENDING) {
        HANDLE events[] = {stopping, operation.hEvent};
        if (WaitForMultipleObjects(2, events, FALSE, timeout) == WAIT_OBJECT_0 + 1) {
            result = GetOverlappedResult(pipe, &operation, count, FALSE);
        } else {
            CancelIoEx(pipe, &operation);
            GetOverlappedResult(pipe, &operation, count, TRUE);
            result = FALSE;
        }
    }
    CloseHandle(operation.hEvent);
    return result;
}

static BOOL send_data(HANDLE pipe, const void *buffer, DWORD length) {
    const char *data = buffer;
    while (length) {
        DWORD written = 0;
        if (!transfer(pipe, (void *)data, length, TRUE, &written, 3000) || !written) return FALSE;
        data += written; length -= written;
    }
    return TRUE;
}

static void error_message(HANDLE pipe, const char *message) {
    send_data(pipe, "ERR ", 4);
    send_data(pipe, message, (DWORD)strlen(message));
    send_data(pipe, "\n", 1);
}

static BOOL read_request(HANDLE pipe, char *request, size_t capacity) {
    ULONGLONG deadline = GetTickCount64() + 5000;
    for (size_t index = 0; index + 1 < capacity; ++index) {
        DWORD count = 0;
        ULONGLONG now = GetTickCount64();
        if (now >= deadline || !transfer(pipe, &request[index], 1, FALSE, &count, (DWORD)(deadline - now)) || count != 1) return FALSE;
        if (request[index] == '\n') { request[index] = 0; return TRUE; }
        if (request[index] < 32 || request[index] > 126) return FALSE;
    }
    return FALSE;
}

static DWORD parse_pid(const char *request) {
    const char *prefix = "CAPTURE 1 ";
    if (strncmp(request, prefix, strlen(prefix))) return 0;
    const char *cursor = request + strlen(prefix);
    if (*cursor < '1' || *cursor > '9') return 0;
    uint64_t pid = 0;
    for (; *cursor; ++cursor) {
        if (*cursor < '0' || *cursor > '9') return 0;
        pid = pid * 10 + (*cursor - '0');
        if (pid > UINT32_MAX) return 0;
    }
    return (DWORD)pid;
}

/* The caller may inspect only a target it can query in its own logon session. */
static HANDLE authorize_target(HANDLE pipe, DWORD pid) {
    ULONG session = 0;
    DWORD target_session = 0;
    if (!GetNamedPipeClientSessionId(pipe, &session) || !ProcessIdToSessionId(pid, &target_session)
            || session != target_session || !ImpersonateNamedPipeClient(pipe)) return NULL;
    HANDLE target = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE, FALSE, pid);
    if (!RevertToSelf()) ExitProcess(ERROR_ACCESS_DENIED);
    return target;
}

static HANDLE make_job(void) {
    HANDLE job = CreateJobObjectW(NULL, NULL);
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits = {0};
    limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if (job && !SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits))) {
        CloseHandle(job); job = NULL;
    }
    return job;
}

static void stop_capture(HANDLE child, const WCHAR *session) {
    if (WaitForSingleObject(child, 0) == WAIT_OBJECT_0) return;
    WCHAR command[1024];
    swprintf(command, 1024, L"\"%ls\" --session_name %ls --terminate_existing_session --no_console_stats --no_csv", presentmon, session);
    STARTUPINFOW startup = {0}; startup.cb = sizeof(startup);
    PROCESS_INFORMATION stop = {0};
    if (CreateProcessW(presentmon, command, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &startup, &stop)) {
        if (WaitForSingleObject(stop.hProcess, 2000) != WAIT_OBJECT_0) TerminateProcess(stop.hProcess, 1);
        CloseHandle(stop.hThread); CloseHandle(stop.hProcess);
    }
    WaitForSingleObject(child, 1000);
}

static void capture(HANDLE pipe, HANDLE target, DWORD pid) {
    HANDLE output_read = NULL, output_write = NULL, null_input = INVALID_HANDLE_VALUE;
    HANDLE job = NULL;
    SECURITY_ATTRIBUTES inherit = {sizeof(inherit), NULL, TRUE};
    PROCESS_INFORMATION child = {0};
    WCHAR session[128], command[1024];
    swprintf(session, 128, L"StatusMonitor-Service-%lu-%ld", GetCurrentProcessId(), InterlockedIncrement(&sequence));
    if (!CreatePipe(&output_read, &output_write, &inherit, 0)
            || !SetHandleInformation(output_read, HANDLE_FLAG_INHERIT, 0)) goto failed;
    null_input = CreateFileW(L"NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, &inherit, OPEN_EXISTING, 0, NULL);
    if (null_input == INVALID_HANDLE_VALUE || !(job = make_job())) goto failed;
    swprintf(command, 1024, L"\"%ls\" --process_id %lu --output_stdout --no_console_stats --v1_metrics --session_name %ls --terminate_on_proc_exit --no_track_gpu --no_track_input", presentmon, pid, session);
    STARTUPINFOEXW startup = {0}; startup.StartupInfo.cb = sizeof(startup);
    startup.StartupInfo.dwFlags = STARTF_USESTDHANDLES;
    startup.StartupInfo.hStdInput = null_input;
    startup.StartupInfo.hStdOutput = output_write;
    startup.StartupInfo.hStdError = output_write;
    /* Explicit inheritance avoids leaking another client's handles into this capture. */
    SIZE_T size = 0;
    InitializeProcThreadAttributeList(NULL, 1, 0, &size);
    startup.lpAttributeList = HeapAlloc(GetProcessHeap(), 0, size);
    if (!startup.lpAttributeList || !InitializeProcThreadAttributeList(startup.lpAttributeList, 1, 0, &size)) {
        if (startup.lpAttributeList) HeapFree(GetProcessHeap(), 0, startup.lpAttributeList);
        goto failed;
    }
    HANDLE inherited[] = {null_input, output_write};
    BOOL configured = UpdateProcThreadAttribute(startup.lpAttributeList, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST,
            inherited, sizeof(inherited), NULL, NULL);
    BOOL launched = configured && CreateProcessW(presentmon, command, NULL, NULL, TRUE,
            CREATE_NO_WINDOW | CREATE_SUSPENDED | EXTENDED_STARTUPINFO_PRESENT, NULL, NULL, &startup.StartupInfo, &child);
    DeleteProcThreadAttributeList(startup.lpAttributeList);
    HeapFree(GetProcessHeap(), 0, startup.lpAttributeList);
    if (!launched) goto failed;
    if (!AssignProcessToJobObject(job, child.hProcess)) { TerminateProcess(child.hProcess, 1); goto failed; }
    ResumeThread(child.hThread);
    CloseHandle(output_write); output_write = NULL;
    if (!send_data(pipe, "OK 1\n", 5)) goto cleanup;
    ULONGLONG last_flush = GetTickCount64();
    for (;;) {
        DWORD available = 0;
        if (WaitForSingleObject(stopping, 0) == WAIT_OBJECT_0 || WaitForSingleObject(target, 0) == WAIT_OBJECT_0
                || !PeekNamedPipe(pipe, NULL, 0, NULL, &available, NULL)) break;
        if (GetTickCount64() - last_flush >= 50) {
            /* Only flush this client's session. The default ETW delivery batches
               frames for about a second, making the frametime graph stall. */
            struct {
                EVENT_TRACE_PROPERTIES properties;
                WCHAR logger[1024];
                WCHAR logfile[1024];
            } trace = {0};
            trace.properties.Wnode.BufferSize = sizeof(trace);
            trace.properties.LoggerNameOffset = sizeof(EVENT_TRACE_PROPERTIES);
            trace.properties.LogFileNameOffset = sizeof(EVENT_TRACE_PROPERTIES) + sizeof(trace.logger);
            ControlTraceW(0, session, &trace.properties, EVENT_TRACE_CONTROL_FLUSH);
            last_flush = GetTickCount64();
        }
        if (!PeekNamedPipe(output_read, NULL, 0, NULL, &available, NULL)) break;
        if (available) {
            char buffer[8192]; DWORD count = 0;
            if (!ReadFile(output_read, buffer, available < sizeof(buffer) ? available : sizeof(buffer), &count, NULL)
                    || !send_data(pipe, buffer, count)) break;
        } else {
            if (WaitForSingleObject(child.hProcess, 0) == WAIT_OBJECT_0) {
                DWORD code = 0; char line[64]; GetExitCodeProcess(child.hProcess, &code);
                int length = snprintf(line, sizeof(line), "#EXIT %lu\n", code);
                send_data(pipe, line, (DWORD)length);
                break;
            }
            WaitForSingleObject(stopping, 20);
        }
    }
    goto cleanup;
failed:
    error_message(pipe, "Falha ao iniciar o PresentMon instalado no servico.");
cleanup:
    if (child.hProcess) {
        if (WaitForSingleObject(child.hProcess, 0) == WAIT_OBJECT_0) {
            DWORD code = 0; char line[64]; GetExitCodeProcess(child.hProcess, &code);
            int length = snprintf(line, sizeof(line), "#EXIT %lu\n", code);
            send_data(pipe, line, (DWORD)length);
        }
        send_data(pipe, "#END\n", 5);
    }
    if (child.hProcess) { stop_capture(child.hProcess, session); CloseHandle(child.hThread); CloseHandle(child.hProcess); }
    if (job) CloseHandle(job);
    if (output_read) CloseHandle(output_read);
    if (output_write) CloseHandle(output_write);
    if (null_input != INVALID_HANDLE_VALUE) CloseHandle(null_input);
}

static DWORD WINAPI client_worker(void *parameter) {
    HANDLE pipe = parameter;
    char request[128];
    if (read_request(pipe, request, sizeof(request))) {
        DWORD pid = parse_pid(request);
        if (!strcmp(request, "PING 1")) send_data(pipe, "OK 1\n", 5);
        else if (!pid) error_message(pipe, "Pedido invalido. Protocolo esperado: CAPTURE 1 PID.");
        else {
            HANDLE target = authorize_target(pipe, pid);
            if (!target) error_message(pipe, "Processo indisponivel ou sem permissao nesta sessao.");
            else { capture(pipe, target, pid); CloseHandle(target); }
        }
    }
    /* Give the peer time to consume the final status and close; never flush unboundedly. */
    char ignored; DWORD received;
    transfer(pipe, &ignored, 1, FALSE, &received, 1000);
    DisconnectNamedPipe(pipe); CloseHandle(pipe);
    InterlockedExchange64(&last_activity, (LONG64)GetTickCount64());
    InterlockedDecrement(&clients);
    return 0;
}

static DWORD serve(void) {
    PSECURITY_DESCRIPTOR descriptor = NULL;
    /* Local interactive users only. They cannot change the service or its binaries. */
    if (!ConvertStringSecurityDescriptorToSecurityDescriptorW(
            L"D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GRGW;;;IU)", SDDL_REVISION_1, &descriptor, NULL)) return GetLastError();
    SECURITY_ATTRIBUTES access = {sizeof(access), descriptor, FALSE};
    InterlockedExchange64(&last_activity, (LONG64)GetTickCount64());
    BOOL first = TRUE;
    DWORD error = NO_ERROR;
    while (WaitForSingleObject(stopping, 0) != WAIT_OBJECT_0) {
        if (InterlockedCompareExchange(&clients, 0, 0) >= MAX_CLIENTS) { WaitForSingleObject(stopping, 100); continue; }
        HANDLE pipe = CreateNamedPipeW(PIPE_NAME, PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED
                | (first ? FILE_FLAG_FIRST_PIPE_INSTANCE : 0), PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
                MAX_CLIENTS + 1, 65536, 1024, 1000, &access);
        if (pipe == INVALID_HANDLE_VALUE) { error = GetLastError(); break; }
        first = FALSE;
        OVERLAPPED connect = {0}; connect.hEvent = CreateEventW(NULL, TRUE, FALSE, NULL);
        if (!connect.hEvent) { error = GetLastError(); CloseHandle(pipe); break; }
        BOOL connected = ConnectNamedPipe(pipe, &connect);
        DWORD code = connected ? NO_ERROR : GetLastError();
        if (code == ERROR_PIPE_CONNECTED) connected = TRUE;
        else if (code == ERROR_IO_PENDING) {
            HANDLE events[] = {stopping, connect.hEvent};
            for (;;) {
                DWORD result = WaitForMultipleObjects(2, events, FALSE, 500);
                if (result == WAIT_OBJECT_0 + 1) {
                    DWORD count = 0; connected = GetOverlappedResult(pipe, &connect, &count, FALSE); break;
                }
                if (result == WAIT_OBJECT_0 || (InterlockedCompareExchange(&clients, 0, 0) == 0
                        && GetTickCount64() - (ULONGLONG)InterlockedCompareExchange64(&last_activity, 0, 0) >= 5000)) {
                    SetEvent(stopping); break;
                }
            }
            if (!connected) { DWORD count; CancelIoEx(pipe, &connect); GetOverlappedResult(pipe, &connect, &count, TRUE); }
        }
        CloseHandle(connect.hEvent);
        if (connected && WaitForSingleObject(stopping, 0) != WAIT_OBJECT_0) {
            InterlockedIncrement(&clients);
            HANDLE thread = CreateThread(NULL, 0, client_worker, pipe, 0, NULL);
            if (thread) CloseHandle(thread);
            else { InterlockedDecrement(&clients); DisconnectNamedPipe(pipe); CloseHandle(pipe); }
        } else { CloseHandle(pipe); if (!connected && code != ERROR_IO_PENDING) { error = code; break; } }
    }
    LocalFree(descriptor);
    SetEvent(stopping);
    report(SERVICE_STOP_PENDING, error);
    while (InterlockedCompareExchange(&clients, 0, 0)) { report(SERVICE_STOP_PENDING, error); Sleep(100); }
    return error;
}

static void WINAPI service_main(DWORD count, WCHAR **arguments) {
    (void)count; (void)arguments;
    stopping = CreateEventW(NULL, TRUE, FALSE, NULL);
    status_handle = RegisterServiceCtrlHandlerExW(SERVICE_NAME, control, NULL);
    if (!status_handle || !stopping) return;
    report(SERVICE_START_PENDING, NO_ERROR);
    GetModuleFileNameW(NULL, presentmon, MAX_PATH);
    WCHAR *slash = wcsrchr(presentmon, L'\\');
    if (!slash || slash - presentmon + 16 >= MAX_PATH) { report(SERVICE_STOPPED, ERROR_BAD_PATHNAME); return; }
    wcscpy(slash + 1, L"PresentMon.exe");
    if (GetFileAttributesW(presentmon) == INVALID_FILE_ATTRIBUTES) { report(SERVICE_STOPPED, ERROR_FILE_NOT_FOUND); return; }
    report(SERVICE_RUNNING, NO_ERROR);
    DWORD error = serve();
    CloseHandle(stopping);
    report(SERVICE_STOPPED, error);
}

int wmain(int argc, WCHAR **argv) {
    SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (argc == 2 && !wcscmp(argv[1], L"--self-test")) {
        if (parse_pid("CAPTURE 1 123") != 123 || parse_pid("CAPTURE 1 4294967295") != UINT32_MAX
                || parse_pid("CAPTURE 1 4294967296") || parse_pid("CAPTURE 1 0") || parse_pid("CAPTURE 1 -1")
                || parse_pid("CAPTURE 1 123 --other") || parse_pid("CAPTURE 2 123") || parse_pid("CAPTURE 1 9999999999999999999999")) return 1;
        puts("Collector protocol self-test passed."); return 0;
    }
    SERVICE_TABLE_ENTRYW table[] = {{SERVICE_NAME, service_main}, {NULL, NULL}};
    return StartServiceCtrlDispatcherW(table) ? 0 : (int)GetLastError();
}
