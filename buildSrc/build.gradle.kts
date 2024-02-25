repositories {
    maven {
        name = "paper"
        url = uri("https://papermc.io/repo/repository/maven-snapshots/")
        mavenContent {
            includeGroup("org.cadixdev")
        }
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        mavenContent {
            includeGroup("org.cadixdev")
        }
    }
    maven {
        name = "Fabric Repository"
        url = uri("https://maven.fabricmc.net")
    }
    maven {
        name = "Legacy Fabric"
        url = uri("https://repo.legacyfabric.net/repository/legacyfabric")
    }
    maven {
        name = "OrnitheMC"
        url = uri("https://maven.ornithemc.net/releases")
    }
    mavenCentral()
}

dependencies {
    implementation("com.opencsv:opencsv:5.9")
    implementation("net.fabricmc:mapping-io:0.5.1")
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("org.ow2.asm:asm-tree:9.6")
    implementation("com.google.guava:guava:33.0.0-jre")
}

plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("legacy-mappings-plugin") {
            id = "io.github.legacymoddingmc.legacy-mappings-plugin"
            implementationClass = "io.github.legacymoddingmc.legacymappings.LegacyMappingsPlugin"
        }
    }
}
