use pi2;

create table users (
                        id int auto_increment primary key,
                        name varchar(64) NOT NULL,
                        email varchar(64) NOT NULL
);

create table songs (
                     id int auto_increment primary key,
                     name varchar(64),
                     genre varchar(64),
                     timestamp timestamp default current_timestamp
);

create table user_songs (
                          song_id int not null,
                          user_id int not null,
                          primary key (song_id, user_id),
                          foreign key (song_id) references songs(id) on delete cascade ,
                          foreign key (user_id) references users(id) on delete cascade
);
