package util

import java.io.File

object JavaResourcesUtil {
    fun onEachResource(path: String, action: (String, File) -> Unit) {

        fun resource2file(path: String): File {
            val resourceURL = object {}.javaClass.getResource(path)
            return File(checkNotNull(resourceURL) { "Path not found: '$path'" }.file)
        }

        with(resource2file(path)) {
            this.walk().forEach { f ->
                action(f.path.removePrefix(this.path), f)
            }
        }
    }
}