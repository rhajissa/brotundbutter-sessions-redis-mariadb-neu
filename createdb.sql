drop table if exists BIB_Ausleihe;
drop table if exists BIB_Mitglied;
drop table if exists BIB_Buchexemplar;
drop table if exists BIB_Buch;
drop table if exists BIB_Autorin;
drop table if exists BIB_Autorin_Buch;

create table BIB_Buch (
  id int primary key auto_increment,
  titel text not null
);

create table BIB_Buchexemplar(
  id int primary key auto_increment,
  code int not null unique,
  buch_id int not null,

  constraint BIB_fk_Buchexemplar_Buch
  foreign key (buch_id)
  references BIB_Buch(id)
  on delete restrict
);

create table BIB_Autorin (
  id int primary key auto_increment,
  name text not null
);

create table BIB_Autorin_Buch(
  autorin_id int not null,
  buch_id int not null
);

create table BIB_Mitglied (
  id int primary key auto_increment,
  name varchar(255) not null,
  code varchar(100) not null unique
);

create table BIB_Ausleihe (
  id int primary key auto_increment,
  mitglied_id int not null,
  buchexemplar_id int not null,
  ausleihdatum timestamp default current_timestamp,
  rueckgabe_frist timestamp not null,
  tatsaechliche_rueckgabe timestamp null default null,
  status varchar(50) default 'ausgeliehen',

  constraint BIB_fk_Ausleihe_Mitglied
  foreign key (mitglied_id)
  references BIB_Mitglied(id)
  on delete restrict,

  constraint BIB_fk_Ausleihe_Exemplar
  foreign key (buchexemplar_id)
  references BIB_Buchexemplar(id)
  on delete restrict
);

-- Beispieldaten
insert into BIB_Autorin(name) values ("Kleppmann");
set @autorin_id = LAST_INSERT_ID();
insert into BIB_Buch (titel) values ("Designing Data Intensive Applications");
set @buch_id = LAST_INSERT_ID();
insert into BIB_Autorin_Buch values(@autorin_id,@buch_id);
insert into BIB_Buchexemplar (code,buch_id) values (100,@buch_id);
insert into BIB_Buchexemplar (code,buch_id) values (101,@buch_id);

-- Testdaten für Mitglieder
insert into BIB_Mitglied (name, code) values 
('Max Mustermann', 'M10001'),
('Erika Musterfrau', 'M10002'),
('John Doe', 'M10003');

-- Testdaten für Ausleihen
-- Max Mustermann hat Exemplar 100 ausgeliehen (überfällig)
insert into BIB_Ausleihe (mitglied_id, buchexemplar_id, ausleihdatum, rueckgabe_frist)
select m.id, be.id, date_sub(now(), interval 30 day), date_sub(now(), interval 16 day)
from BIB_Mitglied m, BIB_Buchexemplar be
where m.code = 'M10001' and be.code = 100;

-- Erika Musterfrau hat Exemplar 101 ausgeliehen (noch aktiv)
insert into BIB_Ausleihe (mitglied_id, buchexemplar_id, ausleihdatum, rueckgabe_frist)
select m.id, be.id, date_sub(now(), interval 5 day), date_add(now(), interval 9 day)
from BIB_Mitglied m, BIB_Buchexemplar be
where m.code = 'M10002' and be.code = 101;
