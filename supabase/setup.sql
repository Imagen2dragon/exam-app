-- Tibeb cloud backup: one private Storage bucket. Each signed-in user can only reach the folder
-- named after their own user id, e.g. backups/<user id>/tibeb.backup. The files are encrypted on the
-- phone before upload, so not even the project owner can read them.
--
-- Run once: Supabase dashboard -> SQL Editor -> New query -> paste this file -> Run.
-- Safe to run again.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('backups', 'backups', false, 52428800, array['application/octet-stream'])   -- 50 MB, private
on conflict (id) do update
  set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "tibeb backups: read own" on storage.objects;
drop policy if exists "tibeb backups: add own" on storage.objects;
drop policy if exists "tibeb backups: replace own" on storage.objects;
drop policy if exists "tibeb backups: delete own" on storage.objects;

create policy "tibeb backups: read own" on storage.objects for select to authenticated
  using (bucket_id = 'backups' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "tibeb backups: add own" on storage.objects for insert to authenticated
  with check (bucket_id = 'backups' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "tibeb backups: replace own" on storage.objects for update to authenticated
  using (bucket_id = 'backups' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'backups' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "tibeb backups: delete own" on storage.objects for delete to authenticated
  using (bucket_id = 'backups' and (storage.foldername(name))[1] = (select auth.uid())::text);
