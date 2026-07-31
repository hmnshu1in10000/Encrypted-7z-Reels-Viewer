package com.example.reelsviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.reelsviewer.data.SessionManager
import com.example.reelsviewer.data.db.AppDatabase
import com.example.reelsviewer.databinding.FragmentLikedReelsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LikedReelsFragment : Fragment() {

    private var _binding: FragmentLikedReelsBinding? = null
    private val binding get() = _binding!!

    var onReelSelectListener: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLikedReelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarLiked.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val passphrase = SessionManager.rawPassword
        if (passphrase != null) {
            val db = AppDatabase.getInstance(requireContext(), passphrase)
            lifecycleScope.launch {
                db.likedReelDao().getAllLiked().collectLatest { likedList ->
                    if (likedList.isEmpty()) {
                        binding.textEmptyState.visibility = View.VISIBLE
                        binding.recyclerLikedReels.visibility = View.GONE
                    } else {
                        binding.textEmptyState.visibility = View.GONE
                        binding.recyclerLikedReels.visibility = View.VISIBLE
                        binding.recyclerLikedReels.layoutManager = GridLayoutManager(requireContext(), 2)
                        binding.recyclerLikedReels.adapter = LikedReelsAdapter(likedList) { entity ->
                            onReelSelectListener?.invoke(entity.videoId)
                            parentFragmentManager.popBackStack()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LikedReelsFragment"
        fun newInstance() = LikedReelsFragment()
    }
}
