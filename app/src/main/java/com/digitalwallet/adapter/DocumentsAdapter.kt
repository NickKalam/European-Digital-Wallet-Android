package com.digitalwallet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.digitalwallet.databinding.ItemDocumentBinding
import com.digitalwallet.databinding.ItemDocumentHeaderBinding
import com.digitalwallet.model.Document
import com.digitalwallet.model.DocumentType

sealed class DocumentListItem {
    data class Header(val type: DocumentType) : DocumentListItem()
    data class Item(val document: Document) : DocumentListItem()
}

class DocumentsAdapter(
    private var items: List<DocumentListItem>,
    private val onDocumentClick: (Document) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is DocumentListItem.Header -> TYPE_HEADER
            is DocumentListItem.Item -> TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            val binding = ItemDocumentHeaderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            HeaderViewHolder(binding)
        } else {
            val binding =
                ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            DocumentViewHolder(binding)
        }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DocumentListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is DocumentListItem.Item -> (holder as DocumentViewHolder).bind(
                item.document,
                onDocumentClick
            )
        }
    }

    fun submitList(newItems: List<DocumentListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HeaderViewHolder(private val binding: ItemDocumentHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: DocumentListItem.Header) {
            binding.headerText.text = header.type.name.replace('_', ' ')
        }
    }

    class DocumentViewHolder(private val binding: ItemDocumentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(doc: Document, onClick: (Document) -> Unit) {
            binding.docType.text = doc.type.name
            binding.docIssuer.text = doc.issuer
            binding.docStatus.text = doc.status.name
            binding.root.setOnClickListener { onClick(doc) }
        }
    }

}
