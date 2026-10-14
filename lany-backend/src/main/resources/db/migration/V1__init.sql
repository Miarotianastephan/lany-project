-- =====================================================================
-- Lany : V1__init.sql  (migration Flyway initiale)
-- SGBD cible : PostgreSQL 15+ (requis pour SET NULL (colonne))
--
-- Conventions
--   * Clés primaires : BIGINT GENERATED ALWAYS AS IDENTITY
--   * Montants       : NUMERIC(15,2), toujours positifs ; c'est "type" qui donne le sens
--   * Instants       : TIMESTAMPTZ (stockés en UTC, convertis en Indian/Antananarivo à l'usage)
--   * Énumérations   : VARCHAR + CHECK (plus simple à faire évoluer qu'un ENUM natif)
--   * updated_at     : maintenu par l'application (Hibernate), pas par un trigger
--
-- ---------------------------------------------------------------------
-- DOC : CLÉS ÉTRANGÈRES COMPOSITES
-- Quoi     : une clé étrangère qui porte sur DEUX colonnes (id, household_id)
--            au lieu d'une seule (id).
-- Pourquoi : avec une FK simple, rien n'empêche une transaction du foyer 1
--            de référencer le membre du foyer 2. La base elle-même doit
--            interdire ce croisement entre foyers (données financières).
-- Comment  : 1) on pose UNIQUE (id, household_id) sur la table référencée
--               (member, category, fixed_expense) ;
--            2) la table enfant déclare
--               FOREIGN KEY (member_id, household_id)
--               REFERENCES member (id, household_id).
--            Le couple doit exister tel quel : le membre doit appartenir
--            au MÊME foyer que la transaction.
--            Piège ON DELETE SET NULL : sans précision, PostgreSQL mettrait
--            aussi household_id à NULL (colonne NOT NULL = erreur). On écrit
--            donc ON DELETE SET NULL (category_id) pour ne vider que la
--            colonne voulue. Si la colonne enfant est NULL, la FK n'est pas
--            vérifiée (MATCH SIMPLE par défaut) : cas des liens optionnels.
-- ---------------------------------------------------------------------
-- DOC : PÉRIODE COUVERTE (covered_from / covered_to)
-- Quoi     : la plage de dates qu'une dépense fixe RÉALISÉE couvre.
-- Pourquoi : la simulation projette les dépenses fixes à venir. Pour ne pas
--            les compter deux fois, elle doit savoir quelles périodes sont
--            déjà payées. Différent de occurred_at (quand l'argent a bougé).
-- Comment  : renseignées si et seulement si nature = 'fixed'.
--              daily   -> un jour              (from = to)
--              weekly  -> lundi à dimanche     (semaine ISO, rattachée au
--                         mois de son lundi)
--              monthly -> 1er au dernier jour du mois
--            Une contrainte EXCLUDE interdit deux réalisations de la même
--            règle dont les plages se chevauchent.
-- =====================================================================

-- Requise par la contrainte EXCLUDE (égalité sur BIGINT + chevauchement de dates)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- app_user : compte de connexion
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Email unique sans tenir compte de la casse
CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email));

COMMENT ON TABLE  app_user                IS 'Compte de connexion à Lany. Un utilisateur peut appartenir à plusieurs foyers via member.';
COMMENT ON COLUMN app_user.id             IS 'Identifiant technique.';
COMMENT ON COLUMN app_user.email          IS 'Email de connexion, unique sans tenir compte de la casse.';
COMMENT ON COLUMN app_user.password_hash  IS 'Mot de passe haché (jamais en clair).';
COMMENT ON COLUMN app_user.display_name   IS 'Nom affiché dans l''application.';
COMMENT ON COLUMN app_user.is_active      IS 'FALSE = compte désactivé (connexion refusée), sans suppression.';
COMMENT ON COLUMN app_user.created_at     IS 'Date de création du compte (UTC).';

-- ---------------------------------------------------------------------
-- household : foyer
-- ---------------------------------------------------------------------
CREATE TABLE household (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                  VARCHAR(100)  NOT NULL,
    days_per_week         SMALLINT      NOT NULL DEFAULT 5 CHECK (days_per_week BETWEEN 1 AND 7),
    weeks_per_month       SMALLINT      NOT NULL DEFAULT 4 CHECK (weeks_per_month BETWEEN 1 AND 5),
    timezone              VARCHAR(50)   NOT NULL DEFAULT 'Indian/Antananarivo',
    currency_code         CHAR(3)       NOT NULL DEFAULT 'MGA',
    low_balance_threshold NUMERIC(15,2) CHECK (low_balance_threshold >= 0),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE  household                       IS 'Foyer familial. Toutes les données métier sont rattachées à un foyer (multi-foyers).';
COMMENT ON COLUMN household.id                    IS 'Identifiant technique.';
COMMENT ON COLUMN household.name                  IS 'Nom du foyer.';
COMMENT ON COLUMN household.days_per_week         IS 'Jours budgétaires par semaine (défaut 5 = lundi à vendredi). Sert à l''équivalence budgétaire ET à définir les jours projetés des règles journalières. Modifier cette valeur n''altère aucun passé.';
COMMENT ON COLUMN household.weeks_per_month       IS 'Semaines par mois (défaut 4). Sert UNIQUEMENT à l''équivalence budgétaire (ex. 2 000 Ar/jour = 40 000 Ar/mois). La projection utilise le calendrier réel.';
COMMENT ON COLUMN household.timezone             IS 'Fuseau IANA du foyer, utilisé pour déterminer le jour métier d''un instant.';
COMMENT ON COLUMN household.currency_code         IS 'Code devise ISO 4217 (MGA = Ariary).';
COMMENT ON COLUMN household.low_balance_threshold IS 'Seuil sous lequel le reste du mois déclenche une alerte. NULL = pas d''alerte.';
COMMENT ON COLUMN household.created_at            IS 'Date de création du foyer (UTC).';

-- ---------------------------------------------------------------------
-- member : personne suivie dans un foyer (avec ou sans compte)
-- ---------------------------------------------------------------------
CREATE TABLE member (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id BIGINT       NOT NULL REFERENCES household (id) ON DELETE RESTRICT,
    user_id      BIGINT       REFERENCES app_user (id) ON DELETE RESTRICT,
    display_name VARCHAR(100) NOT NULL,
    family_role  VARCHAR(50),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    access_level VARCHAR(10)  CHECK (access_level IN ('owner', 'write', 'read')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- access_level renseigné si et seulement si le membre a un compte
    CONSTRAINT chk_member_access_requires_user
        CHECK ((user_id IS NULL) = (access_level IS NULL)),
    -- Un utilisateur n'apparaît qu'une fois par foyer (plusieurs user_id NULL autorisés)
    CONSTRAINT uq_member_household_user UNIQUE (household_id, user_id),
    -- Cible des clés étrangères composites (voir DOC en tête de fichier)
    CONSTRAINT uq_member_id_household UNIQUE (id, household_id)
);

-- "Au moins un owner par foyer" est garanti par le Service Java (règle métier, en transaction).

COMMENT ON TABLE  member              IS 'Personne suivie dans un foyer. Avec compte (user_id) elle peut se connecter ; sans compte (ex. enfant) elle n''est qu''une personne à qui attribuer des dépenses.';
COMMENT ON COLUMN member.id           IS 'Identifiant technique.';
COMMENT ON COLUMN member.household_id IS 'Foyer d''appartenance.';
COMMENT ON COLUMN member.user_id      IS 'Compte de connexion lié. NULL = membre sans compte.';
COMMENT ON COLUMN member.display_name IS 'Nom affiché du membre dans le foyer.';
COMMENT ON COLUMN member.family_role  IS 'Rôle familial en libellé libre (père, mère, fils...). Purement informatif.';
COMMENT ON COLUMN member.is_active    IS 'FALSE = membre parti ou inactif. Un membre n''est jamais supprimé (historique).';
COMMENT ON COLUMN member.access_level IS 'owner (écriture + gestion membres/réglages), write (écriture), read (lecture seule). NULL si pas de compte.';
COMMENT ON COLUMN member.created_at   IS 'Date d''ajout au foyer (UTC).';

-- "Mes foyers" à la connexion
CREATE INDEX idx_member_user ON member (user_id);

-- ---------------------------------------------------------------------
-- category : classement des dépenses et entrées, par foyer
-- ---------------------------------------------------------------------
CREATE TABLE category (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id BIGINT      NOT NULL REFERENCES household (id) ON DELETE RESTRICT,
    name         VARCHAR(60) NOT NULL,
    kind         VARCHAR(10) NOT NULL CHECK (kind IN ('expense', 'income')),
    archived_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_category_id_household UNIQUE (id, household_id)
);

-- Nom unique parmi les catégories NON archivées : on peut réutiliser le nom d'une archivée
CREATE UNIQUE INDEX uq_category_active_name
    ON category (household_id, kind, lower(name))
    WHERE archived_at IS NULL;

COMMENT ON TABLE  category              IS 'Catégorie de dépense ou d''entrée propre à un foyer. Archivée plutôt que supprimée.';
COMMENT ON COLUMN category.id           IS 'Identifiant technique.';
COMMENT ON COLUMN category.household_id IS 'Foyer propriétaire de la catégorie.';
COMMENT ON COLUMN category.name         IS 'Nom de la catégorie (alimentation, transport...).';
COMMENT ON COLUMN category.kind         IS 'expense (dépense) ou income (entrée d''argent).';
COMMENT ON COLUMN category.archived_at  IS 'Date d''archivage. NULL = catégorie active.';
COMMENT ON COLUMN category.created_at   IS 'Date de création (UTC).';

-- ---------------------------------------------------------------------
-- budget_month : budget d'un mois et report
-- ---------------------------------------------------------------------
CREATE TABLE budget_month (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id                        BIGINT        NOT NULL REFERENCES household (id) ON DELETE RESTRICT,
    month                               DATE          NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    starting_amount                     NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (starting_amount >= 0),
    carried_over_amount                 NUMERIC(15,2) NOT NULL DEFAULT 0,  -- négatif autorisé (déficit)
    carry_over_computed_at_confirmation NUMERIC(15,2),
    carry_over_confirmed_at             TIMESTAMPTZ,
    projected_fixed_total               NUMERIC(15,2) CHECK (projected_fixed_total >= 0),
    closed_at                           TIMESTAMPTZ,
    created_at                          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_budget_month_household_month UNIQUE (household_id, month),
    -- Pas de confirmation sans calcul figé, ni l'inverse
    CONSTRAINT chk_budget_month_carry_pair
        CHECK ((carry_over_confirmed_at IS NULL) = (carry_over_computed_at_confirmation IS NULL))
);

COMMENT ON TABLE  budget_month                     IS 'Budget d''un mois pour un foyer : argent frais, report du mois précédent, snapshot du prévu.';
COMMENT ON COLUMN budget_month.id                  IS 'Identifiant technique.';
COMMENT ON COLUMN budget_month.household_id        IS 'Foyer concerné.';
COMMENT ON COLUMN budget_month.month               IS 'Mois concerné, représenté par son 1er jour (ex. 2026-11-01). Unique par foyer.';
COMMENT ON COLUMN budget_month.starting_amount     IS 'Argent frais saisi pour le mois (salaire, revenus de départ). Hors report.';
COMMENT ON COLUMN budget_month.carried_over_amount IS 'Report confirmé du mois précédent, figé. Peut être négatif (déficit reporté tel quel). Peut avoir été ajusté à la main.';
COMMENT ON COLUMN budget_month.carry_over_computed_at_confirmation IS 'Calcul brut du report AU MOMENT de la confirmation. Comparé au calcul actuel pour détecter un écart si le mois précédent est modifié ensuite.';
COMMENT ON COLUMN budget_month.carry_over_confirmed_at IS 'Date de confirmation du report. NULL = report encore en attente de confirmation.';
COMMENT ON COLUMN budget_month.projected_fixed_total IS 'Total des dépenses fixes projetées (calendrier réel), figé à la clôture. Source du « prévu vs réel » des mois passés.';
COMMENT ON COLUMN budget_month.closed_at           IS 'Date de clôture du mois. Prévu pour plus tard, inutilisé au MVP.';
COMMENT ON COLUMN budget_month.created_at          IS 'Date de création (UTC).';
COMMENT ON COLUMN budget_month.updated_at          IS 'Dernière modification (UTC), maintenue par l''application.';

-- ---------------------------------------------------------------------
-- fixed_expense : règle récurrente (jamais un mouvement d'argent)
-- ---------------------------------------------------------------------
CREATE TABLE fixed_expense (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id BIGINT        NOT NULL REFERENCES household (id) ON DELETE RESTRICT,
    label        VARCHAR(120)  NOT NULL,
    amount       NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    frequency    VARCHAR(10)   NOT NULL CHECK (frequency IN ('daily', 'weekly', 'monthly')),
    category_id  BIGINT,
    is_active    BOOLEAN       NOT NULL DEFAULT TRUE,
    archived_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_fixed_expense_id_household UNIQUE (id, household_id),
    CONSTRAINT fk_fixed_expense_category
        FOREIGN KEY (category_id, household_id)
        REFERENCES category (id, household_id)
        ON DELETE SET NULL (category_id)
);

CREATE INDEX idx_fixed_expense_household_visible
    ON fixed_expense (household_id)
    WHERE archived_at IS NULL;

COMMENT ON TABLE  fixed_expense              IS 'Règle de dépense fixe, modifiable (pas de versionnage). Projetée par défaut ; réalisée manuellement en créant une transaction liée.';
COMMENT ON COLUMN fixed_expense.id           IS 'Identifiant technique.';
COMMENT ON COLUMN fixed_expense.household_id IS 'Foyer propriétaire de la règle.';
COMMENT ON COLUMN fixed_expense.label        IS 'Libellé (Loyer, Riz, Internet...).';
COMMENT ON COLUMN fixed_expense.amount       IS 'Montant par période de fréquence (ex. 2 000 si daily). Strictement positif.';
COMMENT ON COLUMN fixed_expense.frequency    IS 'daily, weekly ou monthly : unité à laquelle le montant s''applique.';
COMMENT ON COLUMN fixed_expense.category_id  IS 'Catégorie optionnelle. Mise à NULL si la catégorie est supprimée.';
COMMENT ON COLUMN fixed_expense.is_active    IS 'FALSE = règle en pause (ignorée par la projection), réactivable.';
COMMENT ON COLUMN fixed_expense.archived_at  IS 'Date d''archivage : la règle disparaît de l''interface sans casser les liens. NULL = visible.';
COMMENT ON COLUMN fixed_expense.created_at   IS 'Création de la règle (UTC). Sert de début implicite : pas d''alerte « non enregistrée » avant cette date.';
COMMENT ON COLUMN fixed_expense.updated_at   IS 'Dernière modification (UTC), maintenue par l''application.';

-- ---------------------------------------------------------------------
-- financial_transaction : mouvement réel d'argent
-- ---------------------------------------------------------------------
CREATE TABLE financial_transaction (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id     BIGINT        NOT NULL REFERENCES household (id) ON DELETE RESTRICT,
    member_id        BIGINT        NOT NULL,
    type             VARCHAR(10)   NOT NULL CHECK (type IN ('expense', 'income')),
    nature           VARCHAR(10)   NOT NULL CHECK (nature IN ('fixed', 'variable')),
    amount           NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    occurred_at      TIMESTAMPTZ   NOT NULL,
    description      VARCHAR(255),
    category_id      BIGINT,
    fixed_expense_id BIGINT,
    covered_from     DATE,
    covered_to       DATE,
    created_by       BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Clés étrangères composites : interdisent tout croisement entre foyers
    CONSTRAINT fk_ft_member
        FOREIGN KEY (member_id, household_id)
        REFERENCES member (id, household_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ft_category
        FOREIGN KEY (category_id, household_id)
        REFERENCES category (id, household_id)
        ON DELETE SET NULL (category_id),
    CONSTRAINT fk_ft_fixed_expense
        FOREIGN KEY (fixed_expense_id, household_id)
        REFERENCES fixed_expense (id, household_id)
        ON DELETE SET NULL (fixed_expense_id),

    -- Période couverte : renseignée si et seulement si nature = 'fixed', et cohérente
    CONSTRAINT chk_ft_coverage CHECK (
        (nature = 'fixed') = (covered_from IS NOT NULL)
        AND (covered_from IS NULL) = (covered_to IS NULL)
        AND (covered_from IS NULL OR covered_from <= covered_to)
    ),
    -- Une entrée d'argent n'est jamais une dépense fixe
    CONSTRAINT chk_ft_fixed_is_expense CHECK (nature <> 'fixed' OR type = 'expense'),
    -- Le lien vers une règle n'a de sens que pour une transaction fixe
    -- (fixed_expense_id peut être NULL pour une fixe dont la règle a été supprimée)
    CONSTRAINT chk_ft_rule_link CHECK (fixed_expense_id IS NULL OR nature = 'fixed'),

    -- Anti-double-comptage : une même règle ne peut pas être réalisée deux fois
    -- sur des périodes qui se chevauchent ('[]' = bornes incluses)
    CONSTRAINT excl_ft_no_overlapping_coverage
        EXCLUDE USING gist (
            fixed_expense_id WITH =,
            daterange(covered_from, covered_to, '[]') WITH &&
        )
        WHERE (fixed_expense_id IS NOT NULL)
);

-- Flux d'un jour / semaine / mois, évolution du Dashboard (filtre sur un intervalle d'instants)
CREATE INDEX idx_ft_household_occurred
    ON financial_transaction (household_id, occurred_at);

-- Filtre « dépenses de tel membre »
CREATE INDEX idx_ft_household_member_occurred
    ON financial_transaction (household_id, member_id, occurred_at);

COMMENT ON TABLE  financial_transaction                  IS 'Mouvement réel d''argent (dépense ou entrée). Source de vérité du reste réel.';
COMMENT ON COLUMN financial_transaction.id               IS 'Identifiant technique.';
COMMENT ON COLUMN financial_transaction.household_id     IS 'Foyer concerné. Fait partie des clés étrangères composites.';
COMMENT ON COLUMN financial_transaction.member_id        IS 'Membre à qui la dépense/entrée est attribuée (qui a dépensé). Peut ne pas avoir de compte.';
COMMENT ON COLUMN financial_transaction.type             IS 'expense (sortie) ou income (entrée). Donne le sens du montant.';
COMMENT ON COLUMN financial_transaction.nature           IS 'fixed (réalisation d''une dépense fixe) ou variable (imprévue/occasionnelle).';
COMMENT ON COLUMN financial_transaction.amount           IS 'Montant strictement positif ; le signe est porté par type.';
COMMENT ON COLUMN financial_transaction.occurred_at      IS 'Instant où l''argent a bougé (UTC). Le jour métier se dérive avec le fuseau du foyer.';
COMMENT ON COLUMN financial_transaction.description      IS 'Description libre (ex. Paquet de cigarettes).';
COMMENT ON COLUMN financial_transaction.category_id      IS 'Catégorie optionnelle. Mise à NULL si la catégorie est supprimée.';
COMMENT ON COLUMN financial_transaction.fixed_expense_id IS 'Règle fixe dont la transaction est issue. NULL si variable, ou si la règle a été supprimée (la transaction survit).';
COMMENT ON COLUMN financial_transaction.covered_from     IS 'Début de la période de projection couverte (nature = fixed uniquement). Voir DOC en tête de fichier.';
COMMENT ON COLUMN financial_transaction.covered_to       IS 'Fin (incluse) de la période couverte. daily : = covered_from ; weekly : dimanche ; monthly : dernier jour du mois.';
COMMENT ON COLUMN financial_transaction.created_by       IS 'Compte qui a SAISI la ligne (audit). Distinct de member_id (qui a dépensé).';
COMMENT ON COLUMN financial_transaction.created_at       IS 'Date de saisie (UTC).';
COMMENT ON COLUMN financial_transaction.updated_at       IS 'Dernière modification (UTC), maintenue par l''application.';
