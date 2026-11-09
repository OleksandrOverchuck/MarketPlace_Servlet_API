# JollyCart - backend Servlet API + JDBC

Ten sam kontrakt API co wersja Spring Boot (ten sam frontend).
Zrealizowano na razie: polaczenie z baza, rejestracja (2 kroki z kodem e-mail), logowanie, /me, wylogowanie.

## Uruchomienie

Zmienne srodowiskowe (te same nazwy co w wersji Spring):

| Zmienna | Wymagana | Opis |
|---|---|---|
| `CON_PASSWORD` | tak | haslo uzytkownika MySQL `JollyCart_User` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | do rejestracji | konto Gmail i haslo aplikacji (wysylka kodu) |
| `DB_URL` | nie | domyslnie `jdbc:mysql://localhost:3306/jollycart_servlet?...` |
| `SERVER_PORT` | nie | domyslnie 8080 |

```
mvn compile exec:java
```

albo uruchom klase `pl.jollycart.Main` z IDE. Przed startem zatrzymaj wersje Spring Boot (ten sam port 8080).
