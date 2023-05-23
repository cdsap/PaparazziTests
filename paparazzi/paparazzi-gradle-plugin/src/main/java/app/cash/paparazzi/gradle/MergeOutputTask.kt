/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@CacheableTask
abstract class MergeOutputTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val artifactFiles: DirectoryProperty

  @get:Internal
  val runs = mutableListOf<String>()

  @get:OutputDirectory
  abstract val outputFile: DirectoryProperty


  @TaskAction
  // TODO: figure out why this can't be removed as of Kotlin 1.6+
  @OptIn(ExperimentalStdlibApi::class)
  fun writeResourcesFile() {
    var init = false
    artifactFiles.get().asFile.walkTopDown().forEach {
      if (!init) {
        init = true
        Files.createDirectory(Paths.get("${outputFile.get()}/images"))
        Files.createDirectory(Paths.get("${outputFile.get()}/runs"))
        Files.createDirectory(Paths.get("${outputFile.get()}/videos"))
        var copiedIndex = false
        var copiedJs = false
        it.walkTopDown().forEach {
          if (it.name == "index.html" && !copiedIndex) {
            copiedIndex = true
            it.copyTo(File("${outputFile.get()}/${it.name}"))
          }
          if (it.name == "paparazzi.js" && !copiedJs) {
            copiedJs = true
            it.copyTo(File("${outputFile.get()}/${it.name}"))
          }
        }
      }
      if (it.isDirectory && it.name == "runs") {
        it.walkTopDown().forEach {
          if (it.isFile) {
            if (!File("${outputFile.get()}/runs/${it.name}").exists()) {
              it.copyTo(File("${outputFile.get()}/runs/${it.name}"))
            }
            runs.add(it.name.replace(".js", ""))
          }
        }
      }
      if (it.isDirectory && it.name == "videos") {
        it.walkTopDown().forEach {
          if (it.isFile && !File("${outputFile.get()}/videos/${it.name}").exists()) {
            it.copyTo(File("${outputFile.get()}/videos/${it.name}"))
          }
        }
      }
      if (it.isDirectory && it.name == "images") {
        it.walkTopDown().forEach {
          if (it.isFile && !File("${outputFile.get()}/images/${it.name}").exists()) {
            it.copyTo(File("${outputFile.get()}/images/${it.name}"))
          }
        }
      }

    }
    var a = ""
    runs.forEach {
      a += "  \"$it\",\n"
    }

    File("${outputFile.get()}/index.js").writeText(
      """
      window.all_runs = [
        ${a.dropLast(1)}
      ];
    """.trimIndent()
    )

    artifactFiles.get().asFile.walkTopDown().forEach {
      println(it.name)
      if (it.isDirectory && it.name != "images" && it.name != "runs" && it.name != "videos" && it.name != "paparazzi") {
        it.deleteRecursively()
      }
    }

  }
}
