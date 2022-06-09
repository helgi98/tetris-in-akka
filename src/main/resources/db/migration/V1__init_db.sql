create table user_info (
    id serial primary key,
    username text not null unique,
    display_name text not null,
    email text not null unique,
    password text not null
);

create table game_result (
    id serial primary key,
    user_id integer not null references user_info
    score integer not null,
    lines_cleared integer not null,
    lvl integer not null,
    started_at timestamptz not null,
    finished_at timestamptz not null
);