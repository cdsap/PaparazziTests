import org.gradle.configurationcache.extensions.capitalized

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("app.cash.paparazzi")
}

android {
  namespace = "app.cash.paparazzi.sample"

  compileSdk = libs.versions.compileSdk.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
  }
  buildFeatures {
    compose = true
    viewBinding = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
  }
}

dependencies {

  implementation(libs.composeUi.material)
  testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
  testImplementation("io.github.cdsap:td-paparazzi-ext:0.1")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
  testImplementation(libs.testParameterInjector)
}

androidComponents {
  onVariants(selector().all()) { variant ->
    val mergeOutputTask = project.tasks.register(
      "mergePaparazzi${variant.name.capitalized()}Outputs", MergeOutputTask::class.java
    ) {
      dependsOn(tasks.named("test${variant.name.capitalized()}UnitTest"))
      artifactFiles.set(layout.projectDirectory.dir("build/reports/paparazzi"))
      outputFile.set(layout.projectDirectory.dir("build/reports/paparazzi-td"))
    }

    project.tasks.withType<Test>().configureEach {
      if (name == "test${variant.name.capitalized()}UnitTest") {
        finalizedBy(mergeOutputTask)
      }
    }
  }
}


@CacheableTask
abstract class MergeOutputTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val artifactFiles: DirectoryProperty

  @get:Internal
  val runList = mutableListOf<String>()

  @get:OutputDirectory
  abstract val outputFile: DirectoryProperty


  @TaskAction
  fun writeResourcesFile() {
    val inputDirectory = artifactFiles.get()
    if (inputDirectory.asFile.walkTopDown().count() > 1) {
      val foldersToCopy = listOf("runs", "images", "videos")
      val outputDirectory = outputFile.get()
      createOutputDirectories(outputDirectory)
      createStaticFiles(inputDirectory, outputDirectory)
      inputDirectory.asFile.walkTopDown()
        .filter { it.isDirectory && it.name.startsWith("td-") }
        .forEach {
          it.walkTopDown().forEach {
            if (it.isDirectory && foldersToCopy.contains(it.name)) {
              copyResources(it, outputDirectory)
              if (it.name == "runs") {
                extractRuns(it)
              }
            }
          }

        }
      writeRunsJs(outputDirectory)

    }
  }

  private fun copyResources(it: File, outputDirectory: Directory) {
    it.copyRecursively(
      File("$outputDirectory/${it.name}"), overwrite = true
    )
  }

  private fun extractRuns(it: File) {
    it.walkTopDown().filter { it -> it.name != "runs" }
      .forEach { runList.add(it.name.replace(".js", "")) }
  }

  private fun writeRunsJs(outputDirectory: Directory) {
    var runFormatted = ""
    runList.forEach {
      runFormatted += "\"$it\",\n"
    }

    File("$outputDirectory/index.js").writeText(
      """
          window.all_runs = [
            ${runFormatted.dropLast(1)}
          ];
        """.trimIndent()
    )
  }

  private fun createStaticFiles(
    inputDirectory: Directory, outputDirectory: Directory
  ) {
    inputDirectory.asFileTree.filter { it.name == "index.html" }.first()
      .copyTo(File("$outputDirectory/index.html"))
    inputDirectory.asFileTree.filter { it.name == "paparazzi.js" }.first()
      .copyTo(File("$outputDirectory/paparazzi.js"))
  }

  private fun createOutputDirectories(outputDirectory: Directory) {
    if (File("$outputDirectory/images").isDirectory && File("$outputDirectory/images").exists()) {
      File("$outputDirectory/images").deleteRecursively()
    }
    if (File("$outputDirectory/videos").isDirectory && File("$outputDirectory/videos").exists()) {
      File("$outputDirectory/videos").deleteRecursively()
    }
    if (File("$outputDirectory/runs").isDirectory && File("$outputDirectory/runs").exists()) {
      File("$outputDirectory/runs").deleteRecursively()
    }
    if (File("$outputDirectory/index.html").exists()) {
      File("$outputDirectory/index.html").delete()
    }
    if (File("$outputDirectory/index.js").exists()) {
      File("$outputDirectory/index.js").delete()
    }
    if (File("$outputDirectory/paparazzi.js").exists()) {
      File("$outputDirectory/paparazzi.js").delete()
    }
    val images = File("$outputDirectory/images")
    val runs = File("$outputDirectory/runs")
    val videos = File("$outputDirectory/videos")
    images.mkdir()
    runs.mkdir()
    videos.mkdir()
  }
}
