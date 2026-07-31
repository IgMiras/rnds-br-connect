#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Copia os arquivos de definicao FHIR da RNDS para o classpath do core e gera
# o indice que o ConformancePackage le em tempo de carga.
#
#   uso: ./scripts/copiar-conformance.sh <diretorio-com-os-XMLs-da-RNDS>
#
# Varre RECURSIVAMENTE. O DATASUS distribui os artefatos em subpastas
# (CodeSystem/, Extension/, Profile/, ValueSet/) e a estrutura e PRESERVADA no
# destino: o index.txt guarda caminhos relativos, o que elimina colisao de
# nomes entre subpastas e mantem o pacote parecido com a distribuicao oficial.
#
# Fonte dos artefatos: Portal de Servicos do DATASUS, "arquivos de definicao
# da adocao FHIR R4" (RIA-R 11.2; RIA-C 11.2; REL 11.2).
# ---------------------------------------------------------------------------
set -euo pipefail

ORIGEM="${1:?uso: $0 <diretorio-com-os-XMLs-da-RNDS>}"
DESTINO="rnds-br-connect-core/src/main/resources/conformance"

if [ ! -d "$ORIGEM" ]; then
  echo "ERRO: diretorio de origem nao existe: $ORIGEM" >&2
  exit 1
fi
if [ ! -d "rnds-br-connect-core" ]; then
  echo "ERRO: rode este script a partir da RAIZ do repositorio." >&2
  echo "      (o diretorio atual e $(pwd))" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

rm -rf "$DESTINO"
mkdir -p "$DESTINO"

total=0; aceitos=0
: > "$TMP/lista"; : > "$TMP/urls"

while IFS= read -r -d '' f; do
  total=$((total+1))
  rel="${f#"$ORIGEM"/}"

  tipo="$(head -c 8000 "$f" \
    | grep -oE '<(StructureDefinition|ValueSet|CodeSystem)[ >]' \
    | head -1 | tr -d '<> ' || true)"
  if [ -z "$tipo" ]; then
    echo "  . ignorado (nao e artefato de conformidade): $rel"
    continue
  fi

  url="$(head -c 8000 "$f" \
    | grep -oE '<url[ ]+value="[^"]+"' | head -1 \
    | sed -E 's/.*value="([^"]+)".*/\1/' || true)"
  [ -n "$url" ] || url="(sem url) $rel"
  printf '%s\t%s\n' "$url" "$rel" >> "$TMP/urls"

  mkdir -p "$DESTINO/$(dirname "$rel")"
  cp "$f" "$DESTINO/$rel"
  printf '%s\t%s\n' "$tipo" "$rel" >> "$TMP/lista"
  aceitos=$((aceitos+1))
done < <(find "$ORIGEM" -type f -name '*.xml' -print0 | sort -z)

dups="$(cut -f1 "$TMP/urls" | sort | uniq -d || true)"
if [ -n "$dups" ]; then
  echo
  echo "ERRO: URL canonica duplicada entre arquivos. Decida qual versao vale"
  echo "      e remova a outra da origem antes de prosseguir:"
  while IFS= read -r u; do
    echo "  $u"
    awk -F'\t' -v u="$u" '$1==u {print "      -> " $2}' "$TMP/urls"
  done <<< "$dups"
  exit 2
fi

{
  echo "# Indice do pacote de conformidade da RNDS"
  echo "# Gerado por scripts/copiar-conformance.sh em $(date -Iseconds)"
  echo "# Origem: $ORIGEM"
  cut -f2 "$TMP/lista" | sort
} > "$DESTINO/index.txt"

echo
echo "================ RESUMO ================"
printf "  XMLs encontrados na origem : %d\n" "$total"
printf "  artefatos de conformidade  : %d\n" "$aceitos"
echo "  ----------------------------------"
for t in StructureDefinition ValueSet CodeSystem; do
  n="$(awk -F'\t' -v t="$t" '$1==t' "$TMP/lista" | wc -l | tr -d ' ')"
  printf "  %-20s : %s\n" "$t" "$n"
done
echo "========================================"
echo "indice: $DESTINO/index.txt"
