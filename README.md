# Bablino Stream Italy

Repository di plugin per CloudStream con provider italiani.

## Provider Disponibili

### Hattrick
Provider per stream sportivi live da Hattrick Sport.

**Canali:**
- DAZN 1
- DAZN 1 HD
- Live 2
- Live 7

**Status:** ✅ Attivo

## Installazione

1. Apri CloudStream
2. Vai su Impostazioni → Estensioni → Aggiungi Repository
3. Inserisci l'URL: `https://raw.githubusercontent.com/michelegolino/bablinostreamitaly/master/`
4. Installa i plugin desiderati

## Sviluppo

Questa repository usa Gradle per la build dei plugin.

### Compilare i plugin

```bash
./gradlew build
```

### Creare un nuovo provider

Usa `ExampleProvider` come template per creare nuovi provider.

## Note

- Alcuni stream potrebbero richiedere una VPN per funzionare correttamente
- I provider sono testati ma potrebbero non funzionare sempre a causa di cambiamenti nei siti sorgente

## Autore

michelegolino

## Licenza

Questo progetto è fornito "as is" senza garanzie di alcun tipo.
