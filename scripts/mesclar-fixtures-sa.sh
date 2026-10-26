#!/usr/bin/env bash
# Acrescenta as chaves do SA as tabelas COMPARTILHADAS com outros servicos, sem
# sobrescrever nada. Tabela compartilhada se mescla, nunca se reescreve: foi
# reescrevendo que o vias-administracao.properties do RIA quase ganhou um codigo
# intramuscular errado (10905 no lugar de 10890).
set -euo pipefail
D=rnds-br-connect-core/src/test/resources/mapeamentos
acrescentar() {
  local arq="$D/$1"
  [ -f "$arq" ] || { echo "  !! $1 nao existe -- esperado de servico anterior"; return; }
  if grep -q "^$2 *=" "$arq"; then echo "  .  $1: $2 ja existe"; return; fi
  [ -n "$(tail -c1 "$arq")" ] && echo >> "$arq"
  echo "$2 = $3" >> "$arq"; echo "  +  $1: $2"
}
acrescentar modalidades-assistenciais.properties HOSPITALAR    "04|Atenção Hospitalar"
acrescentar procedimentos.properties             TC_TORAX      "0206020040|TOMOGRAFIA DE HEMITORAX, PULMÃO OU DO MEDIASTINO"
acrescentar cids.properties                      PNEUMONIA_VSR "J121|Pneumonia devida a vírus respiratório sincicial"
acrescentar cids.properties                      COVID_NE      "B342|Infecção por coronavírus de localização não especificada"
acrescentar formas-farmaceuticas.properties      COMPRIMIDO    "19|Comprimido"
