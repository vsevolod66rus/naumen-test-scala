create table if not exists phone_book
(
    id  serial,
    name varchar(25) not null,
    phone varchar(15) not null,
    date date default now(),
    deleted boolean default false
);


