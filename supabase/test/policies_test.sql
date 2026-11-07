-- Asserts the backup bucket's rules: a user reaches only their own folder; anonymous callers nothing.
\set ON_ERROR_STOP 1
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
insert into storage.objects (bucket_id, name) values ('backups', '11111111-1111-1111-1111-111111111111/tibeb.backup');

do $$
begin
  begin
    insert into storage.objects (bucket_id, name) values ('backups', '22222222-2222-2222-2222-222222222222/tibeb.backup');
    raise exception 'FAIL: wrote into another user''s folder';
  exception when insufficient_privilege then null;
  end;
  begin
    insert into storage.objects (bucket_id, name) values ('backups', 'tibeb.backup');
    raise exception 'FAIL: wrote outside any user folder';
  exception when insufficient_privilege then null;
  end;
  begin
    insert into storage.objects (bucket_id, name) values ('avatars', '11111111-1111-1111-1111-111111111111/x');
    raise exception 'FAIL: rules leak to other buckets';
  exception when insufficient_privilege then null;
  end;
end $$;

set request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
do $$
declare n int;
begin
  select count(*) into n from storage.objects;
  if n <> 0 then raise exception 'FAIL: user 2 can see user 1''s backup'; end if;
  update storage.objects set name = '22222222-2222-2222-2222-222222222222/stolen';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: user 2 could move user 1''s backup'; end if;
  delete from storage.objects;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: user 2 could delete user 1''s backup'; end if;
end $$;

set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
do $$
declare n int;
begin
  select count(*) into n from storage.objects;
  if n <> 1 then raise exception 'FAIL: user 1 cannot see their own backup'; end if;
  begin
    update storage.objects set name = '22222222-2222-2222-2222-222222222222/tibeb.backup';
    raise exception 'FAIL: user 1 could move a backup into user 2''s folder';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;
set role anon;
do $$
declare n int;
begin
  select count(*) into n from storage.objects;
  if n <> 0 then raise exception 'FAIL: anonymous callers can see backups'; end if;
end $$;
reset role;

do $$
begin
  if (select public from storage.buckets where id = 'backups') then raise exception 'FAIL: bucket is public'; end if;
end $$;
select 'all policy checks passed' as result;
