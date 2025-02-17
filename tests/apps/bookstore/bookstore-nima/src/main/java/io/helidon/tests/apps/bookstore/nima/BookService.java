/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.tests.apps.bookstore.nima;

import java.util.Collection;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.apps.bookstore.common.Book;
import io.helidon.tests.apps.bookstore.common.BookMapper;
import io.helidon.tests.apps.bookstore.common.BookStore;

import jakarta.json.JsonObject;

/**
 * Implements book service.
 */
public class BookService implements HttpService {

    private static final BookStore BOOK_STORE = new BookStore();
    private static final String ISBN_PARAM = "isbn";

    private final Main.JsonLibrary jsonLibrary;

    BookService(Config config) {
        jsonLibrary = Main.getJsonLibrary(config);
    }

    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getBooks)
                .post("/", this::postBook)
                .get("/{" + ISBN_PARAM + "}", this::getBook)
                .put("/{" + ISBN_PARAM + "}", this::putBook)
                .delete("/{" + ISBN_PARAM + "}", this::deleteBook);

        System.out.println("Using JSON library " + jsonLibrary);
    }

    private void getBooks(ServerRequest request, ServerResponse response) {
        Collection<Book> books = BOOK_STORE.getAll();
        switch (jsonLibrary) {
            case JSONP:
                response.send(BookMapper.encodeJsonp(books));
                break;
            case JSONB:
            case JACKSON:
                response.send(books);
                break;
            default:
                throw new RuntimeException("Unknown JSON library " + jsonLibrary);
        }
    }

    private void postBook(ServerRequest request, ServerResponse response) {
        switch (jsonLibrary) {
            case JSONP:
                JsonObject jo = request.content().as(JsonObject.class);
                addBook(BookMapper.decodeJsonp(jo), response);
                break;
            case JSONB:
            case JACKSON:
                Book book = request.content().as(Book.class);
                addBook(book, response);
                break;
            default:
                throw new RuntimeException("Unknown JSON library " + jsonLibrary);
        }
    }

    private void addBook(Book book, ServerResponse response) {
        if (BOOK_STORE.contains(book.getIsbn())) {
            response.status(Http.Status.CONFLICT_409).send();
        } else {
            BOOK_STORE.store(book);
            response.status(Http.Status.OK_200).send();
        }
    }

    private void getBook(ServerRequest request, ServerResponse response) {
        String isbn = request.path().pathParameters().value(ISBN_PARAM);
        Book book = BOOK_STORE.find(isbn);

        if (book == null) {
            response.status(Http.Status.NOT_FOUND_404).send();
            return;
        }

        switch (jsonLibrary) {
            case JSONP:
                response.send(BookMapper.encodeJsonp(book));
                break;
            case JSONB:
            case JACKSON:
                response.send(book);
                break;
            default:
                throw new RuntimeException("Unknown JSON library " + jsonLibrary);
        }
    }

    private void putBook(ServerRequest request, ServerResponse response) {
        switch (jsonLibrary) {
            case JSONP:
                JsonObject jo = request.content().as(JsonObject.class);
                updateBook(BookMapper.decodeJsonp(jo), response);
                break;
            case JSONB:
            case JACKSON:
                Book book = request.content().as(Book.class);
                updateBook(book, response);
                break;
            default:
                throw new RuntimeException("Unknown JSON library " + jsonLibrary);
        }
    }

    private void updateBook(Book book, ServerResponse response) {
        if (BOOK_STORE.contains(book.getIsbn())) {
            BOOK_STORE.store(book);
            response.status(Http.Status.OK_200).send();
        } else {
            response.status(Http.Status.NOT_FOUND_404).send();
        }
    }

    private void deleteBook(ServerRequest request, ServerResponse response) {
        String isbn = request.path().pathParameters().value(ISBN_PARAM);
        if (BOOK_STORE.contains(isbn)) {
            BOOK_STORE.remove(isbn);
            response.send();
        } else {
            response.status(Http.Status.NOT_FOUND_404).send();
        }
    }
}
