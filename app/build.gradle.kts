plugins {
    id("com.android.application")
}

android {
    namespace = "com.apolo.terminal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apolo.terminal"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val npmInstallTerminal by tasks.registering(Exec::class) {
    workingDir(projectDir)
    commandLine("npm", "install", "--ignore-scripts", "--no-audit", "--no-fund")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

val copyTerminalVendor by tasks.registering(Copy::class) {
    dependsOn(npmInstallTerminal)
    from("node_modules/@xterm/xterm/lib/xterm.js") { into("vendor") }
    from("node_modules/@xterm/xterm/css/xterm.css") { into("vendor") }
    from("node_modules/@xterm/addon-fit/lib/addon-fit.js") { into("vendor") }
    into("src/main/assets/terminal")
}

tasks.named("preBuild").configure {
    dependsOn(copyTerminalVendor)
}
