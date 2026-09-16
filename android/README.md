# OnSite

App Android (Kotlin + Jetpack Compose) para registrar horas trabalhadas por local e gerar invoices em PDF. Feito para prestadores de serviço autônomos (formato australiano: ABN, BSB).

## Funcionalidades

- **Start tracking** — escolha empresa, local e tipo de serviço e toque em **Start**; o app grava a posição GPS. **Stop** encerra a sessão e mostra o resumo. Também é possível **adicionar sessões manualmente** (data, início, duração).
- **Cadastros** — Companies (clientes, com ABN/telefone/e-mail), Sites (locais, com busca de endereço), Job types (tipos de serviço). Todos com edição e exclusão.
- **Profile** — seus dados (nome, cargo, telefone, e-mail, ABN, foto) e dados bancários (BSB, conta) usados na invoice.
- **Reports** — sessões por período, com colunas configuráveis (Data, Local, Endereço, Empresa, Tipo de serviço, Início, Fim, Horas).
- **Invoice em PDF** — a partir do report, escolha a empresa, número e valor/hora; o PDF usa um template fixo (A4) com as mesmas colunas do report e é aberto no share sheet.
- **Idiomas** — inglês, português e espanhol, selecionáveis em Settings (além de tema claro/escuro).

Os dados ficam salvos localmente (Room/SQLite). Não há rastreamento em segundo plano.

## Como rodar

1. Abra a pasta do projeto no **Android Studio** (Iguana/Koala ou superior) e deixe o Gradle sincronizar. O `gradle/wrapper/gradle-wrapper.jar` não é versionado — o Android Studio o gera no primeiro sync (ou rode `gradle wrapper` na raiz).
2. Conecte um dispositivo físico (recomendado para GPS real) ou um emulador com localização configurada.
3. Run ▶ e aceite a permissão de localização.

Pela linha de comando: `./gradlew assembleDebug` (APK) ou `./gradlew installDebug` (instala no dispositivo conectado).

## Estrutura

```
app/src/main/java/com/rodolfobertozo/onsite/
├── MainActivity.kt            # toda a UI Compose (menu, telas, diálogos)
├── *ViewModel.kt              # um ViewModel por área (tracker, companies, sites, job types, profile, invoice, report, settings)
├── AppLocale.kt               # seleção de idioma (SharedPreferences + attachBaseContext)
├── Validators.kt              # regras de ABN, BSB, conta, telefone, e-mail
├── AddressSearch.kt           # busca de endereços (Photon/OSM, restrita à Austrália)
├── data/                      # Room: entidades, DAOs, AppDatabase (migrações)
├── invoice/                   # InvoiceData (linhas por dia) + InvoicePdfTemplate (layout A4)
└── report/ReportColumn.kt     # template de colunas compartilhado por report e PDF
```

## Busca de endereço

Usa a API pública [Photon](https://photon.komoot.io) (OpenStreetMap), limitada ao bounding box da Austrália. Requer `INTERNET`.
