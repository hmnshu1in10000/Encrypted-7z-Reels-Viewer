package com.example.reelsviewer.ui

import android.app.Activity
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

    var onUnlockListener: (() -> Unit)? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = resolveRealPath(uri)
                binding.editArchivePath.setText(path)
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
            val path = binding.editArchivePath.text?.toString()?.trim()
            val password = binding.editPassword.text?.toString()

            if (path.isNullOrEmpty() || !File(path).exists()) {
                Toast.makeText(requireContext(), "Please select a valid .7z archive file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            SessionManager.archiveFilePath = path
            SessionManager.rawPassword = password.toCharArray()
            SessionManager.isAuthenticated = true

            dismiss()
            onUnlockListener?.invoke()
        }
    }

    private fun resolveRealPath(uri: Uri): String {
        return uri.path ?: uri.toString()
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
