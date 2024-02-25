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
}

tasks.register<JavaExec>("enigma") {
  classpath = enigmaRuntime
  mainClass.set("cuchaz.enigma.gui.Main")
  jvmArgs("-Xmx2048M")
  args("-jar", "build/rfg/srg_merged_minecraft.jar", "-mappings", file("rezult").absolutePath)
}

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
  enigmaRuntime("cuchaz:enigma-swing:2.4.1")
  enigmaRuntime("cuchaz:enigma-cli:2.4.1")
}

tasks.register("hello") {
  doLast {
    val files = project.configurations.getByName("mapping").files
    println(files)
  }
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

@DisableCachingByDefault
abstract class EnigmaTask() : JavaExec() {
  /*@InputFile
  abstract fun getJar() : RegularFileProperty

  @Input
  abstract fun getMappings() : Property<File>*/

  init {
    classpath = enigmaRuntime
    mainClass.set("cuchaz.enigma.gui.Main")
    jvmArgs?.add("-Xmx2048m")
  }
/*
  @TaskAction
  override fun exec() {
    args.add("-jar")
    args.add(getJar().get().asFile.absolutePath)
    args.add("-mappings")
    args.add(getMappings().get().absolutePath)
    //args.add("-profile")
    //args.add("enigma_profile.json")
    super.exec()
  }*/
}
