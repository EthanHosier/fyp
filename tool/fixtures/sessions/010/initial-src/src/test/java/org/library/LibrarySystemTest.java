package org.library;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.library.Book.BookStatus;
import org.library.Member.MemberType;

import static org.junit.jupiter.api.Assertions.*;

public class LibrarySystemTest {

    private LibrarySystem freshSystem(Loan loan, Member member, Book book) {
        List<Book> bs = new ArrayList<>();
        if (book != null) bs.add(book);
        List<Member> ms = new ArrayList<>();
        if (member != null) ms.add(member);
        List<Loan> ls = new ArrayList<>();
        if (loan != null) ls.add(loan);
        return new LibrarySystem(bs, ms, ls);
    }

    @Test
    void lendBookMarksLent() {
        Book book = new Book("b1", "T", "A", "i", BookStatus.AVAILABLE);
        Member m = new Member("m1", "Alice", MemberType.STANDARD, 2020);
        LibrarySystem sys = freshSystem(null, m, book);
        double cost = sys.bulkLendBooks("m1", List.of("b1"));
        assertEquals(BookStatus.LENT, book.getStatus());
        assertTrue(cost > 0.0);
    }

    @Test
    void returnOnTimeNoFee() {
        Book book = new Book("b1", "T", "A", "i", BookStatus.LENT);
        Member m = new Member("m1", "Alice", MemberType.STANDARD, 2020);
        LocalDate today = LocalDate.of(2024, 6, 1);
        Loan loan = new Loan("l1", "b1", "m1", today.minusDays(5), today.plusDays(2));
        LibrarySystem sys = freshSystem(loan, m, book);
        double fee = sys.processReturn("l1", today);
        assertEquals(0.0, fee);
        assertTrue(loan.isReturned());
    }

    @Test
    void returnOverdueChargesFee() {
        Book book = new Book("b1", "T", "A", "i", BookStatus.LENT);
        Member m = new Member("m1", "Alice", MemberType.STANDARD, 2020);
        LocalDate today = LocalDate.of(2024, 6, 20);
        Loan loan = new Loan("l1", "b1", "m1", today.minusDays(20), today.minusDays(10));
        LibrarySystem sys = freshSystem(loan, m, book);
        double fee = sys.processReturn("l1", today);
        assertTrue(fee > 0.0);
        assertTrue(loan.isReturned());
        assertFalse(sys.getNotifications().isEmpty());
    }

    @Test
    void bulkLendTierTransitions() {
        Member m = new Member("m1", "Alice", MemberType.STANDARD, 2020);
        List<Book> books = new ArrayList<>();
        List<String> ids10 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            books.add(new Book("b" + i, "t", "a", "i", BookStatus.AVAILABLE));
            ids10.add("b" + i);
        }
        LibrarySystem sys = new LibrarySystem(books, List.of(m), new ArrayList<>());
        double cost10 = sys.bulkLendBooks("m1", ids10);
        // 10 books * 5.0 * 0.85 = 42.5
        assertEquals(42.5, cost10, 0.001);

        // Now 5-tier
        List<Book> books2 = new ArrayList<>();
        List<String> ids5 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            books2.add(new Book("c" + i, "t", "a", "i", BookStatus.AVAILABLE));
            ids5.add("c" + i);
        }
        Member m2 = new Member("m2", "Bob", MemberType.STANDARD, 2020);
        LibrarySystem sys2 = new LibrarySystem(books2, List.of(m2), new ArrayList<>());
        double cost5 = sys2.bulkLendBooks("m2", ids5);
        // 5*5*0.95 = 23.75
        assertEquals(23.75, cost5, 0.001);
    }

    @Test
    void validateMemberHappyAndSad() {
        Member good = new Member("m1", "Alice", MemberType.STANDARD, 2020);
        Member bad = new Member("m2", "", MemberType.STANDARD, 2020);
        LibrarySystem sys = freshSystem(null, good, null);
        assertTrue(sys.validateMember(good));
        assertFalse(sys.validateMember(bad));
        assertFalse(sys.validateMember(null));
    }
}
