pluginManagement {
    repositories {
        google()
        // Mirror Maven Central artifacts to avoid transient central/GitHub CDN 504s.
        maven {
            url = uri("https://maven.wso2.org/nexus/content/repositories/central/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            url = uri("https://maven.wso2.org/nexus/content/repositories/central/")
        }
        mavenCentral()
    }
}
rootProject.name = "MockExam"
include(":app")
