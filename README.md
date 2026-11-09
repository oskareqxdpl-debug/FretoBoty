# Bot Panel (Fabric 1.21.4)

## Jak dostać gotowy .jar (bez instalowania czegokolwiek)
1. Załóż konto na github.com i utwórz nowe repozytorium.
2. Wrzuć tam CAŁĄ zawartość tego folderu (razem z folderem `.github`). Commit changes.
3. Zakładka **Actions** -> poczekaj na zielony build -> na dole **Artifacts** -> pobierz `botmod-jar`.
4. Jeśli build jest czerwony, wklej mi log błędu.

## Instalacja
Fabric Loader 1.21.4 + Fabric API 0.119.2+1.21.4 + ten .jar w folderze `mods`.

## Obsługa
- Panel: klawisz **K** albo przycisk **Boty** w menu głównym / pauzy.
- Wpisz **nick** i **IP serwera** (sam adres, bez portu; mod sam znajdzie port jak normalna gra), kliknij **Dodaj bota**.
  Do 3 botów naraz, każdy ma własną konsolę. Przycisk **Mój IP** wpisuje serwer, na którym jesteś.
- Klik w kolumnę bota = wybór (jasna ramka). Pole na dole wysyła wiadomość lub `/komendę` wybranemu botowi
  (albo wszystkim, po przełączeniu "Wybrany bot / Wszystkie boty").
- Ruch: W/A/S/D, Spacja, strzałki (gdy nie piszesz w polu) albo przyciski obok hotbara.
- **Hotbar** bota (na dole): lewy klik = wybierz slot, **prawy klik = wybierz slot i kliknij prawym** (np. kompas).
  Klawisze 1-9 wybierają slot, R = prawy klik.
- Gdy serwer otworzy okno (menu trybów z kompasu), pojawia się ono na środku. Klikasz w slot, żeby wybrać tryb.
- **EQ** w nagłówku bota: podgląd ekwipunku (klik = wyrzuć 1 szt., Shift+klik = cały stack).
- **Kam**: prawdziwy widok z oczu bota (spectator + /spectate, wymaga OP, wyjście Shift).

## Diagnostyka
W konsoli bota są szare linie `[dbg]` z numerami pakietów. Jeśli bot nie wchodzi albo coś nie działa,
skopiuj całą konsolę bota i wyślij mi.

## Ograniczenia
- Tylko serwery offline (nonpremium). Bot nie renderuje świata (nie ma widoku 3D), bot nie zna terenu.
- Numery pakietów są pisane z pamięci dla 1.21.4 i mogą wymagać poprawek po twoim logu.
