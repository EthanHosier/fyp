package org.library;

public class Book {
    public enum BookStatus { AVAILABLE, LENT, LOST }

    private final String id;
    private final String title;
    private final String author;
    private final String isbn;
    private BookStatus status;

    public Book(String id, String title, String author, String isbn, BookStatus status) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.status = status;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public BookStatus getStatus() { return status; }
    public void setStatus(BookStatus s) { this.status = s; }
}
