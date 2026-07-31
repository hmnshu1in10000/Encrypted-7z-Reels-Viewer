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
 * Handles AES-256 header and stream decryption with password callback.
 * Supports optional/empty passwords for unencrypted archives.
 */
class SevenZipExtractor(
    private val archivePath: String,
    private val password: String? = null
) {

    private class ArchivePasswordCallback(private val password: String?) : IArchiveOpenCallback, ICryptoGetTextPassword {
        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}
        override fun cryptoGetTextPassword(): String = password ?: ""
    }

    fun initSevenZip(context: Context) {
        try {
            SevenZip.initSevenZipFromPlatformJAR()
        } catch (e: Exception) {
            // Ignore if native libraries are pre-initialized
        }
    }

    /**
     * Open archive helper passing null for auto-detection of format (.7z, .zip, etc.)
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
            SevenZip.openInArchive(null, inStream, callback)
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

        val randomAccessFile = RandomAccessFile(file, "r")
        val inStream: IInStream = RandomAccessFileInStream(randomAccessFile)
        val callback = ArchivePasswordCallback(password)

        var inArchive: IInArchive? = null
        try {
            inArchive = SevenZip.openInArchive(null, inStream, callback)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                randomAccessFile.close()
            } catch (e: Exception) {
                e.printStackTrace()
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

        val randomAccessFile = RandomAccessFile(file, "r")
        val inStream: IInStream = RandomAccessFileInStream(randomAccessFile)
        var success = false
        val openCallback = ArchivePasswordCallback(password)

        var inArchive: IInArchive? = null
        try {
            inArchive = SevenZip.openInArchive(null, inStream, openCallback)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                randomAccessFile.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return success
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
