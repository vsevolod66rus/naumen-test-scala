create table if not exists phone_book
(
    id  serial,
    name varchar(40) not null,
    phone varchar(15) not null,
    date date default now(),
    deleted boolean default false,
    primary key (name, phone)
);


