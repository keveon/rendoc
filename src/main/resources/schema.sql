-- Rendoc 批量任务持久化 schema
-- 使用 SQLite，零运维，容器内文件存储

CREATE TABLE IF NOT EXISTS jobs (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'pending',       -- pending, processing, completed, failed
    total INTEGER NOT NULL DEFAULT 0,
    succeeded INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    template_path TEXT NOT NULL,
    source_format TEXT NOT NULL DEFAULT 'docx',
    created_at TEXT NOT NULL,                      -- ISO-8601
    completed_at TEXT
);

CREATE TABLE IF NOT EXISTS job_items (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    seq INTEGER NOT NULL,                           -- 0-based item 序号
    status TEXT NOT NULL DEFAULT 'pending',         -- pending, processing, done, failed
    data_json TEXT NOT NULL,
    output_path TEXT,
    error_message TEXT,
    created_at TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_job_items_status ON job_items(status);
CREATE INDEX IF NOT EXISTS idx_job_items_job_id ON job_items(job_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
