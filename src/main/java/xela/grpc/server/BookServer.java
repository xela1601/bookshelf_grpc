package xela.grpc.server;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.grpc.ServerBuilder;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;
import org.bson.Document;
import xela.grpc.generated.BookProto;
import xela.grpc.generated.BookServiceGrpc;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
public class BookServer {
    private static final Logger logger = Logger.getLogger(BookServer.class.getName());
    private static final int PORT =  Integer.parseInt(
            Optional.ofNullable(
                    System.getenv("GRPC_PORT"))
                    .orElseThrow(() -> new RuntimeException("GRPC_PORT is not set in the environment"))
    );
    private static final int MONGO_PORT =  Integer.parseInt(
            Optional.ofNullable(
                            System.getenv("MONGO_PORT"))
                    .orElseThrow(() -> new RuntimeException("MONGO_PORT is not set in the environment"))
    );
    private static final String MONGO_HOST =  Optional.ofNullable(System.getenv("MONGO_HOST"))
                    .orElseThrow(() -> new RuntimeException("MONGO_HOST is not set in the environment"));


    public static void main(String[] args) throws InterruptedException, IOException {
        //BookRepository bookRepository = new BookRepository();
        //addSampleBooks(bookRepository);
        BookServiceImpl service = new BookServiceImpl(MONGO_HOST, MONGO_PORT, "bookshelf", "books");
        io.grpc.Server server = ServerBuilder.forPort(PORT).addService(service).build();
        server.start();
        logger.info(String.format("Started gRPC server on port %d", PORT));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Received Shutdown Request");
            server.shutdown();
            logger.info("Successfully stopped the server");
            service.mongoClient.close();
            logger.info("Successfully closed database connection");
        }));
        server.awaitTermination();
        System.exit(0);
    }

    private static class BookServiceImpl extends BookServiceGrpc.BookServiceImplBase {

        private final MongoClient mongoClient;
        private final MongoDatabase mongoDatabase;
        private final MongoCollection<Document> bookCollection;
        public BookServiceImpl(String mongoHost, int mongoPort, String dbName, String collectionName) {
            this.mongoClient = MongoClients.create(String.format("mongodb://%s:%d", mongoHost, mongoPort));
            this.mongoDatabase = this.mongoClient.getDatabase(dbName);
            this.bookCollection = this.mongoDatabase.getCollection(collectionName);
            int added = 0;
            long start = System.nanoTime();
            for(Book b: sampleBooks()) {
                if(this.bookCollection.countDocuments(Filters.eq("isbn", b.getIsbn())) > 0) {
                    continue;
                }
                this.bookCollection.insertOne(b.toDocument());
                added++;
            }
            long elapsed = (System.nanoTime() - start) / 1000 / 1000;
            if(added > 0)
                logger.info(String.format("Added %d sample books in %d ms", added, elapsed));
        }

        @Override
        public void delete(BookProto.Isbn request, StreamObserver<BookProto.DeleteResponse> responseObserver) {
            String isbn = request.getValue();
            logger.info(String.format("Received delete request for isbn \"%s\"", isbn));
                DeleteResult result = this.bookCollection.deleteOne(Filters.eq("isbn", request.getValue()));
                if(result.getDeletedCount() == 0) {
                    logger.severe(String.format("Failed to delete book with isbn \"%s\"",isbn));
                    responseObserver.onError(Status.NOT_FOUND.asException());
                    return;
                }
                logger.info(String.format("Successfully deleted book with isbn \"%s\"", isbn));
                responseObserver.onNext(BookProto.DeleteResponse.newBuilder().setSuccess(true).build());
                responseObserver.onCompleted();
        }

        @Override
        public void update(BookProto.UpdateBookRequest request, StreamObserver<BookProto.Book> responseObserver) {
            String isbn = request.getIsbn();
            Book newBook = Book.fromProtobuf(request.getNewBook());
            logger.info(String.format("Received update request for isbn \"%s\" with updated book: %s", isbn, newBook));
                UpdateResult result = this.bookCollection.updateOne(
                        Filters.eq("isbn", isbn),
                        Updates.combine(
                                Updates.set("title", newBook.getTitle()),
                                Updates.set("author", newBook.getAuthor()),
                                Updates.set("isbn", newBook.getIsbn())
                        )
                );
                if(result.getMatchedCount() == 0) {
                    logger.severe(String.format("Book with isbn \"%s\" can't be updated, because it does not exist yet.", isbn));
                    responseObserver.onError(Status.NOT_FOUND.asException());
                }
                logger.info(String.format("Successfully updated book with isbn \"%s\" to %s", isbn, newBook));
                responseObserver.onNext(request.getNewBook());
                responseObserver.onCompleted();
        }

        @Override
        public void add(BookProto.Book request, StreamObserver<BookProto.Book> responseObserver) {
            Book newBook = Book.fromProtobuf(request);
            logger.info(String.format("Received add request with book %s", newBook));
            if(bookCollection.countDocuments(Filters.eq("isbn", newBook.getIsbn())) >= 1) {
                responseObserver.onError(Status.ALREADY_EXISTS.asException());
                logger.severe(String.format("A book with the given isbn \"%s\" already exists.", request.getIsbn()));
                return;
            }
            bookCollection.insertOne(newBook.toDocument());
            logger.info(String.format("Successfully added book %s", newBook));
            responseObserver.onNext(request);
            responseObserver.onCompleted();
        }

        @Override
        public void listAll(BookProto.ListBooksRequest request, StreamObserver<BookProto.Book> responseObserver) {
            logger.info("Received listAll request");
            try (MongoCursor<Document> cursor = this.bookCollection.find().cursor()) {
                while (cursor.hasNext()) {
                    Book b = Book.fromDocument(cursor.next());
                    responseObserver.onNext(b.toProtobuf());
                }
            }
            responseObserver.onCompleted();
        }


        @Override
        public void get(BookProto.Isbn request, StreamObserver<BookProto.Book> responseObserver) {
            String isbn = request.getValue();
            logger.info(String.format("Received get request for isbn \"%s\"", isbn));
            long count = bookCollection.countDocuments(Filters.eq("isbn", request.getValue()));
            if(count < 1) {
                logger.severe(String.format("Count not find book with isbn \"%s\"", isbn));
                responseObserver.onError(Status.NOT_FOUND.asException());
                return;
            }
            FindIterable<Document> findResult = this.bookCollection.find(Filters.eq("isbn", request.getValue()));
            Book b = Book.fromDocument(Objects.requireNonNull(findResult.first()));
            logger.info(String.format("Found book: %s", b));
            responseObserver.onNext(b.toProtobuf());
            responseObserver.onCompleted();
            }
        private static Collection<Book> sampleBooks() {
            Collection<Book> books = new ArrayList<>();
            books.add(new Book("Harry Potter and the Deathly Hallows", "J. K. Rowling","0-545-01022-5"));
            books.add(new Book("Eragon", "Christopher Paolini","0-375-82668-8"));
            books.add(new Book("Measuring the World", "Daniel Kehlmann", "3-498-03528-2"));
            books.add(new Book("Elantris", "Brandon Sanderson", "0765311771"));
            books.add(new Book("The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "0345391802"));
            books.add(new Book("The Martian", "Andy Weir", "0553418025"));
            books.add(new Book("Guards! Guards!", "Terry Pratchett", "0062225758"));
            books.add(new Book("Alice in Wonderland", "Lewis Carroll", "3458317422"));
            books.add(new Book("Life, the Universe and Everything", "Douglas Adams", "0345391829"));
            return books;
        }
    }


}

