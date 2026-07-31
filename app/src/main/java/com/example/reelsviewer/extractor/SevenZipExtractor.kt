package com.example.reelsviewer.extractor

import android.content.Context
import com.example.reelsviewer.data.VideoItem
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale

/**
 * 7z Decryption Worker wrapping SevenZipJBinding native extraction.
 * Explicitly initializes native platform bindings to prevent codec missing errors.
 */
class SevenZipExtractor(
    private val archivePath: String,
    private val password: String? = null,
    context: Context? = null
) {

    init {
        context?.let { initSevenZip(it) }
    }

    private class ArchivePasswordCallback(private val password: String?) : IArchiveOpenCallback, ICryptoGetTextPassword {
        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}
        override fun cryptoGetTextPassword(): String = password ?: ""
    }

    fun initSevenZip(context: Context) {
        try {
            SevenZip.initSevenZipFromPlatform()
        } catch (e: Exception) {
            try {
                SevenZip.initSevenZipFromPlatformJAR()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Open archive helper with fallback format auto-detection.
     */
    fun openArchive(filePath: String, password: String? = null): IInArchive? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return null
            }
            val randomAccessFile = RandomAccessFile(file, "r")
            val inStream: IInStream = RandomAccessFileInStream(randomAccessFile)
            val callback = ArchivePasswordCallback(password)
            try {
                SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, inStream, callback)
            } catch (e: Exception) {
                SevenZip.openInArchive(null, inStream, callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getArchiveEntries(inArchive: IInArchive?): List<String> {
        if (inArchive == null) return emptyList()
        val fileList = mutableListOf<String>()
        return try {
            val count = inArchive.numberOfItems
            for (i in 0 until count) {
                val path = inArchive.getStringProperty(i, PropID.PATH)
                if (path != null) {
                    fileList.add(path.toString())
                }
            }
            fileList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun closeArchive(inArchive: IInArchive?) {
        try {
            inArchive?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Scan the central directory header to get all MP4/MKV/MOV/WEBM video entries.
     */
    fun scanVideoItems(): List<VideoItem> {
        val videoItems = mutableListOf<VideoItem>()
        val file = File(archivePath)
        if (!file.exists() || !file.canRead()) return videoItems

        var randomAccessFile: RandomAccessFile? = null
        var inStream: IInStream? = null
        var inArchive: IInArchive? = null

        try {
            randomAccessFile = RandomAccessFile(file, "r")
            inStream = RandomAccessFileInStream(randomAccessFile)
            val callback = ArchivePasswordCallback(password)

            try {
                inArchive = SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, inStream, callback)
            } catch (e: Exception) {
                inArchive = SevenZip.openInArchive(null, inStream, callback)
            }

            if (inArchive != null) {
                val itemCount = inArchive.numberOfItems
                val videoExtensions = setOf("mp4", "mkv", "mov", "webm", "avi", "3gp")

                for (i in 0 until itemCount) {
                    val path = inArchive.getStringProperty(i, PropID.PATH) ?: continue
                    val isFolder = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false

                    if (!isFolder) {
                        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (videoExtensions.contains(extension)) {
                            val size = (inArchive.getProperty(i, PropID.SIZE) as? Long) ?: 0L
                            val packedSize = (inArchive.getProperty(i, PropID.PACKED_SIZE) as? Long) ?: 0L
                            val filename = File(path).name
                            val videoId = sha256(path)

                            videoItems.add(
                                VideoItem(
                                    videoId = videoId,
                                    relativePath = path,
                                    filename = filename,
                                    archiveIndex = i,
                                    compressedSize = packedSize,
                                    uncompressedSize = size
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                inArchive?.close()
                inStream?.close()
                randomAccessFile?.close()
            } catch (ignored: Exception) {
            }
        }

        return videoItems
    }

    /**
     * Decrypt and extract a specific entry by its central directory index to local temporary cache file.
     */
    fun extractVideoEntry(archiveIndex: Int, outputFile: File): Boolean {
        val file = File(archivePath)
        if (!file.exists() || !file.canRead()) return false

        var randomAccessFile: RandomAccessFile? = null
        var inStream: IInStream? = null
        var inArchive: IInArchive? = null
        var success = false
        val openCallback = ArchivePasswordCallback(password)

        try {
            randomAccessFile = RandomAccessFile(file, "r")
            inStream = RandomAccessFileInStream(randomAccessFile)

            try {
                inArchive = SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, inStream, openCallback)
            } catch (e: Exception) {
                inArchive = SevenZip.openInArchive(null, inStream, openCallback)
            }

            if (inArchive != null) {
                outputFile.parentFile?.mkdirs()
                val fos = FileOutputStream(outputFile)

                val extractCallback = object : IArchiveExtractCallback, ICryptoGetTextPassword {
                    override fun cryptoGetTextPassword(): String = password ?: ""

                    override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
                        if (index != archiveIndex || extractAskMode != ExtractAskMode.EXTRACT) return null
                        return ISequentialOutStream { data ->
                            fos.write(data)
                            data.size
                        }
                    }

                    override fun prepareOperation(extractAskMode: ExtractAskMode?) {}
                    override fun setOperationResult(extractOperationResult: ExtractOperationResult?) {
                        if (extractOperationResult == ExtractOperationResult.OK) {
                            success = true
                        }
                    }
                    override fun setCompleted(completeValue: Long) {}
                    override fun setTotal(total: Long) {}
                }

                inArchive.extract(intArrayOf(archiveIndex), false, extractCallback)
                fos.flush()
                fos.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            success = false
        } finally {
            try {
                inArchive?.close()
                inStream?.close()
                randomAccessFile?.close()
            } catch (ignored: Exception) {
            }
        }

        return success
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
