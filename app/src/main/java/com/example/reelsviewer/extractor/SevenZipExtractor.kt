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
 */
class SevenZipExtractor(
    private val archivePath: String,
    private val password: String
) {

    private class ArchivePasswordCallback(private val password: String) : IArchiveOpenCallback, ICryptoGetTextPassword {
        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}
        override fun cryptoGetTextPassword(): String = password
    }

    fun initSevenZip(context: Context) {
        try {
            SevenZip.initSevenZipFromPlatformJAR()
        } catch (e: Exception) {
            // Ignore if native libraries are pre-initialized
        }
    }

    /**
     * Scan the central directory header to get all MP4/MKV/MOV/WEBM video entries.
     */
    fun scanVideoItems(): List<VideoItem> {
        val randomAccessFile = RandomAccessFile(archivePath, "r")
        val inStream: IInStream = RandomAccessFileInStream(randomAccessFile)
        val videoItems = mutableListOf<VideoItem>()
        val callback = ArchivePasswordCallback(password)

        var inArchive: IInArchive? = null
        try {
            inArchive = SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, inStream, callback)
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
        } finally {
            inArchive?.close()
            randomAccessFile.close()
        }

        return videoItems
    }

    /**
     * Decrypt and extract a specific entry by its central directory index to local temporary cache file.
     */
    fun extractVideoEntry(archiveIndex: Int, outputFile: File): Boolean {
        val randomAccessFile = RandomAccessFile(archivePath, "r")
        val inStream: IInStream = RandomAccessFileInStream(randomAccessFile)
        var success = false
        val openCallback = ArchivePasswordCallback(password)

        var inArchive: IInArchive? = null
        try {
            inArchive = SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, inStream, openCallback)
            if (inArchive != null) {
                outputFile.parentFile?.mkdirs()
                val fos = FileOutputStream(outputFile)

                val extractCallback = object : IArchiveExtractCallback, ICryptoGetTextPassword {
                    override fun cryptoGetTextPassword(): String = password

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
            inArchive?.close()
            randomAccessFile.close()
        }

        return success
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
