plugins {
    idea
    id("org.jetbrains.intellij") version "0.3.6"
    id("scala")
    id("com.diffplug.gradle.spotless") version "3.14.0"
}

allprojects {
    apply {
        plugin("idea")
        plugin("org.jetbrains.intellij")
    }

    repositories {
        mavenCentral()
    }

    intellij {
        version = "IC-2019.3"
        setPlugins("Kotlin", "java", "org.intellij.scala:2019.3.23")
    }
    version = "2019.3.0"
}

spotless {
    isEnforceCheck = false
    scala {
        scalafmt().configFile("scalafmt.conf")
    }
}

project(":") {
    intellij {
        pluginName = "IntellijScalaToKotlin"
    }
    dependencies {
        // exclude "scala-library" from runtime dependencies
        // due to https://intellij-support.jetbrains.com/hc/en-us/community/posts/206003909-Plugin-dependency-class-loading-problem
        // and similar problems
        configurations.runtime.exclude("org.scala-lang", "scala-library")
        compileOnly("org.scala-lang:scala-library:2.12.6")
        testCompile("org.scala-lang:scala-library:2.12.6")
        testCompile("junit:junit:4.12")
    }
}
