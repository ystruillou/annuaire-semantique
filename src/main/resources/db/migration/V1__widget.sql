-- Configuration des widgets. Les données métier (entités, ontologies) sont dans la base RDF.
create table widget (
    id        uuid          primary key,
    nom       varchar(80)   not null,
    type      varchar(20)   not null,
    domaine   varchar(80)   not null,
    champs    varchar(1000) not null default '',
    origines  varchar(1000) not null default '*',
    cree_le   timestamp with time zone not null
);
