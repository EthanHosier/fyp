package org.library;

import java.time.LocalDate;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Member alice = new Member("m1", "Alice", Member.MemberType.STANDARD, 2020);
        Book book = new Book("b1", "Title", "Auth", "isbn", Book.BookStatus.AVAILABLE);
        Loan loan = new Loan("l1", "b1", "m1", LocalDate.now().minusDays(1), LocalDate.now().plusDays(7));
        LibrarySystem sys = new LibrarySystem(List.of(book), List.of(alice), List.of(loan));
        sys.validateMember(alice);
        System.out.println(sys.fooBar("m1", LocalDate.now()));
    }
}
