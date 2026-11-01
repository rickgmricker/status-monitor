import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject
import java.io.File

plugins {
    application
}

@DisableCachingByDefault(because = "Native Windows packaging depends on the host JDK and WiX")
abstract class WindowsPackageTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val payload: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val icon: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val jpackage: RegularFileProperty
    @get:Input abstract val format: Property<String>
    @get:Input abstract val appVersion: Property<String>
    @get:Input abstract val mainJar: Property<String>
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @get:LocalState abstract val temporary: DirectoryProperty
    @get:Internal abstract val buildRoot: DirectoryProperty
    @get:Internal abstract val wixHome: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val installerResources: DirectoryProperty
    @get:Inject abstract val execOperations: ExecOperations
    @get:Inject abstract val fileOperations: FileSystemOperations

    @TaskAction fun packageApplication() {
        check(System.getProperty("os.name").startsWith("Windows")) { "O pacote Windows deve ser gerado no Windows." }
        val type = format.get()
        val output = destination.get().asFile.canonicalFile
        val temp = temporary.get().asFile.canonicalFile
        val root = buildRoot.get().asFile.canonicalFile.toPath()
        for (directory in listOf(output, temp)) {
            require(directory.toPath().startsWith(root) && directory.toPath() != root) { "Saída de empacotamento fora de build/." }
        }
        var wixBin: File? = null
        if (type != "app-image") {
            val configured = wixHome.get().asFile
            wixBin = listOf(configured, configured.resolve("bin")).firstOrNull {
                it.resolve("candle.exe").isFile && it.resolve("light.exe").isFile
            }
            check(wixBin != null) { "WiX não encontrado. Execute packaging/setup-wix.ps1 ou configure -PwixHome=C:/caminho/WiX." }
        }
        fileOperations.delete { delete(output, temp) }
        output.mkdirs(); temp.mkdirs()
        val arguments = mutableListOf("-J-Duser.language=en", "-J-Duser.country=US",
            "--type", type, "--name", "Status Monitor", "--app-version", appVersion.get(),
            "--vendor", "GMRicker", /*"--description", "Monitor de hardware e desempenho em jogos",*/
            "--dest", output.absolutePath, "--temp", temp.absolutePath, "--icon", icon.get().asFile.absolutePath)
        if (type == "app-image") {
            arguments.addAll(listOf("--input", payload.get().asFile.absolutePath, "--main-jar", mainJar.get(),
                "--main-class", "com.gmricker.monitor.MonitorApplication", "--java-options", "--enable-native-access=ALL-UNNAMED",
                "--add-modules", "java.desktop,java.management,jdk.management,jdk.unsupported,java.logging,java.naming"))
        } else {
            arguments.addAll(listOf("--app-image", payload.get().asFile.resolve("Status Monitor").absolutePath,
                "--install-dir", "Status Monitor",
                "--win-dir-chooser", "--win-menu", "--win-menu-group", "Status Monitor",
                "--win-shortcut", "--win-shortcut-prompt", "--win-upgrade-uuid", "18572358-095a-48fd-b932-e9eaf5ac0df8"))
            arguments.addAll(listOf("--resource-dir", installerResources.get().asFile.absolutePath))
        }
        execOperations.exec {
            executable(jpackage.get().asFile)
            args(arguments)
            if (wixBin != null) environment("PATH", wixBin.absolutePath + File.pathSeparator + System.getenv("PATH"))
        }
    }
}

group = "com.gmricker.monitor"
version = "0.2.0"

tasks.processResources {
    val applicationVersion = project.version.toString()
    inputs.property("appVersion", applicationVersion)
    filesMatching("com/gmricker/monitor/application.properties") {
        expand("version" to applicationVersion)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "com.gmricker.monitor.MonitorApplication"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

val collectorPayload = tasks.register<Exec>("collectorPayload") {
    group = "build"
    description = "Compila o serviço coletor nativo e prepara seus arquivos (sem instalar)."
    val compiler = providers.gradleProperty("zigExe").orElse(
        layout.projectDirectory.file(".build-tools/zig-x86_64-windows-0.15.2/zig.exe").asFile.absolutePath)
    inputs.files(fileTree("native/collector"), fileTree("tools/presentmon"))
    inputs.property("zigCompiler", compiler)
    outputs.dir(layout.buildDirectory.dir("collector/payload"))
    commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
        layout.projectDirectory.file("native/collector/build.ps1").asFile.absolutePath,
        "-Zig", compiler.get(), "-Output", layout.buildDirectory.dir("collector/payload").get().asFile.absolutePath)
}

tasks.named("run") { dependsOn(collectorPayload) }

distributions {
    main {
        contents {
            from("tools/presentmon") { into("tools/presentmon") }
            from(collectorPayload.map { it.outputs.files }) { into("tools/collector") }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.jar {
    manifest {
        attributes("Main-Class" to application.mainClass.get(), "Class-Path" to
            configurations.runtimeClasspath.get().files.sortedBy { it.name }.joinToString(" ") { it.name })
    }
}

val compilePackagingIcon = tasks.register<JavaCompile>("compilePackagingIcon") {
    dependsOn(tasks.classes)
    source("packaging/CreateIcon.java")
    classpath = sourceSets.main.get().output
    destinationDirectory.set(layout.buildDirectory.dir("packaging/icon-classes"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
val packagingIconFile = layout.buildDirectory.file("packaging/monitor.ico")
val packagingIcon = tasks.register<JavaExec>("packagingIcon") {
    dependsOn(compilePackagingIcon)
    classpath = files(compilePackagingIcon.flatMap { it.destinationDirectory }, sourceSets.main.get().output)
    mainClass.set("com.gmricker.monitor.ui.CreateIcon")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    args(packagingIconFile.get().asFile.absolutePath)
    outputs.file(packagingIconFile)
}
val jpackageInput = tasks.register<Sync>("jpackageInput") {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("packaging/input"))
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    from("tools/presentmon") { into("tools/presentmon") }
    from(collectorPayload.map { it.outputs.files }) { into("tools/collector") }
}
val jpackageExecutable = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
    .map { it.metadata.installationPath.file("bin/jpackage.exe") }
val configuredWix = providers.gradleProperty("wixHome").orElse(providers.environmentVariable("WIX"))
    .orElse(layout.projectDirectory.dir(".build-tools/wix3").asFile.absolutePath)

val compileInstallerPreparation = tasks.register<JavaCompile>("compileInstallerPreparation") {
    source("packaging/PrepareWindowsInstaller.java")
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("packaging/installer-preparation-classes"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
val installerActions = tasks.register<Exec>("installerActions") {
    val compiler = providers.gradleProperty("zigExe").orElse(
        layout.projectDirectory.file(".build-tools/zig-x86_64-windows-0.15.2/zig.exe").asFile.absolutePath)
    inputs.file("native/installer/actions.c")
    inputs.file("packaging/build-installer-actions.ps1")
    inputs.property("zigCompiler", compiler)
    outputs.file(layout.buildDirectory.file("packaging/installer-actions.dll"))
    commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
        layout.projectDirectory.file("packaging/build-installer-actions.ps1").asFile.absolutePath,
        "-Zig", compiler.get(), "-Output", layout.buildDirectory.file("packaging/installer-actions.dll").get().asFile.absolutePath)
}
val prepareInstallerResources = tasks.register<JavaExec>("prepareInstallerResources") {
    group = "distribution"
    description = "Prepara as opções e o serviço do instalador sem gerar um pacote."
    dependsOn(compileInstallerPreparation, collectorPayload, installerActions)
    classpath = files(compileInstallerPreparation.flatMap { it.destinationDirectory })
    mainClass.set("PrepareWindowsInstaller")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    inputs.dir("packaging/windows")
    inputs.file("packaging/PrepareWindowsInstaller.java")
    inputs.dir(collectorPayload.map { it.outputs.files.singleFile })
    inputs.file(installerActions.map { it.outputs.files.singleFile })
    outputs.dir(layout.buildDirectory.dir("packaging/windows-resources"))
    args(layout.projectDirectory.dir("packaging/windows").asFile.absolutePath,
        layout.buildDirectory.dir("collector/payload").get().asFile.absolutePath,
        layout.buildDirectory.dir("packaging/windows-resources").get().asFile.absolutePath,
        layout.buildDirectory.file("packaging/installer-actions.dll").get().asFile.absolutePath)
}
tasks.register<Exec>("validateInstallerSources") {
    group = "verification"
    description = "Valida as fontes WiX sem gerar ou instalar MSI/EXE."
    dependsOn(prepareInstallerResources)
    commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
        layout.projectDirectory.file("packaging/validate-installer.ps1").asFile.absolutePath,
        "-Resources", layout.buildDirectory.dir("packaging/windows-resources").get().asFile.absolutePath,
        "-Wix", configuredWix.get())
}

fun WindowsPackageTask.configurePackage(type: String) {
    group = "distribution"
    format.set(type)
    appVersion.set(project.version.toString())
    mainJar.set(tasks.jar.get().archiveFileName)
    icon.set(packagingIconFile)
    jpackage.set(jpackageExecutable)
    buildRoot.set(layout.buildDirectory)
    temporary.set(layout.buildDirectory.dir("packaging/tmp-$type"))
    wixHome.set(layout.projectDirectory.dir(configuredWix.get()))
    installerResources.set(layout.buildDirectory.dir("packaging/windows-resources"))
    dependsOn(prepareInstallerResources)
}
val jpackageImage = tasks.register<WindowsPackageTask>("jpackageImage") {
    configurePackage("app-image")
    description = "Gera executável Windows e runtime Java embutido."
    dependsOn(jpackageInput, packagingIcon)
    payload.set(layout.buildDirectory.dir("packaging/input"))
    destination.set(layout.buildDirectory.dir("jpackage/image"))
}
val jpackageInstaller = tasks.register<WindowsPackageTask>("jpackageInstaller") {
    configurePackage("exe")
    description = "Gera instalador EXE Windows x64 com Java embutido."
    dependsOn(jpackageImage, tasks.check)
    payload.set(jpackageImage.flatMap { it.destination })
    destination.set(layout.buildDirectory.dir("installers/exe"))
}
tasks.register<WindowsPackageTask>("jpackageMsi") {
    configurePackage("msi")
    description = "Gera instalador MSI Windows x64 com Java embutido."
    dependsOn(jpackageImage, tasks.check)
    payload.set(jpackageImage.flatMap { it.destination })
    destination.set(layout.buildDirectory.dir("installers/msi"))
}
tasks.register<Zip>("jpackagePortable") {
    group = "distribution"
    description = "Compacta a imagem portátil Windows com Java embutido."
    dependsOn(jpackageImage)
    from(jpackageImage.flatMap { it.destination })
    archiveFileName.set("Status-Monitor-${project.version}-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
