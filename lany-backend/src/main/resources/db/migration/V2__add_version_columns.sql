-- =====================================================================
-- Lany : V2__add_version_columns.sql
-- Objet : verrouillage optimiste (accès concurrents)
--
-- DOC : VERROUILLAGE OPTIMISTE
-- Quoi     : une colonne "version" incrémentée à chaque modification d'une ligne.
-- Pourquoi : si Hery et Voahangy ouvrent la même donnée, puis enregistrent
--            chacun leur version, la seconde écriture écraserait la première
--            sans prévenir. Sur des données financières, c'est inacceptable.
-- Comment  : 1) la lecture renvoie la version (ex. 3) avec la donnée ;
--            2) l'écriture renvoie cette version ;
--            3) Hibernate (@Version) exécute
--               UPDATE ... SET version = 4 WHERE id = ? AND version = 3 ;
--            4) si 0 ligne est modifiée, quelqu'un est passé entre-temps :
--               l'API répond 409 (« donnée modifiée par quelqu'un d'autre »).
--            Seul Hibernate incrémente la version : une requête SQL directe
--            qui modifie la ligne sans toucher à "version" contourne le mécanisme.
--
-- Tables concernées : celles où deux utilisateurs peuvent réellement se
-- concurrencer (budget_month, financial_transaction, fixed_expense).
-- =====================================================================

ALTER TABLE budget_month          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE financial_transaction ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE fixed_expense         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN budget_month.version          IS 'Version de la ligne pour le verrouillage optimiste. Incrémentée par Hibernate à chaque modification.';
COMMENT ON COLUMN financial_transaction.version IS 'Version de la ligne pour le verrouillage optimiste. Incrémentée par Hibernate à chaque modification.';
COMMENT ON COLUMN fixed_expense.version         IS 'Version de la ligne pour le verrouillage optimiste. Incrémentée par Hibernate à chaque modification.';
