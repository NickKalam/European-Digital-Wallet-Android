package com.digitalwallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.digitalwallet.R
import com.digitalwallet.adapter.DocumentListItem
import com.digitalwallet.adapter.DocumentsAdapter
import com.digitalwallet.databinding.FragmentDocumentsBinding
import com.digitalwallet.repository.UserRepository
import com.digitalwallet.repository.DocumentRepository
import com.digitalwallet.util.DatabaseProvider
import com.digitalwallet.util.SessionManager
import com.digitalwallet.viewmodel.AuthViewModel
import com.digitalwallet.viewmodel.DocumentViewModel
import com.digitalwallet.viewmodel.DocumentViewModelFactory
import com.digitalwallet.viewmodel.AuthViewModelFactory
import com.google.firebase.auth.FirebaseAuth

class DocumentsFragment : Fragment() {
    private var _binding: FragmentDocumentsBinding? = null
    private val binding get() = _binding!!

    private val documentsViewModel: DocumentViewModel by activityViewModels {
        DocumentViewModelFactory(
            DocumentRepository(DatabaseProvider.getDatabase(requireContext()))
        )
    }

    private val userViewModel: AuthViewModel by activityViewModels {
        AuthViewModelFactory(
            UserRepository(DatabaseProvider.getDatabase(requireContext()))
        )
    }

    private lateinit var adapter: DocumentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentsBinding.inflate(inflater, container, false)

        adapter = DocumentsAdapter(emptyList()) { document ->
            val action =
                DocumentsFragmentDirections.actionDocumentsFragmentToDocumentDetailFragment(document.documentId)
            findNavController().navigate(action)
        }
        binding.documentsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.documentsRecycler.adapter = adapter

        documentsViewModel.documents.observe(viewLifecycleOwner) { docs ->
            val sectioned = buildSectionedList(docs)
            adapter.submitList(sectioned)
        }

        val userId = SessionManager.getUserId(requireContext())
        if (userId != null) {
            documentsViewModel.loadUserDocuments(userId)
        } else {
            findNavController().navigate(R.id.action_documentsFragment_to_loginFragment)
        }

        binding.addDocumentButton.setOnClickListener {
            findNavController().navigate(R.id.action_documentsFragment_to_addDocumentFragment)
        }

        val toolbar=binding.topAppBar
        toolbar.inflateMenu(R.menu.documents_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    SessionManager.clearSession(requireContext())
                    findNavController().navigate(R.id.action_documentsFragment_to_loginFragment)
                    true
                }
                R.id.action_delete_user -> {
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.confirm_deletion_title))
                        .setMessage(getString(R.string.confirm_deletion_message))
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            if(userId!=null)
                                userViewModel.deleteUser(userId)

                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                    true
                }
                else -> false
            }
        }
        userViewModel.deleteUserResult.observe(viewLifecycleOwner) { res ->
            res ?: return@observe

            if (res.isSuccess) {
                toast(getString(R.string.profile_deleted_success))
                SessionManager.clearSession(requireContext())
                findNavController().navigate(R.id.action_documentsFragment_to_loginFragment)
            } else {
                toast(getString(R.string.generic_error))
            }
            userViewModel.clearDeleteUserResult()
        }


        return binding.root
    }

    // Section the list by type
    private fun buildSectionedList(docs: List<com.digitalwallet.model.Document>): List<DocumentListItem> {
        val grouped = docs.groupBy { it.type }
        val list = mutableListOf<DocumentListItem>()
        grouped.forEach { (type, documents) ->
            list.add(DocumentListItem.Header(type))
            documents.forEach { list.add(DocumentListItem.Item(it)) }
        }
        return list
    }

    override fun onResume() {
        super.onResume()
        val userId = SessionManager.getUserId(requireContext())
        if (userId != null) documentsViewModel.loadUserDocuments(userId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
