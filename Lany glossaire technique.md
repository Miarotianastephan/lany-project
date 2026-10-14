# Lany : glossaire technique

Référence courte des termes utilisés dans le projet, regroupés par thème.

## 1. Base de données (PostgreSQL)

| Terme | Signification |
| --- | --- |
| **SGBD** | Système de gestion de base de données (ici PostgreSQL). |
| **Table / colonne / ligne** | Structure de stockage / un champ / un enregistrement. |
| **Clé primaire (PK)** | Colonne qui identifie une ligne de façon unique. |
| **Identity** | `GENERATED ALWAYS AS IDENTITY` : la base génère seule l'identifiant (1, 2, 3...). |
| **Clé étrangère (FK)** | Colonne qui référence la clé d'une autre table et garantit que la cible existe. |
| **Clé composite** | Clé sur plusieurs colonnes. Ici `(id, household_id)` : impose que deux lignes liées appartiennent au même foyer. |
| **`UNIQUE`** | Interdit les doublons sur une ou plusieurs colonnes. |
| **`CHECK`** | Règle de validité vérifiée par la base (ex. `amount > 0`). |
| **`EXCLUDE`** | Contrainte avancée : interdit deux lignes dont les valeurs se chevauchent (ici deux périodes couvertes). Utilise `btree_gist`. |
| **`btree_gist`** | Extension PostgreSQL qui permet d'utiliser `=` dans une contrainte `EXCLUDE`. |
| **`ON DELETE RESTRICT`** | Interdit de supprimer une ligne encore référencée. |
| **`ON DELETE SET NULL (col)`** | À la suppression de la cible, vide uniquement la colonne indiquée. PostgreSQL 15+. |
| **Index** | Structure qui accélère les recherches. PostgreSQL n'indexe pas automatiquement les clés étrangères. |
| **Index partiel** | Index limité aux lignes qui respectent une condition (`WHERE archived_at IS NULL`). |
| **Vue** | Requête enregistrée, utilisée comme une table (ex. totaux agrégés). |
| **`COMMENT ON COLUMN`** | Documentation stockée dans la base, visible dans pgAdmin ou DBeaver. |
| **ACID** | Garanties d'une transaction : tout ou rien, cohérence, isolation, durabilité. |
| **Transaction SQL** | Groupe d'opérations exécutées ensemble ou pas du tout. Ne pas confondre avec `financial_transaction`. |
| **Migration (Flyway)** | Script SQL versionné (`V1__init.sql`) appliqué dans l'ordre pour faire évoluer le schéma. |
| **Mot réservé** | Mot que le SGBD utilise lui-même (`user`) : à éviter pour nommer une table. |

## 2. Types et conventions de données

| Terme | Signification |
| --- | --- |
| **`BIGINT`** | Entier sur 64 bits (identifiants). |
| **`NUMERIC(15,2)`** | Nombre décimal exact : 15 chiffres au total, 2 après la virgule. Jamais de flottant pour l'argent. |
| **`VARCHAR(n)`** | Texte de longueur maximale n. |
| **`TIMESTAMPTZ`** | Instant précis, stocké en UTC. |
| **`DATE`** | Jour calendaire sans heure. |
| **Énumération** | Liste de valeurs autorisées. Ici `VARCHAR` + `CHECK` plutôt qu'un `ENUM` natif. |
| **Nullable / `NULL`** | Colonne qui peut être vide / absence de valeur. |
| **Archivage (`archived_at`)** | Masquer un élément sans le supprimer, pour préserver les liens et l'historique. |
| **Snapshot** | Valeur copiée et figée à un instant donné (montant d'une transaction, `projected_fixed_total`). |

## 3. Dates et fuseau horaire

| Terme | Signification |
| --- | --- |
| **UTC** | Référence horaire mondiale, utilisée pour stocker les instants. |
| **Fuseau `Indian/Antananarivo`** | Madagascar, UTC+3, sans heure d'été. |
| **Instant** | Moment précis (`occurred_at`). |
| **Jour métier** | Jour de Madagascar auquel appartient un instant. Dérivé, jamais stocké. |
| **Semaine ISO** | Semaine du lundi au dimanche. |
| **Semaine à cheval** | Semaine répartie sur deux mois ; rattachée au mois de son lundi. |
| **`AT TIME ZONE`** | Conversion d'un instant vers un fuseau en SQL (utilisée pour grouper, pas pour filtrer). |

## 4. Règles métier de Lany

| Terme | Signification |
| --- | --- |
| **Foyer (`household`)** | Groupe familial. Toutes les données lui sont rattachées (multi-foyers). |
| **Membre (`member`)** | Personne suivie dans un foyer, avec ou sans compte. |
| **Compte (`app_user`)** | Identité de connexion. Un compte peut avoir un membre dans plusieurs foyers. |
| **`owner` / `write` / `read`** | Niveaux d'accès : propriétaire (gestion + écriture), écriture, lecture seule. Définis par foyer. |
| **Dépense fixe** | Règle récurrente prévue (loyer, riz). Jamais un mouvement d'argent. |
| **Dépense variable** | Dépense imprévue ou occasionnelle. |
| **Transaction** | Mouvement réel d'argent (dépense ou entrée). |
| **Fréquence** | `daily`, `weekly`, `monthly` : unité du montant d'une règle fixe. |
| **Règle modifiable** | Une dépense fixe se modifie directement ; l'historique réel est protégé par les snapshots. |
| **Réaliser une dépense fixe** | Action manuelle qui crée la transaction correspondante. |
| **Période couverte** | `covered_from` à `covered_to` : plage de (**prévision**** ****du m****ois**) projection déjà payée par une transaction. |
| **Double comptage** | Compter deux fois la même dépense (projetée et réalisée). Évité par la période couverte. |
| **Jours budgétaires** | Les `days_per_week` premiers jours de la semaine (lundi à vendredi par défaut). |
| **Équivalence budgétaire** | Conversion avec 5 jours × 4 semaines (2 000 Ar/jour = 10 000/semaine = 40 000/mois). Affichée sur la page Dépenses fixes. |
| **Projection (« prévision du mois »)** | Dépenses fixes restantes calculées avec le calendrier réel, d'aujourd'hui à la fin du mois. |
| **Reste réel** | Départ + report + entrées − dépenses, transactions réelles seulement. Chiffre principal. |
| **Reste simulé** | Reste réel − fixes projetées non réalisées. |
| **Argent de départ** | Argent frais saisi pour le mois (`starting_amount`). |
| **Report** | Reste du mois précédent, proposé puis confirmé et figé. Un déficit est reporté tel quel. |
| **Écart de report** | Différence entre le report figé et le calcul actuel, signalée si le mois précédent est modifié. |
| **Clôture de mois** | Verrouillage d'un mois. Prévue dans la structure, hors MVP. |
| **`created_by` / `member_id`** | Qui a saisi la ligne / qui a dépensé. |
| **MVP** | Version minimale utilisable de l'application. |

## 5. Java et programmation orientée objet

| Terme | Signification |
| --- | --- |
| **POO** | Programmation orientée objet : organiser le code en objets qui portent données et comportements. |
| **Encapsulation** | Cacher l'état interne d'un objet (principalement les attributs) et n'exposer que des méthodes contrôlées. |
| **Abstraction** | Exposer ce qu'un objet fait sans montrer comment. |
| **Polymorphisme** | Même appel, comportement différent selon le type (ex. chaque `Frequency` calcule à sa façon). |
| **SOLID** | Cinq principes de conception : responsabilité unique, ouvert/fermé, substitution de Liskov, ségrégation des interfaces, inversion des dépendances. |
| **Objet-valeur (`Money`)** | Petit objet immuable défini par sa valeur, qui centralise une règle (ici l'arrondi). |
| **`BigDecimal`** | Type Java pour les nombres décimaux exacts. |
| **`Instant` / `ZoneId` / `Clock`** | Instant UTC / fuseau / source du temps injectable pour tester « nous sommes le 9 octobre ». |
| **`enum`** | Ensemble fixe de valeurs, pouvant porter des méthodes. |
| **JUnit** | Framework de tests unitaires Java. |

## 6. Spring Boot et architecture

| Terme | Signification |
| --- | --- |
| **Spring Boot** | Framework Java pour construire le backend avec peu de configuration. |
| **Controller** | Reçoit les requêtes HTTP et renvoie les réponses. Aucune logique métier. |
| **Service** | Contient la logique métier et les transactions. |
| **Repository** | Accès à la base de données. |
| **Entity** | Classe Java qui correspond à une table. |
| **DTO** | Objet de transfert : forme des données échangées avec le frontend, distincte de l'Entity. |
| **Séparation des couches** | Chaque couche a une seule responsabilité et ne connaît que la couche inférieure. |
| **JPA / Hibernate** | Standard et implémentation qui relient les Entities aux tables. |
| **Spring Data JPA** | Génère les Repositories à partir d'interfaces. |
| **`@Transactional`** | Exécute une méthode dans une transaction SQL. |
| **Bean Validation** | Validation par annotations côté Java, en plus des `CHECK` SQL. |
| **Spring Security** | Authentification et autorisation (dont l'isolation par foyer). |
| **API REST** | Communication HTTP entre Next.js et Spring Boot, en JSON. |
| **Sérialisation JSON** | Conversion d'objets Java en JSON. Les montants demandent une attention particulière. |
| **Row Level Security (RLS)** | Isolation par ligne gérée par PostgreSQL. Optionnelle ici. |

## 7. Frontend (Next.js)

| Terme | Signification |
| --- | --- |
| **Next.js / App Router** | Framework React ; routage par dossiers. |
| **Server Component / Client Component** | Composant rendu côté serveur / composant interactif dans le navigateur. |
| **Server Action / Route Handler** | Fonction serveur appelée par un formulaire / point d'entrée HTTP dans Next.js. Ici intermédiaires vers l'API Spring. |
| **Tailwind CSS** | Styles par classes utilitaires. |
| **shadcn/ui / Radix UI** | Composants copiés dans le projet / primitives accessibles sur lesquelles ils s'appuient. |
| **Dashboard** | Vue synthétique de la situation financière. |