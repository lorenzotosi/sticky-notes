<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Verification and traceability matrix

This document defines the project's acceptance cases C01–C55. It connects expected behavior and process checks to [functional requirements](requirements.md), supporting specifications, verification levels, and recorded evidence. A case ID identifies a scenario, not a single test method or a completion status.

## How to use the cases

- Functional cases link existing REQ identifiers; technical and process cases link the relevant local document instead of inventing functional requirements.
- `common` means shared Kotlin tests on both JVM and a real headless browser. Java/JS consumer tests check the adapters. Vue component tests, HTTP tests, Mongo integration tests, and production-browser E2E tests exercise different boundaries.
- A case can require several levels. A passing common test does not prove the API, database, or full UI behavior in the same row.
- Record evidence with the case ID, exact test or repeatable procedure, exercised layer, source revision, environment, result, and report/run/artifact reference. Link that evidence from this document; do not replace it with a bare “passed” label.
- Reference the same IDs in tests, architecture discussions, and the project report. Keep IDs stable; add new cases explicitly rather than renumbering existing cases.

These are acceptance criteria for the complete project. HTTP endpoints, Mongo persistence, Docker/E2E behavior, and administrative handover criteria remain requirements to verify at their respective integration boundaries. Their inclusion does not assert that those features or checks currently exist.

## Recorded evidence

| Cases | Exercised scope | Evidence |
| --- | --- | --- |
| C01–C20 | Shared JVM/ChromeHeadless domain scenarios and the specific Java, JS, and Vue consumer checks listed in R1 | [Shared-domain acceptance evidence](evidence/R1.md#acceptance-cases), including reproduction commands and observed results |

R1 does not prove the future API, Mongo mapper, or complete Kanban UI checks attached to C01–C20. Evidence for C21–C55 must be linked when those checks are actually executed; none is asserted by this transfer.

## Shared domain

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c01"></a>C01 | [REQ-02](requirements.md#req-02-content-length-and-normalization) | Nota senza contenuto oppure soli whitespace | Creare o modificare nota | INVALID_CONTENT; nessuna mutazione; errore campo content | common JVM+browser,API,UI | T017,T019,T035,T036 |
| <a id="c02"></a>C02 | [REQ-02](requirements.md#req-02-content-length-and-normalization) | Contenuti di 1,500,501 unità UTF-16 | Normalizzare e validare | 1 e 500 validi; 501 invalido. Limite applicato dopo trim e CRLF->LF | common JVM+browser | T017,T024 |
| <a id="c03"></a>C03 | [REQ-02](requirements.md#req-02-content-length-and-normalization) | Emoji fuori BMP, CRLF, spazi esterni e interni | Validare con JVM e JS | Identici testo normalizzato e lunghezza. Emoji conta 2; spazi interni conservati | common,facade JS | T017,T020,T024 |
| <a id="c04"></a>C04 | [domain](domain.md); [adr/001-board-aggregate](adr/001-board-aggregate.md) | Due entità stesso ID con contenuto diverso; due snapshot analoghi | Confrontare uguaglianza e mutare lista originale | Entità uguali per ID; snapshot strutturali diversi; lista originale non altera entità | common JVM+browser | T017 |
| <a id="c05"></a>C05 | [REQ-01](requirements.md#req-01-note-creation-and-defaults); [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [contracts](contracts.md) | Board vuota revision R | Creare, modificare e cancellare una nota | ID invariato su modifica; TODO/coda/default YELLOW; delete rimuove solo ID indicato | common,API,UI | T019,T035,T036 |
| <a id="c06"></a>C06 | [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [contracts](contracts.md) | ID duplicato o inesistente, enum colore sconosciuto | Inviare comandi | Duplicato/enum rifiutati; assente 404 via API; snapshot invariato | common,API | T019,T035,T045 |
| <a id="c07"></a>C07 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement) | WIP=1, nota A DOING e B TODO | Spostare B in DOING | WIP_LIMIT_REACHED; B resta TODO. Da browser e Java medesimo risultato | common,Java consumer,JS,API,UI | T021,T025,T037,T041 |
| <a id="c08"></a>C08 | [REQ-05](requirements.md#req-05-allowed-state-transitions); [REQ-07](requirements.md#req-07-in-column-reordering) | Una nota in ciascuno dei tre stati | Provare tutte le 9 coppie source/target | Solo TODO → DOING, DOING → TODO, DOING → DONE, DONE → DOING e i tre riordini nello stesso stato sono validi, salvo altri vincoli. | common,API | T021,T037 |
| <a id="c09"></a>C09 | [REQ-07](requirements.md#req-07-in-column-reordering) | Lista DOING [A,B,C] con WIP pieno | Riordinare A in posizione 2 dopo rimozione | [B,C,A], nessun rifiuto WIP e nessun duplicato/perdita | common,API,UI | T021,T037,T041 |
| <a id="c10"></a>C10 | [REQ-08](requirements.md#req-08-configurable-wip-limit) | Due note DOING, limite attuale 3 | Impostare limite 1,2,0,21,non intero | 1 rifiutato; 2 valido; altri payload fuori range/tipo rifiutati | common,API,UI | T021,T037,T042 |
| <a id="c11"></a>C11 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement); [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | Nota DOING bloccata | Tentare DONE o TODO, poi stesso-colonna | Cambi di stato NOTE_BLOCKED; riordino permesso; nota conta nel WIP | common,API,UI | T023,T037,T042 |
| <a id="c12"></a>C12 | [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | Nota TODO/DOING libera | Bloccare con motivo vuoto,1,200,201 unità | Vuoto e 201 invalidi; 1 e 200 validi; sblocco elimina motivo | common,API,UI | T023,T037,T042 |
| <a id="c13"></a>C13 | [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | Nota DONE | Tentare blocco e modifica testo/colore | Blocco rifiutato; testo/colore ammessi. Interfaccia riflette le regole | common,API,UI | T023,T037,T042 |
| <a id="c14"></a>C14 | [REQ-11](requirements.md#req-11-guarded-transition-to-done) | DOING con un item incompleto | Tentare DONE | CHECKLIST_INCOMPLETE; snapshot immutato | common,Java,JS,API,UI | T023,T025,T038,T043 |
| <a id="c15"></a>C15 | [REQ-11](requirements.md#req-11-guarded-transition-to-done) | DOING senza item o con tutti completati, non bloccata | Tentare DONE | Successo per entrambe le checklist; stato DONE | common,API,UI | T023,T038,T043 |
| <a id="c16"></a>C16 | [REQ-12](requirements.md#req-12-immutability-of-done-checklists--reopening) | Nota DONE | Aggiungere/rinominare/toggle/eliminare un item | NOTE_DONE_READ_ONLY per ogni mutazione checklist; dati invariati | common,API,UI | T023,T038,T043 |
| <a id="c17"></a>C17 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement); [REQ-12](requirements.md#req-12-immutability-of-done-checklists--reopening) | Nota DONE e DOING a capienza massima | Riaprire, poi liberare un posto e riprovare | Prima WIP_LIMIT_REACHED, poi DOING. Checklist resta coerente | common,API,UI | T023,T038,T043 |
| <a id="c18"></a>C18 | [REQ-03](requirements.md#req-03-board-and-note-capacity-limits) | Board con 200 note o nota con 20 item | Creare il successivo e poi eliminare/ricreare | BOARD_FULL/CHECKLIST_FULL; dopo eliminazione si può creare entro limite | common,API | T019,T023,T038 |
| <a id="c19"></a>C19 | [REQ-07](requirements.md#req-07-in-column-reordering) | Lista di destinazione dimensione 2 dopo rimozione | Usare indici -1,0,2,3 e frazione | 0 e 2 validi; -1,3 e frazione rifiutati senza clamp | common,API,UI | T021,T037,T041 |
| <a id="c20"></a>C20 | [contracts](contracts.md); [adr/003-interop](adr/003-interop.md) | JSON snapshot con duplicati, DONE incompleto, blocco DONE o WIP incoerente | Reidratare Board | Rifiuto diagnostico, nessun reset o salvataggio silenzioso | common,mapper Mongo | T027,T030,T032 |

## Persistence and concurrency

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c21"></a>C21 | [contracts](contracts.md); [architecture](architecture.md) | DB con revision R, client invia R-1 | Tentare modifica | 409 REVISION_CONFLICT con currentRevision; nessun salvataggio; una revision massima Int non può traboccare: rifiuto diagnostico prima del salvataggio | unit,API,Mongo | T031,T032,T035 |
| <a id="c22"></a>C22 | [architecture](architecture.md); [contracts](contracts.md) | WIP1, due TODO, due richieste sulla stessa revision | Eseguire ingressi concorrenti con barrier e connessioni diverse | Un successo e un409; una DOING; revision +1; zero lost update | Mongo integration,HTTP | T032,T044 |
| <a id="c23"></a>C23 | [contracts](contracts.md) | Revision stale e/o ID mancante o payload invalido | Inviare comandi ambigui | Precedenze di validazione descritte sotto rispettate; code stabile e HTTP corrispondente. | API | T031,T045 |
| <a id="c24"></a>C24 | [architecture](architecture.md) | Board persistita con ordine, colori, blocco e checklist | Riavviare backend e bootstrap | Snapshot invariato; bootstrap non sovrascrive board esistente | Mongo integration | T032 |

## User interface and accessibility

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c25"></a>C25 | [ux](ux.md) | Form con testo e richiesta fallita | Simulare400,503 e timeout | Draft conservato, messaggio utile, saving termina, niente finto successo | Vue unit,UI | T033,T036,T046 |
| <a id="c26"></a>C26 | [ux](ux.md); [contracts](contracts.md) | Due finestre leggono revision R | Prima salva; seconda salva testo diverso | Seconda riceve409, ricarica e conserva draft; nuovo invio solo esplicito | Vue,2 browser context | T033,T046,T053 |
| <a id="c27"></a>C27 | [ux](ux.md) | Server salva, risposta si perde per timeout | Client tenta recupero | Rilettura stato prima di reinvio; nessuna creazione automatica duplicata | Vue unit,E2E | T033,T046 |
| <a id="c28"></a>C28 | [ux](ux.md); [contracts](contracts.md) | Nota/item rimosso mentre è aperto in un altro editor | Inviare modifica dal vecchio editor | Conflitto/404 coerente; testo copiabile, nessuna ricreazione nascosta | Vue,E2E | T033,T046 |
| <a id="c29"></a>C29 | [REQ-07](requirements.md#req-07-in-column-reordering); [ux](ux.md) | Snapshot con tre stati, ordine e testo HTML letterale | Renderizzare board | Ordine rispettato; HTML mostrato come testo, nessun v-html/script | Vue component | T034 |
| <a id="c30"></a>C30 | [ux](ux.md) | Utente solo tastiera e zoom200% | Creare/editare/colorare/muovere/completare/eliminare | Tutte azioni raggiungibili, focus visibile/ripristinato, annunci e label presenti | Manuale UI,E2E selettivo | T034,T036,T041,T048,T068 |
| <a id="c31"></a>C31 | [ux](ux.md) | Viewport360px e contenuti massimi | Visualizzare e usare editor/board | Nessun controllo irraggiungibile; wrapping sensato; etichetta dei colori | Manuale UI | T034,T048,T068 |

## HTTP validation and availability

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c32"></a>C32 | [contracts](contracts.md) | API con JSON malformato, campi extra e tipi sbagliati | Inviare richieste dirette | 400 con code e messaggio senza stacktrace; nessuna mutazione | HTTP integration | T039,T045 |
| <a id="c33"></a>C33 | [contracts](contracts.md) | Comando JSON maggiore di64KiB | Inviare body e osservare DB | 413 e nessuna modifica; limite verificato nel percorso reale | HTTP integration | T039,T045 |
| <a id="c34"></a>C34 | [contracts](contracts.md); [deployment](deployment.md) | Mongo spento, poi riavviato | Leggere/scrivere e interrogare health | 503 su operazioni; liveness viva/readiness non sana; recupero senza perdita | HTTP integration,Docker | T039,T045,T052 |

## Containers, end-to-end behavior, and recovery

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c35"></a>C35 | [deployment](deployment.md) | Immagini candidate con versione V e commit H | Avviare app e ispezionare metadata | Entrambi non-root, labels V/H, health e collegamento API riusciti | Docker | T049,T050,T052 |
| <a id="c36"></a>C36 | [deployment](deployment.md) | Stack consegna appena avviato | Usare solo127.0.0.1:8080 e proxy /api | UI/API funzionano; DB/API senza porte host; errori API non diventano HTML | Compose,HTTP | T050,T051,T052 |
| <a id="c37"></a>C37 | [deployment](deployment.md) | Nota persistita su volume nominato | Stop/start stack senza-v | Nota, ordine e checklist conservati; stop normale non rimuove volume | Compose | T051,T052 |
| <a id="c38"></a>C38 | [REQ-01](requirements.md#req-01-note-creation-and-defaults); [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [contracts](contracts.md) | Stack reale e browser produzione | CRUD, colore e reload | Valori persistiti e ID coerenti; nessun dato finto | Playwright E2E | T053 |
| <a id="c39"></a>C39 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement) | Stack con WIP1 e due note | Spostare entrambe in DOING da UI | Prima riesce, seconda rifiutata e messaggio spiega WIP | Playwright E2E | T053 |
| <a id="c40"></a>C40 | [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | Nota bloccata con motivo | Tentare movimento poi sbloccare | Rifiuto prima e successo dopo, motivo leggibile | Playwright E2E | T053 |
| <a id="c41"></a>C41 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement); [REQ-11](requirements.md#req-11-guarded-transition-to-done); [REQ-12](requirements.md#req-12-immutability-of-done-checklists--reopening) | DOING con item incompleto | Completa -> toggle -> completa -> riapri | Rifiuto poi successo; DONE checklist read-only; riapertura rispetta capienza | Playwright E2E | T053 |
| <a id="c42"></a>C42 | [contracts](contracts.md); [ux](ux.md) | Due browser context separati | Edit concorrente sulla stessa revision | Nessun dato perso, draft conservato e resubmit esplicito | Playwright E2E | T053 |
| <a id="c43"></a>C43 | [deployment](deployment.md) | DB test con snapshot complesso | Backup e restore in volume nuovo | Snapshot equivalente, schema/versione documentati; DB originale intatto | Script integration | T054 |

## Documentation, CI, releases, installation, and audit

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c44"></a>C44 | [ci-cd](ci-cd.md); [index](index.md) | Bundle docs e sito Pages con base path repository | Aprire indice, guide e API | Tutti i link relativi risolvono; documentazione API effettivamente generata | Link check+manuale | T055,T068 |
| <a id="c45"></a>C45 | [ci-cd](ci-cd.md); [repository-governance](repository-governance.md) | PR con errore intenzionale Vue/domain/E2E | Eseguire CI e tentare merge | Check required rosso, merge e release bloccati, report disponibili | GitHub Actions | T014,T015,T056 |
| <a id="c46"></a>C46 | [ci-cd](ci-cd.md) | Bundle release candidato | Confrontare versioni e ricalcolare checksum | Versione/SHA coerenti; tutti gli asset presenti; nessun secret | Release verification | T057,T059 |
| <a id="c47"></a>C47 | [deployment](deployment.md) | Computer/runner della piattaforma dichiarata | Pull immagini per digest da registry | Pull riuscito, architettura supportata, labels coerenti | Registry+Docker | T058,T062 |
| <a id="c48"></a>C48 | [ci-cd](ci-cd.md) | Commit feat,fix,docs e chore(release) di prova | Simulare calcolo e flusso semantic-release | Incrementi corretti, docs no release, chore(release)patch; publish solo post-quality | Release dry-run+CI | T016,T059,T060 |
| <a id="c49"></a>C49 | [deployment](deployment.md) | Manifest parziale e vecchia release compatibile | Validare e provare rollback su stack test | Incompleto rilevato; tag immutabili; rollback conserva dati | Release+Docker | T061 |
| <a id="c50"></a>C50 | [deployment](deployment.md) | Cartella vuota e Docker Compose soltanto | Seguire quickstart bundle | App avviata senza Node/Gradle o cache locali del repository | Installazione indipendente | T062,T068 |
| <a id="c51"></a>C51 | [development](development.md) | Lockfile e immagini della consegna | Scansionare vulnerabilità/licenze/esposizione | Esiti reali documentati, problemi applicabili risolti/mitigati; niente falsa certificazione | Audit tecnico | T063 |

## Course traceability and handover

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c52"></a>C52 | [index](index.md) | Ogni obbligo slide esame | Seguire collegamenti traceability | Decisione, codice, test, run e artifact presenti e accessibili | Revisione documentale | T065 |
| <a id="c53"></a>C53 | [development](development.md) | Autore che non ha scritto lo script demo | Seguire percorso e recovery senza aiuto | Mostra tutti i casi principali; reset solo ambiente demo | Prova incrociata | T066,T068 |
| <a id="c54"></a>C54 | [repository-governance](repository-governance.md) | Commit/tag candidato e storia PR | Clonare e confrontare contributi dichiarati | Nessun file essenziale non tracciato; contributi/review autentici | Git audit | T069 |
| <a id="c55"></a>C55 | [repository-governance](repository-governance.md) | Bundle e indice finali, modalità amministrativa verificata | Inviare e verificare ricezione/accessi | Consegna ricevuta, docenti possono accedere, entrambi gli autori identificati | Checklist umana | T070,T072 |

## Error precedence for C23

The imported case originally referred to operational decision S09. Its required sequence is stated here so the case is self-contained.

For backend commands: structural decoding/validation → board load → expected revision → entity presence → domain validation → atomic conditional save (CAS). Within domain validation: existing entity → valid payload → DONE mutation restrictions → blocker → transition → checklist → WIP/capacity → destination index. Tests combining several invalid conditions must identify the expected first error.

HTTP expectations are defined in [API contracts](contracts.md): malformed requests use 400, missing entities 404, revision/domain conflicts 409, database unavailability 503, and unexpected or invalid-persisted-state diagnostics 500. The JS facade returns domain/decoding codes without HTTP statuses; a non-decodable JSON request returns `INVALID_REQUEST`. Client prevalidation cannot guarantee server success.

## Maintaining the matrix

Update case definitions when an accepted requirement changes, and update the affected specifications and tests in the same change. Add links to new evidence documents or CI artifacts after verification. Preserve the distinction between a designed behavior, an implemented test, and an observed successful run. The Sheet remains the scheduling tool; this versioned matrix is the repository reference for case meanings.
