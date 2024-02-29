import io.github.legacymoddingmc.legacymappings.task.PrepareEnigmaTask

// Use RFG to fetch the srg jar
plugins {
  id("com.gtnewhorizons.retrofuturagradle") version "1.3.33"
  id("io.github.legacymoddingmc.legacy-mappings-plugin")
}

minecraft {
  mcVersion.set("1.7.10")
}

repositories {
  maven {
    name = "Fabric Repository"
    url = uri("https://maven.fabricmc.net")
  }
}

repositories {
  maven {
    name = "forge"
    url = uri("https://maven.minecraftforge.net")
    mavenContent {
      includeGroup("net.minecraftforge")
      includeGroup("net.minecraftforge.srg2source")
      includeGroup("de.oceanlabs.mcp")
      includeGroup("cpw.mods")
    }
  }

  exclusiveContent {
    forRepository {
      ivy {
        name = "Enigma releases"
        url = uri("https://github.com/LegacyModdingMC/enigma/releases/download/")
        patternLayout {
          artifact("[revision]/[module]-[revision](-[classifier])(.[ext])")
        }
        metadataSources {
          artifact()
        }
      }
    }
    filter {
      includeGroup("io.github.legacymoddingmc")
    }
  }
}

// Fetch build number from Github Actions
val buildNumber = System.getenv().get("BUILD_NUMBER") ?: "local"
val minecraftVersion = "1.7.10"

val mappingVersion = "${minecraftVersion}+build.$buildNumber"

tasks.jar.configure { enabled = false }
tasks.reobfJar.configure { enabled = false }
tasks.reobfJar.configure { enabled = false }
tasks.packageMcLauncher.configure { enabled = false }
tasks.packagePatchedMc.configure { enabled = false }
tasks.compileJava.configure { enabled = false }

minecraft {
  useForgeEmbeddedMappings = false
  // hack: force generateForgeSrgMappings to rerun by setting a non-downloaded mapping, and outputting to a different
  // directory than the one it checks
  mcpMappingVersion = "11"
}

tasks.generateForgeSrgMappings {
  methodsCsv = tasks.exportMappings.flatMap { t -> t.outputCsvDir.file("methods.csv") }
  fieldsCsv = tasks.exportMappings.flatMap { t -> t.outputCsvDir.file("fields.csv") }

  // TODO use tiny once support for it is implemented
  //inputMcpTiny = tasks.exportMappings.flatMap { t -> t.outputTinyFile }

  notchToSrg = layout.buildDirectory.file("userdev_local/notch-srg.srg")
  notchToMcp = layout.buildDirectory.file("userdev_local/notch-mcp.srg")
  srgToMcp = layout.buildDirectory.file("userdev_local/srg-mcp.srg")
  mcpToSrg = layout.buildDirectory.file("userdev_local/mcp-srg.srg")
  mcpToNotch = layout.buildDirectory.file("userdev_local/mcp-notch.srg")
  srgExc = layout.buildDirectory.file("userdev_local/srg.exc")
  mcpExc = layout.buildDirectory.file("userdev_local/mcp.exc")
}

tasks.remapDecompiledJar {
  paramCsv = tasks.exportMappings.flatMap { t -> t.outputCsvDir.file("params.csv") }
}

val v2UnmergedMappingsJar = tasks.register<Jar>("v2UnmergedMappingsJar") {
  setDescription("Assembles a Yarn-style jar containing a tiny V2 mapping from the SRG namespace.");
  val mappings = tasks.exportMappings.flatMap { t -> t.outputTinyFile }
  group = "mapping build"
  archiveFileName = "legacymappings-${mappingVersion}-v2.jar"
  from(mappings) {
    rename("mappings.tiny", "mappings/mappings.tiny")
  }
  from(project.file("LICENSE.MCP")) {
    rename("LICENSE.MCP", "LICENSE")
  }
  destinationDirectory.set(file("build/libs"))
  manifest {
    attributes(Pair("Minecraft-Version-Id", minecraftVersion))
  }
}

val csvZip = tasks.register<Zip>("csvZip") {
  setDescription("Assembles an MCP-style zip containing CSV files for method, field and parameter mappings.");
  val mappings = tasks.exportMappings.flatMap { t -> t.outputCsvDir }
  group = "mapping build"
  archiveFileName = "legacymappings-${mappingVersion}-csv.zip"
  from(mappings)
  from(project.file("LICENSE.MCP")) {
    rename("LICENSE.MCP", "LICENSE")
  }
  destinationDirectory.set(file("build/libs"))
}

val packageMappings = tasks.register("packageMappings") {
  group = "mapping build"
  setDescription("Assembles all the mapping archives.");
  dependsOn(v2UnmergedMappingsJar, csvZip)
}

tasks.assemble.configure {
  dependsOn(packageMappings)
}

val openEnigma = tasks.register<JavaExec>("openEnigma") {
  group = "internal mapping"
  classpath = enigmaRuntime
  mainClass.set("org.quiltmc.enigma.gui.Main")
  jvmArgs("-Xmx2048M", "-Denigma.legacymodding=true")
  args(
          "-jar",
          tasks.named<PrepareEnigmaTask>("prepareEnigma").get().outputEnigmaJar.get().asFile,
          "-mappings",
          tasks.named<PrepareEnigmaTask>("prepareEnigma").get().outputEnigmaMapping.get().asFile
  )
}

val enigma = tasks.register("enigma") {
  setDescription("Opens Enigma for editing the mappings.");
  group = "mapping"
}

enigma.configure { dependsOn(tasks.saveEnigma) }
tasks.saveEnigma.configure { dependsOn(openEnigma) }
openEnigma.configure { dependsOn(tasks.prepareEnigma) }


tasks.jar.configure { enabled = false }
tasks.reobfJar.configure { enabled = false }
tasks.reobfJar.configure { enabled = false }
tasks.packageMcLauncher.configure { enabled = false }
tasks.packagePatchedMc.configure { enabled = false }
tasks.compileJava.configure { enabled = false }

val enigmaRuntime: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  enigmaRuntime("io.github.legacymoddingmc:enigma-swing:2.2.1-lmmc:all")
}

legacyMappings {
  userdev("net.minecraftforge:forge:1.7.10-10.13.4.1614-1.7.10:userdev")

  mcp("de.oceanlabs.mcp:mcp_stable:12-1.7.10@zip")
  mcp("de.oceanlabs.mcp:mcp_stable:22-1.8.9@zip")
  mcp("de.oceanlabs.mcp:mcp_stable:26-1.9.4@zip")
  mcp("de.oceanlabs.mcp:mcp_stable:29-1.10.2@zip")
  mcp("de.oceanlabs.mcp:mcp_stable:32-1.11@zip")
  mcp("de.oceanlabs.mcp:mcp_stable:39-1.12@zip")

  yarn("net.legacyfabric:yarn:1.7.10+build.458:mergedv2")
  feather("net.ornithemc:feather:1.7.10+build.26:mergedv2")

  layerOrder("yarn", "feather", "mcpPreferOlder")
  commentOrder("mcpPreferOlder", "mcpPreferNewer", "yarn", "feather")
}
