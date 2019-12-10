-- -*- mode: sql; sql-product: postgres; -*-

-- :name create-monitors-table!
-- :command :execute
-- :result :raw
CREATE TABLE monitors
(
    id           serial PRIMARY KEY,
    monitor_type TEXT      NOT NULL,
    group_name   text      NOT NULL,
    name         TEXT      NOT NULL,
    target       text      NOT NULL,
    status       text      NOT NULL,
    success      boolean   NOT NULL,
    ts           timestamp NOT NULL,
    duration     integer -- milliseconds
);
CREATE INDEX monitors_monitor_type_idx ON monitors (monitor_type);
CREATE INDEX monitors_group_name_idx ON monitors (group_name);
CREATE INDEX monitors_name_idx ON monitors (name);
CREATE INDEX monitors_target_idx ON monitors (target);
CREATE INDEX monitors_status_idx ON monitors (status);
CREATE INDEX monitors_success_idx ON monitors (success);
CREATE INDEX monitors_ts_idx ON monitors (ts);

CREATE TABLE outages
(
    id       SERIAL PRIMARY KEY,
    begin_id INTEGER UNIQUE NOT NULL REFERENCES monitors (id),
    end_id   INTEGER REFERENCES monitors (id)
);

-- :name start-outage! :!
INSERT INTO outages(begin_id)
VALUES (:begin-id)

-- :name find-open-outage :? :1
SELECT o.begin_id
FROM monitors AS m,
     outages AS o
WHERE o.end_id IS NULL
  AND o.begin_id = m.id
  AND m.monitor_type = :monitor-type
  AND m.group_name = :group-name
  AND m.target = :target
LIMIT 1

-- :name end-outage! :!
UPDATE outages
SET end_id=:end-id
WHERE begin_id = :begin-id

-- :name outage-summary :?
SELECT DISTINCT mb.monitor_type,
                mb.group_name,
                mb.name,
                mb.target,
                mb.ts         AS begin_ts,
                me.ts         AS end_ts,
                me.ts - mb.ts AS duration
FROM outages AS o,
     monitors AS mb,
     monitors AS me
WHERE o.begin_id = mb.id
  AND o.end_id = me.id
ORDER BY me.ts DESC

-- :name ongoing-outage-summary :?
SELECT DISTINCT mb.monitor_type,
                mb.group_name,
                mb.name,
                mb.target,
                mb.ts         AS begin_ts,
                now() - mb.ts AS duration
FROM outages AS o,
     monitors AS mb
WHERE o.begin_id = mb.id
  AND o.end_id IS NULL
ORDER BY mb.ts ASC

-- :name tables-list :? :*
SELECT table_name
FROM information_schema.TABLES
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'

-- :name summarize :? :*
SELECT monitor_type,
       group_name,
       name,
       target,
       avg(duration)                                                                         AS average_duration,
       (COUNT(*) - sum(case when success = false then 1 else 0 end))::FLOAT / COUNT(*) * 100 AS uptime,
       ts::date                                                                              AS date
FROM monitors
GROUP BY monitor_type, group_name, name, target, date
ORDER BY monitor_type, group_name, name, target, date

-- :name summarize-graph-data :? :*
SELECT monitor_type,
       group_name,
       name,
       target,
       sum(case when success = false then 1 else 0 end)::float / COUNT(*) * 100.0 AS downtime,
       ts::date                                                                   AS date
FROM monitors
GROUP BY monitor_type, group_name, name, target, date
ORDER BY monitor_type, group_name, name, target, date

--- FIXME: \crosstabview target date downtime
-- :name summarize-week :? :*
SELECT monitor_type,
       group_name,
       name,
       target,
       sum(case when success = false then 1 else 0 end)::float / COUNT(*) * 100.0          AS downtime,
       (SELECT SUM(case when success = false then 1 else 0 end)::FLOAT / COUNT(*) * 100.0) AS today,
       ts::date                                                                            AS date
FROM monitors
GROUP BY monitor_type, group_name, name, target, date
ORDER BY monitor_type, group_name, name, target, date

-- :name summarize-by-date :? :*
SELECT monitor_type,
       group_name,
       name,
       target,
       avg(duration)                                                                         AS average_duration,
       (COUNT(*) - SUM(case when success = false then 1 else 0 end))::FLOAT / COUNT(*) * 100 AS uptime,
       ts::date                                                                              AS date
FROM monitors
WHERE ts::date = :date::date
GROUP BY monitor_type, group_name, name, target, date
ORDER BY monitor_type, group_name, name, target, date

-- :name summarize-from-date :? :*
SELECT monitor_type,
       group_name,
       name,
       target,
       avg(duration)                                                                         AS average_duration,
       (COUNT(*) - SUM(case when success = false then 1 else 0 end))::FLOAT / COUNT(*) * 100 AS uptime,
       ts::date                                                                              AS date
FROM monitors
WHERE
--~ (cond (and (:from-date params) (:to-date params)) "ts::date BETWEEN :from-date AND :to-date" (:from-date params) "ts::date >= :from-date" :else "ts::date <= :to-date")
GROUP BY monitor_type, group_name, name, target, date
ORDER BY monitor_type, group_name, name, target, date

--- FIXME extend above to do 7 days, with one column for each day (instead of uptime column; values are uptime percent). One problem: x AS today() etc. will not work, so we need a way of recalculating dates based on

-- :name first-ts-by-monitor :? :1
SELECT monitor_type,
       group_name,
       name,
       target,
       MIN(ts) AS first_ts
FROM monitors
WHERE monitor_type = :monitor_type AND group_name=:group_name AND name=:name

-- :name target-by-date :? :*
SELECT monitor_type,
       group_name,
       name,
       duration,
       status,
       success,
       ts
FROM monitors
WHERE target = :target
  AND date(ts) = :date::date
ORDER BY ts

-- :name monitors-by-date :? :*
SELECT monitor_type,
       target,
       status,
       duration,
       ts
FROM monitors
WHERE group_name = :group_name
  AND date(ts) = :date::date
ORDER BY ts

--- select target, date(ts) as day, sum(case when success=true then 1 else 0 end) over (partition by date(ts), target, success order by target, success, date(ts)) as fails from monitors group by target, success, day
