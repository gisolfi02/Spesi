<div align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="Logo Spesì" width="120" />

  # Spesì

  **Le tue spese, sotto controllo.**

  App Android open source per registrare e analizzare le spese personali, con un'interfaccia semplice e colorata.

  ![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
  ![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
  ![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
</div>

<p align="center">
  <a href="https://github.com/gisolfi02/Spesi/releases/latest">
    <img
      src="https://img.shields.io/badge/SCARICA-SPES%C3%8C-7138EB?style=for-the-badge&logo=android&logoColor=white"
      alt="Scarica Spesì"
    />
  </a>
</p>

## Che cos'è Spesì?

Spesì aiuta a tenere traccia delle spese quotidiane e a capire come vengono distribuite nel tempo. Non richiede la creazione di un account e le funzioni principali sono disponibili senza connessione Internet.

## Funzionalità

- **Dashboard mensile:** totale delle spese, confronto con il mese precedente e grafico a ciambella per categoria.
- **Gestione delle spese:** aggiunta, consultazione, modifica ed eliminazione delle singole transazioni.
- **Categorie:** sei categorie predefinite e possibilità di crearne di personalizzate con nome, icona e colore. Le categorie archiviate rimangono associate alle spese storiche.
- **Spese ricorrenti:** registrazione di spese con frequenza settimanale, mensile, trimestrale e altre periodicità.
- **Storico:** riepiloghi annuali, dettaglio dei singoli mesi ed esportazione delle transazioni mensili in CSV.
- **Notifiche facoltative:** riepiloghi settimanali e mensili delle spese.
- **Backup e ripristino:** esportazione e importazione dei dati tramite file JSON.

## Screenshot

<p align="center">
  <img src="docs/screenshots/home.png" width="190" alt="Home"/>
  <img src="docs/screenshots/aggiungi.png" width="190" alt="Aggiungi spesa"/>
  <img src="docs/screenshots/storico.png" width="190" alt="Storico"/>
  <img src="docs/screenshots/dettaglio-mese.png" width="190" alt="Dettaglio mese"/>
  <img src="docs/screenshots/impostazioni.png" width="190" alt="Impostazioni"/>
</p>

## Download e installazione

Spesì è disponibile gratuitamente per Android 8.0 e versioni successive.

1. Clicca su **Scarica Spesì**.
2. Nella pagina della release, espandi la sezione **Assets**.
3. Scarica il file `.apk` della versione più recente.
4. Apri il file sul tuo smartphone e autorizza
   l'installazione da questa sorgente, se richiesto.
5. Installa e avvia Spesì.

Non sono necessari account o connessione Internet
per utilizzare le funzionalità dell'app.

## Tecnologie

- **Kotlin** e **Jetpack Compose** per la UI Android.
- **Room / SQLite** per la persistenza locale delle spese.
- **DataStore** per le preferenze.
- **WorkManager** per le attività pianificate, comprese le notifiche e le ricorrenze.
- **Gradle** per la compilazione.

## Requisiti

- Android **8.0 (API 26)** o successivo.
- Per compilare il progetto: Android Studio con gli SDK richiesti dal progetto installati.

## Come compilare e provare l'app

1. Clona il repository:

   ```bash
   git clone https://github.com/gisolfi02/Spesi.git
   ```

2. Apri la cartella `Spesi` con **Android Studio**.
3. Attendi la sincronizzazione di Gradle e l'installazione degli SDK eventualmente richiesti.
4. Seleziona un emulatore o un dispositivo Android con il debug abilitato.
5. Premi **Run** per compilare e avviare l'app.

## Dati e privacy

Le spese vengono registrate nel database locale dell'app. Spesì non richiede un account né un server per funzionare. I file JSON di backup possono contenere informazioni finanziarie personali **in chiaro**: conservali in un luogo sicuro e condividili soltanto se necessario.

La gestione degli eventuali backup di sistema dipende anche dalle impostazioni del dispositivo Android.

## Contributi

Segnalazioni di bug, suggerimenti e proposte sono benvenuti nella sezione [Issues](https://github.com/gisolfi02/Spesi/issues).

## Licenza

Questo progetto è distribuito sotto licenza **MIT**. Consulta il file [LICENSE](LICENSE) per i termini completi.
