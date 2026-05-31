package com.example.mytube.data.repository

import com.example.mytube.data.LocalDatabase
import com.example.mytube.data.entity.BookmarkEntity

class BookmarkRepository(private val db: LocalDatabase) {
    fun getAll(): List<BookmarkEntity> = db.getAllBookmarks()

    fun add(url: String, title: String) {
        db.insertBookmark(BookmarkEntity(url = url, title = title))
    }

    fun remove(url: String) {
        db.deleteBookmark(url)
    }

    fun exists(url: String): Boolean = db.bookmarkExists(url)
}
