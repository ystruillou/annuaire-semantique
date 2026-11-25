# Annuaire sémantique

Démonstrateur personnel : un annuaire d'entreprises dont la structure des fiches est décrite en RDF et peut être modifiée par ses administrateurs, sans intervention d'un développeur.

Il réunit dans une petite application les techniques d'une plateforme de données sémantiques : base RDF interrogée en SPARQL, validation SHACL, index Elasticsearch, API REST et GraphQL, widgets intégrables dans un site tiers, connecteur vers une source externe.

Démonstration en ligne : https://directory.tikoad-services.info (interface d'administration) et https://directory.tikoad-services.info/demo.html (les widgets dans la page d'un faux site partenaire). La consultation est libre ; les modifications demandent un jeton.

Ce n'est pas un produit. Les sociétés des données d'exemple sont fictives.

## Ce qu'il fait

- **Domaines et champs modifiables.** Un domaine d'intérêt (« Nautisme ») porte ses propres champs (« Activité », « Effectif »). Un administrateur en ajoute un par un appel d'API : quelques triplets de plus dans la base, pas de table à modifier ni de redéploiement.
- **Validation par l'ontologie.** Chaque domaine est une forme SHACL. Une fiche est validée contre les formes de ses domaines avant d'être enregistrée : la règle vient des données, pas du code.
- **Recherche.** Plein texte en français, filtres exacts et décompte par facette. Un champ ajouté par un administrateur devient aussitôt cherchable et, s'il l'a demandé, filtrable.
- **Deux API sur les mêmes services.** REST pour lire et écrire, GraphQL en lecture.
- **Widgets.** Annuaire ou carte, configurés dans l'application, posés sur un autre site par une balise. Le widget ne reçoit que les champs que l'administrateur a choisi de publier, et seulement sur les sites qu'il a autorisés.
- **Interface d'administration** en Angular : domaines et champs, fiches (le formulaire est construit à partir des champs définis dans l'ontologie), widgets.
- **Source externe.** Préremplissage d'une fiche à partir du répertoire Sirene, par l'API publique « Recherche d'entreprises ».

## Architecture

```
 site partenaire            navigateur de l'administrateur
  <annuaire-widget>                    |
        |                              |
        v                              v
  /widget/{id}/donnees     /api (REST)     /graphql
        \________________________|____________/
                                 |
                      application Spring Boot
            ____________________|_____________________
           |                    |                     |
     base RDF (SPARQL)    Elasticsearch          PostgreSQL
     Jena TDB2 / Fuseki   index de recherche     configuration
     ontologies, fiches   (copie reconstructible) des widgets
```

| Sujet | Où regarder |
|---|---|
| Ontologies en SHACL, ajout d'un champ | `src/main/java/fr/annuaire/semantique/Ontologies.java` |
| Fiches en RDF, validation SHACL | `src/main/java/fr/annuaire/semantique/Entites.java` |
| Accès SPARQL, base locale ou serveur Fuseki | `src/main/java/fr/annuaire/semantique/BaseRdf.java` |
| Index, facettes, gabarit dynamique | `src/main/java/fr/annuaire/recherche/IndexRecherche.java` |
| API REST et protection des écritures | `src/main/java/fr/annuaire/api/` |
| Schéma GraphQL | `src/main/resources/graphql/schema.graphqls` |
| Interface d'administration (Angular 21) | `admin/src/app/` |
| Widget (composant web, sans dépendance) | `src/main/resources/static/widget.js` |
| Connecteur Sirene | `src/main/java/fr/annuaire/sources/ConnecteurSirene.java` |

La base RDF est la référence. L'index Elasticsearch en est une copie, reconstruite par `POST /api/index/reconstruire`.

## Lancer

### Avec Docker

```
cd deploiement
cp .env.exemple .env      # puis changer les deux secrets
docker compose up -d --build
```

Quatre services démarrent : Fuseki, Elasticsearch, PostgreSQL et l'application sur http://localhost:8096. Cette configuration est exécutée et interrogée à chaque modification par l'intégration continue (`.github/workflows/ci.yml`).

### Sans Docker

Il faut Java 21 et un Elasticsearch 9 sur `localhost:9200`. La base RDF tourne alors dans le processus (TDB2, dossier `donnees/`), et la configuration des widgets dans un fichier H2.

```
set ANNUAIRE_ADMIN_JETON=un-jeton-de-16-caracteres-au-moins
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--annuaire.exemple=true"
```

- Interface d'administration : http://localhost:8096/admin/ (à construire d'abord : `cd admin`, `npm ci`, `npx ng build`)
- Page d'un faux site partenaire avec les deux widgets : http://localhost:8096/demo.html
- Console GraphQL : http://localhost:8096/graphiql

## Essayer

Ajouter un champ au domaine, comme le ferait un administrateur :

```
curl -X POST http://localhost:8096/api/domaines/nautisme/champs ^
  -H "Authorization: Bearer %ANNUAIRE_ADMIN_JETON%" -H "Content-Type: application/json" ^
  -d "{\"libelle\":\"Label\",\"type\":\"TEXTE\",\"facette\":true}"
```

Chercher, avec un filtre de facette :

```
curl "http://localhost:8096/api/recherche?q=voilerie&domaine=nautisme&f.commune=Brest"
```

La même recherche en GraphQL :

```graphql
{
  recherche(texte: "voilerie", domaine: "nautisme") {
    total
    entites { nom commune champs { champ valeur } }
    facettes { libelle valeurs { valeur nombre } }
  }
}
```

Une valeur qui ne respecte pas le type déclaré est refusée par SHACL (réponse 422) :

```
curl -X POST http://localhost:8096/api/entites ^
  -H "Authorization: Bearer %ANNUAIRE_ADMIN_JETON%" -H "Content-Type: application/json" ^
  -d "{\"nom\":\"Test\",\"domaines\":[\"nautisme\"],\"champs\":{\"nautisme-effectif\":\"beaucoup\"}}"
```

## Sécurité

- Les lectures sont ouvertes. Toute écriture exige l'en-tête `Authorization: Bearer <jeton>` ; sans jeton configuré, elles sont toutes refusées.
- Les requêtes SPARQL sont paramétrées : une valeur saisie ne peut pas modifier la requête (un test le vérifie).
- Le widget insère les données comme du texte, jamais comme du HTML, et charge la bibliothèque de cartes avec un contrôle d'intégrité.
- Fuseki, Elasticsearch et PostgreSQL ne sont pas exposés hors du réseau Docker.

## Limites connues

- Pas de comptes ni de droits par domaine : un seul jeton d'administration.
- Les champs de domaine ont trois types (texte, nombre, oui/non) et une seule valeur.
- En mode Fuseki, chaque opération ouvre une connexion HTTP : le chargement en masse est lent.
- Les tests automatisés couvrent la couche sémantique ; le reste est vérifié par l'intégration continue, sur les services réels.

## Technique

Java 21, Spring Boot 4, Angular 21, Apache Jena 6 (TDB2, Fuseki, SHACL), Elasticsearch 9, PostgreSQL 18, Flyway, Spring GraphQL. Licence MIT.
