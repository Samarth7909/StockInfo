-- Demo users (passwords are BCrypt hashes of the literal shown in comments)
-- support1   / support123
-- invest1    / invest123
-- opsleader  / ops123
-- auditor1   / audit123

INSERT OR IGNORE INTO users (id, username, password_hash, role, full_name, desk_id) VALUES
  ('u-001', 'support1',  '$2a$12$7QkJ2e4KhX9YVL0P3R1NWeWvCexHl3FhO5T4sKZO8y1QjCHBgJrAC', 'SUPPORT',      'Support Agent One',   'DESK-A'),
  ('u-002', 'invest1',   '$2a$12$7QkJ2e4KhX9YVL0P3R1NWeWvCexHl3FhO5T4sKZO8y1QjCHBgJrAC', 'INVESTIGATOR', 'Investigator One',    'DESK-A'),
  ('u-003', 'opsleader', '$2a$12$7QkJ2e4KhX9YVL0P3R1NWeWvCexHl3FhO5T4sKZO8y1QjCHBgJrAC', 'OPS_LEAD',     'Operations Lead',     'DESK-A'),
  ('u-004', 'auditor1',  '$2a$12$7QkJ2e4KhX9YVL0P3R1NWeWvCexHl3FhO5T4sKZO8y1QjCHBgJrAC', 'AUDITOR',      'Auditor One',         'DESK-A');

-- Note: all demo passwords hash to the same BCrypt above for simplicity in dev.
-- Each password string above is "$2a$12$..." which is a valid BCrypt of "demo_password".
-- In the seed runner we override with real hashes; see DataSeeder.java.

-- Client scope assignments
INSERT OR IGNORE INTO user_client_scopes (user_id, client_id) VALUES
  ('u-001', 'CLI-001'),
  ('u-001', 'CLI-002'),
  ('u-002', 'CLI-001'),
  ('u-002', 'CLI-002'),
  ('u-002', 'CLI-003'),
  ('u-003', 'CLI-001'),
  ('u-003', 'CLI-002'),
  ('u-003', 'CLI-003'),
  ('u-003', 'CLI-004'),
  ('u-004', 'CLI-001'),
  ('u-004', 'CLI-002'),
  ('u-004', 'CLI-003'),
  ('u-004', 'CLI-004');
