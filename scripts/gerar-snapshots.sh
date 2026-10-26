#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Gera os snapshots dos StructureDefinition de um pacote de conformidade e os
# grava de volta nos arquivos, para que a Camada 4 nao precise gera-los em
# tempo de execucao.
#
#   uso: ./scripts/gerar-snapshots.sh <subdiretorio-do-pacote> [heap]
#   exemplo: ./scripts/gerar-snapshots.sh redfm -Xmx3g
#
# POR QUE ISTO EXISTE, E POR QUE UM PROCESSO POR PERFIL
#
# Nenhum StructureDefinition publicado pela RNDS traz `snapshot`. Ate o RIRA
# isso era apenas custo de inicializacao. O REDFM mudou o quadro: seus perfis
# derivam do IG BR-Core em cadeias de ate tres niveis, e a JVM morre com
# OutOfMemoryError mesmo com 4 GB -- com apenas 102 artefatos e 8 MB em disco.
#
# A pilha do estouro mostra que a causa nao e volume: cada chamada a
# generateSnapshot constroi um FHIRPathEngine, cujo construtor converte TODOS
# os StructureDefinition conhecidos de R4 para R5 -- os 51 do pacote mais os
# ~650 do DefaultProfileValidationSupport. Gerar 51 snapshots numa unica JVM
# sao dezenas de milhares de conversoes. E custo quadratico DENTRO do HAPI.
#
# Dai um processo por perfil: cada JVM converte, grava e morre.
#
# O gerador le e grava DIRETO em src/main/resources, sem passar pelo classpath,
# para que cada iteracao enxergue o que a anterior gravou sem recompilar.
# ---------------------------------------------------------------------------
set -euo pipefail

SUB="${1:?uso: $0 <subdiretorio-do-pacote> [heap]   (ex.: redfm -Xmx3g)}"
HEAP="${2:--Xmx3g}"
PACOTE="rnds-br-connect-core/src/main/resources/conformance/$SUB"

[ -d "rnds-br-connect-core" ] || { echo "ERRO: rode a partir da raiz do repositorio." >&2; exit 1; }
[ -f "$PACOTE/index.txt" ] || { echo "ERRO: pacote nao encontrado: $PACOTE/index.txt" >&2; exit 1; }

CP_FILE="$(mktemp)"; LISTA="$(mktemp)"
trap 'rm -f "$CP_FILE" "$LISTA"' EXIT

echo "Compilando e montando o classpath..."
mvn -q -pl rnds-br-connect-core test-compile \
    dependency:build-classpath -Dmdep.outputFile="$CP_FILE" -Dmdep.includeScope=test

CP="rnds-br-connect-core/target/classes:rnds-br-connect-core/target/test-classes:$(cat "$CP_FILE")"
GERADOR="br.rndsconnect.tools.GeradorDeSnapshots"

echo "Levantando os perfis sem snapshot..."
java -Xmx1g -cp "$CP" "$GERADOR" "$PACOTE" 2>/dev/null > "$LISTA"
TOTAL=$(grep -c . "$LISTA" || true)
echo "  $TOTAL perfil(is) a processar"
echo

ok=0; falhou=0; n=0
: > /tmp/snapshots-falhas.log
while IFS= read -r url; do
  [ -z "$url" ] && continue
  n=$((n + 1))
  printf "[%3d/%3d] %-52s " "$n" "$TOTAL" "${url##*/}"
  if saida=$(java "$HEAP" -cp "$CP" "$GERADOR" "$PACOTE" "$url" 2>&1); then
    echo "$saida" | grep -qE "OK|JA TEM" && { echo "ok"; ok=$((ok + 1)); } \
      || { echo "?"; falhou=$((falhou + 1)); printf '=== %s ===\n%s\n' "$url" "$saida" >> /tmp/snapshots-falhas.log; }
  else
    echo "FALHOU"
    falhou=$((falhou + 1))
    printf '=== %s ===\n%s\n' "$url" "$saida" >> /tmp/snapshots-falhas.log
  fi
done < "$LISTA"

echo
echo "$ok gerado(s), $falhou falha(s)"
[ "$falhou" -gt 0 ] && echo "Detalhe das falhas: /tmp/snapshots-falhas.log"
echo
echo "Os snapshots foram gravados em src/main/resources; recompile antes de testar:"
echo "   mvn -q -pl rnds-br-connect-core process-resources"
