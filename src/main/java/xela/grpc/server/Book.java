package xela.grpc.server;
import org.bson.Document;

import xela.grpc.generated.BookProto;

import javax.print.Doc;
import java.util.Objects;

/**
 * Book domain class.
 * <p>
 * Consists of isbn, title and author.
 * <p>
 * Knows how to (de)serialize itself to/from protobuf.
 */
public class Book {
    private final String isbn;
    private final String title;
    private final String author;

    public Book(String title, String author, String isbn) {
        this.title = Objects.requireNonNull(title, "title");
        this.author = Objects.requireNonNull(author, "author");
        this.isbn = Objects.requireNonNull(isbn, "isbn");
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }


    public BookProto.Book toProtobuf() {
        return BookProto.Book.newBuilder().setIsbn(isbn).setAuthor(author).setTitle(title).build();
    }

    public static Book fromDocument(Document document) {
        return new Book(
                document.getString("author"),
                document.getString("title"),
                document.getString("isbn")
        );
    }

    public Document toDocument() {
        return new Document("isbn", this.isbn).append("author", this.author).append("title", this.title);
    }

    public static Book fromProtobuf(BookProto.Book protobuf) {
        return new Book(protobuf.getTitle(), protobuf.getAuthor(),protobuf.getIsbn());
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Book book = (Book) o;

        if (!isbn.equals(book.isbn)) return false;
        if (!title.equals(book.title)) return false;
        return author.equals(book.author);
    }

    @Override
    public int hashCode() {
        int result = isbn.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + author.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("{ Author: %s, Title: %s, Isbn: %s }", author, title, isbn);
    }
}
