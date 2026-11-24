pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "PAPI AI"

include(":app")
include(":domain")
include(":data")
include(":core:ai")
include(":core:ocr")
include(":core:translation")
include(":core:speech")
include(":core:accessibility")
include(":feature:reader")
include(":feature:translator")
include(":feature:voice")
include(":feature:models")
include(":feature:settings")
