#!/usr/bin/env bash
# Acrescenta as chaves do RAC as tabelas COMPARTILHADAS com outros servicos, sem
# sobrescrever nada. Tabela compartilhada se mescla, nunca se reescreve.
set -euo pipefail
D=rnds-br-connect-core/src/test/resources/mapeamentos
acrescentar() {
  local arq="$D/$1"
  [ -f "$arq" ] || { echo "  !! $1 nao existe -- esperado de servico anterior"; return; }
  if grep -q "^$2 *=" "$arq"; then echo "  .  $1: $2 ja existe"; return; fi
  [ -n "$(tail -c1 "$arq")" ] && echo >> "$arq"
  echo "$2 = $3" >> "$arq"; echo "  +  $1: $2"
}
acrescentar carateres-atendimento-rnds.properties ELETIVO        "01|Eletivo"
acrescentar procedencias.properties               SEM_INFORMACAO "99|Informação ausente no modelo de origem"
acrescentar motivos-desfecho.properties           OBITO          "06|Óbito"
acrescentar financiamentos.properties             FEDERAL_SUS    "01|Federal SUS"
acrescentar cbos.properties                       ENFERMEIRO     "223505|ENFERMEIRO"
acrescentar cbos.properties                       FISIOTERAPEUTA "223605|FISIOTERAPEUTA GERAL"
acrescentar procedimentos.properties              CONSULTA_NS_AE "0301010048|CONSULTA DE PROFISSIONAIS DE NIVEL SUPERIOR NA ATENÇÃO ESPECIALIZADA (EXCETO MÉDICO)"
acrescentar cids.properties                       VARIZES_ESOFAGO "I859|Varizes esofagianas sem sangramento"

# O alergenos.properties foi migrado para alergenos.yml: o BRAlergenos compoe tres
# CodeSystems, e o formato properties so carrega um. A entrada do SA esta preservada.
if [ -f "$D/alergenos.properties" ] && [ -f "$D/alergenos.yml" ]; then
  if grep -q "DIPIRONA_SOLUCAO" "$D/alergenos.yml"; then
    rm "$D/alergenos.properties"
    echo "  -  alergenos.properties removido (migrado para alergenos.yml)"
  else
    echo "  !! alergenos.yml nao tem a entrada do SA -- migracao NAO aplicada"
  fi
fi
