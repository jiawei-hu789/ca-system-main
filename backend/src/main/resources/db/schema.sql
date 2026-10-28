CREATE TABLE IF NOT EXISTS system_state (
  name TEXT PRIMARY KEY,
  value BLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS applications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id),
  applicant_name BLOB NOT NULL,
  email BLOB NOT NULL,
  public_key_pem TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  created_at TEXT NOT NULL,
  reviewed_at TEXT,
  reviewed_by INTEGER REFERENCES users(id),
  review_comment BLOB
);
CREATE INDEX IF NOT EXISTS idx_applications_user ON applications(user_id, id);
CREATE INDEX IF NOT EXISTS idx_applications_status ON applications(status, id);

CREATE TABLE IF NOT EXISTS evidence_files (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  application_id INTEGER NOT NULL REFERENCES applications(id),
  original_name BLOB NOT NULL,
  content_type TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  content BLOB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_evidence_application ON evidence_files(application_id);

CREATE TABLE IF NOT EXISTS certificates (
  serial TEXT PRIMARY KEY,
  application_id INTEGER NOT NULL UNIQUE REFERENCES applications(id),
  user_id INTEGER NOT NULL REFERENCES users(id),
  certificate_der BLOB NOT NULL,
  issued_at TEXT NOT NULL,
  not_before TEXT NOT NULL,
  not_after TEXT NOT NULL,
  revoked_at TEXT,
  revocation_reason BLOB
);
CREATE INDEX IF NOT EXISTS idx_certificates_user ON certificates(user_id);

CREATE TABLE IF NOT EXISTS revocation_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  certificate_serial TEXT NOT NULL REFERENCES certificates(serial),
  user_id INTEGER NOT NULL REFERENCES users(id),
  reason BLOB NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
  created_at TEXT NOT NULL,
  completed_at TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pending_revocation ON revocation_requests(certificate_serial) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS operation_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actor_id INTEGER NOT NULL REFERENCES users(id),
  operation TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_id TEXT NOT NULL,
  created_at TEXT NOT NULL
);
