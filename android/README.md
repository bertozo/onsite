# GeoTracker

App Android (Kotlin + Jetpack Compose) para registrar quanto tempo você passa em um local, usando GPS.

## Como funciona

1. Ao abrir o app, ele pede permissão de localização e busca as coordenadas GPS atuais.
2. Você pode digitar uma **label opcional** (ex: "Escritório", "Academia").
3. Toque em **Start**: salva um registro com data/hora, latitude/longitude e a label.
4. O botão vira **Stop**. Ao tocar, salva outro registro com o horário de término (mesma localização/label).
5. O **Histórico** abaixo lista todos os registros, e calcula automaticamente a duração de cada sessão concluída (tempo entre Start e Stop).

Os dados ficam salvos localmente no dispositivo em um banco Room (SQLite), então o histórico persiste entre aberturas do app.

## Como abrir e rodar

1. Abra a pasta `geolocation-app` no **Android Studio** (versão recente, Iguana/Koala ou superior).
2. O Android Studio vai sincronizar o Gradle automaticamente. Como este projeto foi criado sem o Gradle instalado na máquina, o arquivo `gradle/wrapper/gradle-wrapper.jar` não existe ainda — na primeira sincronização o Android Studio oferece para criá-lo/baixar a distribuição do Gradle automaticamente (aceite o prompt). Se preferir gerar manualmente e tiver o Gradle instalado, rode `gradle wrapper` na raiz do projeto.
3. Conecte um dispositivo físico (recomendado, para GPS real) ou use um emulador com localização configurada (Extended Controls > Location).
4. Rode o app (Run ▶). Aceite a permissão de localização quando solicitado.

## Estrutura principal

- `app/src/main/java/.../MainActivity.kt` — UI em Jetpack Compose (tela principal, botões Start/Stop, histórico).
- `app/src/main/java/.../LocationTrackerViewModel.kt` — lógica de obtenção de localização (FusedLocationProviderClient) e gravação dos registros.
- `app/src/main/java/.../data/` — entidade Room (`LocationRecord`), DAO e banco de dados.

## Permissões

O app usa apenas `ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION` (localização em primeiro plano). Não há rastreamento em segundo plano.
