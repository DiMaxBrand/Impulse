package eu.siacs.conversations.utils

import java.io.File

class FileWriterException(file: File) : Exception("Could not write to ${file.absolutePath}") {
    internal constructor() : this(File(""))
}
