package com.example.smartvoiceassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(private val todoList: MutableList<TodoItem>) :
    RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val todoText: TextView = itemView.findViewById(R.id.todoText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.todo_item_layout, parent, false)
        return TodoViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val currentItem = todoList[position]
        holder.todoText.text = currentItem.text
    }

    override fun getItemCount() = todoList.size

    // Add a method to update the list
    fun addTodo(item: TodoItem) {
        todoList.add(item)
        notifyItemInserted(todoList.size - 1) // Efficient update
    }
    fun clearTodos() {
        val size = todoList.size
        todoList.clear()
        notifyItemRangeRemoved(0, size)
    }
}