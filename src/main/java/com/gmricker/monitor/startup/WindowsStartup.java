package com.gmricker.monitor.startup;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import com.gmricker.monitor.ingame.PresentMonCapture;
import com.gmricker.monitor.collector.WindowsCollectorService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

/** Opt-in logon startup for the current user; never uses an elevated registry key. */
public final class WindowsStartup {
    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "Status Monitor";
    private final Path deployment;
    private final Path javaw;
    private final List<Path> classpath;
    private final Registry registry;
    private final Path launcher;

    interface Registry {
        boolean exists();
        void write(String command);
        void remove();
    }

    public WindowsStartup(Path settingsDirectory) {
        this(settingsDirectory, Path.of(System.getProperty("java.home"), "bin", "javaw.exe"),
                Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(java.io.File.pathSeparator)))
                        .map(value -> Path.of(value).toAbsolutePath().normalize()).toList(),
                new CurrentUserRegistry(), packagedLauncher());
    }

    WindowsStartup(Path directory, Path javaw, List<Path> classpath, Registry registry) {
        this(directory, javaw, classpath, registry, null);
    }

    WindowsStartup(Path directory, Path javaw, List<Path> classpath, Registry registry, Path launcher) {
        deployment = directory.toAbsolutePath().resolve("startup-app");
        this.javaw = javaw.toAbsolutePath();
        this.classpath = classpath;
        this.registry = registry;
        this.launcher = launcher;
    }

    private static Path packagedLauncher() {
        String path = System.getProperty("jpackage.app-path");
        return path == null || path.isBlank() ? null : Path.of(path).toAbsolutePath();
    }

    public static boolean isSupported() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    public boolean isEnabled() {
        return registry.exists();
    }

    public void setEnabled(boolean enabled) throws IOException {
        if (!enabled) {
            registry.remove();
            return;
        }
        if (launcher != null) {
            if (!Files.isRegularFile(launcher)) throw new IOException(tr("O executável instalado do Monitor não foi encontrado."));
            registry.write(launcherCommand(launcher));
            return;
        }
        String command = command(javaw, deployment);
        if (!Files.isRegularFile(javaw)) {
            throw new IOException(tr("O Java usado para iniciar o Monitor não foi encontrado."));
        }
        deploy();
        registry.write(command);
    }

    static String command(Path javaw, Path deployment) throws IOException {
        String command = quote(javaw.toString()) + " --enable-native-access=ALL-UNNAMED -cp "
                + quote(deployment + java.io.File.separator + "*")
                + " com.gmricker.monitor.MonitorApplication --startup";
        return checkedCommand(command);
    }

    static String launcherCommand(Path launcher) throws IOException {
        return checkedCommand(quote(launcher.toString()) + " --startup");
    }

    private static String checkedCommand(String command) throws IOException {
        if (command.length() > 260) {
            throw new IOException(tr("O caminho do Monitor é longo demais para a inicialização do Windows."));
        }
        return command;
    }

    private static String quote(String value) throws IOException {
        if (value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IOException(tr("Caminho inválido para inicialização do Windows."));
        }
        return '"' + value + '"';
    }

    private void deploy() throws IOException {
        Files.createDirectories(deployment);
        var retained = new HashSet<String>();
        var directories = classpath.stream().filter(Files::isDirectory).toList();
        for (var source : classpath) {
            if (Files.isRegularFile(source) && source.getFileName().toString().endsWith(".jar")) {
                String name = source.getFileName().toString();
                if (!retained.add(name)) {
                    throw new IOException(tr("Há bibliotecas com o mesmo nome: ") + name);
                }
                var destination = deployment.resolve(name);
                if (!Files.exists(destination) || !Files.isSameFile(source, destination)) {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        if (!directories.isEmpty()) {
            String name = "monitor-classes.jar";
            if (!retained.add(name)) {
                throw new IOException(tr("Nome de biblioteca reservado: ") + name);
            }
            var entries = new HashSet<String>();
            try (var jar = new JarOutputStream(Files.newOutputStream(deployment.resolve(name)))) {
                for (var directory : directories) {
                    try (var files = Files.walk(directory)) {
                        for (var source : files.filter(Files::isRegularFile).toList()) {
                            String entry = directory.relativize(source).toString().replace('\\', '/');
                            if (entries.add(entry)) {
                                jar.putNextEntry(new JarEntry(entry));
                                Files.copy(source, jar);
                                jar.closeEntry();
                            }
                        }
                    }
                }
            }
        }
        if (retained.isEmpty()) {
            throw new IOException(tr("Não foi possível localizar os arquivos do Monitor."));
        }
        var collector = PresentMonCapture.executablePath();
        if (Files.isRegularFile(collector)) {
            var tools = deployment.resolve("tools/presentmon");
            Files.createDirectories(tools);
            for (var name : List.of("PresentMon.exe", "LICENSE.txt", "README.md")) {
                var source = collector.getParent().resolve(name);
                var target = tools.resolve(name);
                if (Files.isRegularFile(source) && (!Files.exists(target) || !Files.isSameFile(source, target))) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        var servicePayload = WindowsCollectorService.payloadPath();
        if (Files.isDirectory(servicePayload)) {
            var targetDirectory = deployment.resolve("tools/collector");
            Files.createDirectories(targetDirectory);
            for (var name : List.of("StatusMonitorCollector.exe", "PresentMon.exe", "PresentMon-LICENSE.txt", "manage-service.ps1")) {
                var source = servicePayload.resolve(name);
                var target = targetDirectory.resolve(name);
                if (Files.isRegularFile(source) && (!Files.exists(target) || !Files.isSameFile(source, target)))
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // Remove only obsolete jars in this app's deployment, never other startup entries.
        try (var files = Files.list(deployment)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".jar") && !retained.contains(name)) {
                    Files.delete(file);
                }
            }
        }
    }

    private static final class CurrentUserRegistry implements Registry {
        @Override
        public boolean exists() {
            return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME);
        }

        @Override
        public void write(String command) {
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, RUN_KEY);
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME, command);
        }

        @Override
        public void remove() {
            if (exists()) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME);
            }
        }
    }
}
