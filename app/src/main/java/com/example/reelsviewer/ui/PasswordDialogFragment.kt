package com.example.reelsviewer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.example.reelsviewer.data.SessionManager
import com.example.reelsviewer.databinding.DialogPasswordBinding
import java.io.File

class PasswordDialogFragment : DialogFragment() {

    private var _binding: DialogPasswordBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null
    var onUnlockListener: (() -> Unit)? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedUri = uri
                val pathDisplay = uri.path ?: uri.toString()
                binding.editArchivePath.setText(pathDisplay)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBrowseArchive.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        }

        binding.btnUnlock.setOnClickListener {
            val pathInput = binding.editArchivePath.text?.toString()?.trim()
            val passwordInput = binding.editPassword.text?.toString() ?: ""

            if (pathInput.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Please select a valid .7z archive file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var resolvedFile: File? = null
            val uri = selectedUri
            if (uri != null) {
                resolvedFile = getFileFromUri(requireContext(), uri)
            } else {
                val directFile = File(pathInput)
                if (directFile.exists()) {
                    resolvedFile = directFile
                }
            }

            if (resolvedFile == null || !resolvedFile.exists()) {
                Toast.makeText(requireContext(), "Could not access or copy selected archive file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            SessionManager.archiveFilePath = resolvedFile.absolutePath
            SessionManager.rawPassword = passwordInput.toCharArray()
            SessionManager.isAuthenticated = true

            dismiss()
            onUnlockListener?.invoke()
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "input_archive.7z")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PasswordDialogFragment"
        fun newInstance() = PasswordDialogFragment()
    }
}
