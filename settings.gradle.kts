pluginManagement {
    repositories {
        // 配置 Google 仓库（自动包含 content 过滤）
        google()
        // 配置 Maven Central
        mavenCentral()
        // 配置 Gradle 插件门户
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // 修正 URL 声明语法
    }
}

rootProject.name = "TFGY999"
include(":app")