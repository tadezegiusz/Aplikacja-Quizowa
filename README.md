# QuizMaster – Aplikacja Quizowa Android 📱

QuizMaster to aplikacja mobilna stworzona w języku Kotlin z wykorzystaniem Jetpack Compose. Umożliwia rozwiązywanie quizów zarówno w trybie jednoosobowym, jak i wieloosobowym online z wykorzystaniem Firebase Firestore.

## Funkcjonalności

### 🎯 Tryb Solo

* Wybór quizu z lokalnej bazy danych (plik JSON).
* Losowanie kolejności odpowiedzi.
* Natychmiastowa informacja zwrotna po udzieleniu odpowiedzi.
* Obliczanie wyniku na podstawie liczby poprawnych odpowiedzi oraz czasu rozwiązania quizu.
* Ekran podsumowania z końcowym wynikiem.

### 👥 Tryb Multiplayer

* Pobieranie quizów z Firebase Firestore.
* Tworzenie lobby z unikalnym kodem.
* Dołączanie do istniejącego lobby za pomocą kodu.
* Własne nicki graczy bez konieczności logowania.
* Lista graczy oczekujących w lobby.
* Synchronizacja rozpoczęcia rozgrywki przez hosta.
* Automatyczne przejście wszystkich uczestników do quizu po starcie gry.

### 🏆 Ranking i wyniki

* Zapisywanie wyników graczy w Firestore.
* Oczekiwanie na zakończenie quizu przez wszystkich uczestników.
* Generowanie rankingu posortowanego według wyniku końcowego.
* Wyświetlanie liczby poprawnych odpowiedzi, czasu oraz punktacji każdego gracza.

## Technologie

* Kotlin
* Jetpack Compose
* Navigation Compose
* Firebase Firestore
* Gson
* Material 3

## Architektura projektu

Projekt składa się z kilku głównych modułów:

* **Menu główne** – wybór trybu gry.
* **QuizListScreen** – lista quizów dostępnych lokalnie.
* **QuizListScreenMultiplayer** – lista quizów online.
* **LobbyHostScreen** – tworzenie lobby.
* **LobbyJoinScreen** – dołączanie do lobby.
* **QuizScreen** – rozgrywka solo.
* **QuizScreenMultiplayer** – rozgrywka wieloosobowa.
* **ResultsScreen** – ranking graczy.
* **Firebase Firestore** – przechowywanie quizów, lobby i wyników.

## Punktacja

Wynik końcowy obliczany jest według wzoru:

`score = (liczba_poprawnych_odpowiedzi / czas_w_sekundach) × 10`

Dzięki temu premiowana jest zarówno poprawność odpowiedzi, jak i szybkość ich udzielania.

## Struktura danych quizu

Przykładowy quiz zapisany w JSON:

```json
{
  "id": "quiz1",
  "title": "Geografia",
  "questions": [
    {
      "question": "Stolica Polski?",
      "answers": ["Warszawa", "Kraków", "Gdańsk", "Poznań"],
      "correctAnswer": "Warszawa"
    }
  ]
}
```

## Możliwe kierunki rozwoju

* Logowanie użytkowników Firebase Authentication.
* Tworzenie quizów z poziomu aplikacji.
* Kategorie i poziomy trudności.
* Historia wyników graczy.
* Globalne rankingi.
* Powiadomienia o rozpoczęciu gry.
* Obsługa obrazków w pytaniach.

## Autor

Projekt wykonany w ramach nauki programowania aplikacji mobilnych w Android Studio z wykorzystaniem Jetpack Compose i Firebase.

