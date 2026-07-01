CREATE TABLE IF NOT EXISTS "tb_affiliated_writers" (
    "wr_code" INT PRIMARY KEY,
    "wr_name" VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS "tb_books" (
    "bk_code" INT PRIMARY KEY,
    "bk_title" VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS "tb_edition" (
    "ed_code" INT PRIMARY KEY,
    "pub_date" TIMESTAMP,
    "n_edt" INT,
    "bk_id" INT
);

CREATE TABLE IF NOT EXISTS "tb_authors" (
    "bk_code" INT,
    "wr_id" INT,
    PRIMARY KEY ("bk_code", "wr_id")
);

DELETE FROM "tb_authors";
DELETE FROM "tb_affiliated_writers";
DELETE FROM "tb_books";
DELETE FROM "tb_edition";

INSERT INTO "tb_affiliated_writers" ("wr_code", "wr_name") VALUES
(1, 'J.K. Rowling'),
(2, 'George R.R. Martin'),
(3, 'J.R.R. Tolkien'),
(4, 'Isaac Asimov');

INSERT INTO "tb_books" ("bk_code", "bk_title") VALUES
(1, 'Harry Potter and the Philosophers Stone'),
(2, 'A Game of Thrones'),
(3, 'The Lord of the Rings'),
(4, 'Foundation');

INSERT INTO "tb_edition" ("ed_code", "pub_date", "n_edt", "bk_id") VALUES
(1, '1997-06-26 00:00:00', 1, 1),
(2, '1998-07-02 00:00:00', 2, 1),
(3, '1996-08-06 00:00:00', 1, 2),
(4, '1954-07-29 00:00:00', 1, 3),
(5, '1951-06-01 00:00:00', 1, 4);

INSERT INTO "tb_authors" ("bk_code", "wr_id") VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4);
