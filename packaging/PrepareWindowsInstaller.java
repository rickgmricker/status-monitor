import java.nio.file.*;
import java.util.List;

/** Customizes the current JDK template without freezing the rest of jpackage's WiX project. */
public class PrepareWindowsInstaller {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), payload = Path.of(args[1]).toAbsolutePath(), output = Path.of(args[2]);
        Files.createDirectories(output);
        String main = Files.readString(Path.of(java.net.URI.create(
                "jrt:/jdk.jpackage/jdk/jpackage/internal/resources/main.wxs")));
        main = replace(main, "<?define JpInstallerVersion=200 ?>", "<?define JpInstallerVersion=500 ?>");
        String references = "";
        for (String component : List.of("MonitorCollectorService", "MonitorCollectorPresentMon", "MonitorCollectorLicense",
                "MonitorInstallerPreferences", "MonitorWindowsStartup"))
            references += "\n      <ComponentRef Id=\"" + component + "\"/>";
        main = replace(main, "<ComponentGroupRef Id=\"Files\"/>", "<ComponentGroupRef Id=\"Files\"/>" + references);
        main = replace(main, "    <InstallExecuteSequence>",
                "    <?include $(var.MonitorInstallerInclude) ?>\n\n    <InstallExecuteSequence>\n" + defaults()
                        + "      <Custom Action=\"MonitorClearUserStartup\" Before=\"RemoveRegistryValues\">REMOVE=\"ALL\" AND NOT UPGRADINGPRODUCTCODE</Custom>\n");
        main = replace(main, "    <InstallUISequence>", "    <InstallUISequence>\n" + defaults());
        Files.writeString(output.resolve("main.wxs"), main);
        Files.writeString(output.resolve("overrides.wxi"), """
                <?xml version="1.0" encoding="utf-8"?>
                <Include xmlns="http://schemas.microsoft.com/wix/2006/wi">
                  <?define MonitorCollectorPayload="%s" ?>
                  <?define MonitorInstallerActions="%s" ?>
                  <?define MonitorInstallerInclude="%s" ?>
                  <?ifdef JpAllowDowngrades ?>
                  <?ifdef JpAllowDowngrades ?>
                    <?undef JpAllowDowngrades ?>
                  <?endif?>
                  <?endif?>
                </Include>
                """.formatted(xml(payload), xml(Path.of(args[3]).toAbsolutePath()), xml(output.resolve("MonitorInstaller.wxi").toAbsolutePath())));
        for (String name : List.of("MonitorInstaller.wxi", "ShortcutPromptDlg.wxs"))
            Files.copy(source.resolve(name), output.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }
    private static String xml(Path path) { return path.toString().replace("&", "&amp;").replace("\"", "&quot;"); }
    private static String defaults() {
        return """
                      <Custom Action="MonitorUsePreviousLanguage" After="AppSearch">NOT MONITOR_LANGUAGE AND MONITOR_PREVIOUS_LANGUAGE</Custom>
                      <Custom Action="MonitorDefaultLanguage" After="MonitorUsePreviousLanguage">NOT MONITOR_LANGUAGE</Custom>
                      <Custom Action="MonitorDefaultStartup" After="MonitorDefaultLanguage">NOT MONITOR_OPTIONS_SET AND NOT MONITOR_STARTUP AND MONITOR_PREVIOUS_STARTUP</Custom>
                """;
    }
    private static String replace(String text, String anchor, String value) {
        if (text.indexOf(anchor) < 0 || text.indexOf(anchor) != text.lastIndexOf(anchor))
            throw new IllegalStateException("Unsupported jpackage template anchor: " + anchor);
        return text.replace(anchor, value);
    }
}
