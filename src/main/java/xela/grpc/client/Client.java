package xela.grpc.client;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import xela.grpc.generated.BookProto;
import xela.grpc.generated.BookServiceGrpc;
import xela.grpc.server.Book;
import xela.grpc.server.BookServer;

import java.util.Iterator;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {

    private static final String SERVER = "localhost:8082";
    private static final Scanner SCANNER = new Scanner(System.in);

    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forTarget(SERVER).usePlaintext().build();
        BookServiceGrpc.BookServiceBlockingStub bookService = BookServiceGrpc.newBlockingStub(channel);
        System.out.println("Welcome to the book client. What do you want to do?");
        while(true) {
            System.out.println("[l] for list all books");
            System.out.println("[a] for adding a new book");
            System.out.println("[g] for getting a book");
            System.out.println("[u] for updating a book");
            System.out.println("[d] for deleting a book");
            System.out.println("[q] to quit");
            System.out.print(">");
            String line = SCANNER.nextLine();
            if(line == null || line.isEmpty()) {
                return;
            }
            try {
                switch (line) {
                    case "l" -> listBooks(bookService);
                    case "a" -> addBook(bookService);
                    case "g" -> getBook(bookService);
                    case "u" -> updateBook(bookService);
                    case "d" -> deleteBook(bookService);
                    case "q" -> {
                        System.out.println("Quitting...");
                        System.exit(0);
                    }
                    default -> System.out.printf("Unsupported option: %s%n", line);
                }
            } catch (io.grpc.StatusRuntimeException e) {
                System.err.printf("An error occurred during this operation: %s",e.getMessage());
            }

        }


    }




    private static String getUserInput(String field) {
        String value = "";
        while(value.length() == 0) {
            System.out.printf("Enter the book's %s:%n", field);
            System.out.print(">");
            String in = SCANNER.nextLine();
            if(in != null) value = in;
        }
        return value;
    }

    private static Book getBookFromUser() {
      System.out.println("Enter the book's details:");
      return new Book(getUserInput("title"), getUserInput("author"), getUserInput("isbn"));
    }

    private static void getBook(BookServiceGrpc.BookServiceBlockingStub bookService) {
        String isbn = getUserInput("isbn");
        Book b = Book.fromProtobuf(
                bookService.get(
                        BookProto.Isbn.newBuilder()
                                .setValue(isbn)
                                .build()
                )
        );
        System.out.printf("Here's your book: %s%n", b);


    }

    private static void deleteBook(BookServiceGrpc.BookServiceBlockingStub bookService) {
        System.out.println("Which book do you want to delete?");
        String isbn = getUserInput("isbn");
        BookProto.DeleteResponse response = bookService.delete(BookProto.Isbn.newBuilder().setValue(isbn).build());
        if(response.getSuccess()) {
            System.out.printf("Deleted book with isbn \"%s\" successfully%n", isbn);
            return;
        }
        System.err.printf("Failed to delete book with isbn \"%s\", because it does not exist.", isbn);
    }

    private static void updateBook(BookServiceGrpc.BookServiceBlockingStub bookService) {
        String isbn = getUserInput("isbn");
        Book newBook = getBookFromUser();
        BookProto.Book b = bookService.update(
                    BookProto.UpdateBookRequest
                            .newBuilder()
                            .setNewBook(newBook.toProtobuf())
                            .setIsbn(isbn)
                            .build());


        System.out.printf("Updated book with isbn \"%s\" to %s%n", isbn, Book.fromProtobuf(b));
    }
    private static void addBook(BookServiceGrpc.BookServiceBlockingStub bookService) {
        Book added = Book.fromProtobuf(bookService.add(getBookFromUser().toProtobuf()));
        System.out.printf("Successfully added book %s%n", added);
    }

    private static void listBooks(BookServiceGrpc.BookServiceBlockingStub bookService) {
        Iterator<BookProto.Book> books = bookService.listAll(BookProto.ListBooksRequest.getDefaultInstance());
        System.out.println( "Available books:");
        while(books.hasNext()) {
            Book b =  Book.fromProtobuf(books.next());
            System.out.println( b.toString());
        }
    }

}
