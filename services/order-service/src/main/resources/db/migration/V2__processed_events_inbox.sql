-- Transactional inbox: records events already processed by each consumer
-- group, so at-least-once Kafka delivery results in exactly-once processing.
-- The claim (INSERT) happens in the same DB transaction as the state change,
-- so a duplicate event either sees the row (skip) or violates the PK (skip).
CREATE TABLE processed_events (
    event_id       RAW(16) NOT NULL,
    consumer_group VARCHAR2(50) NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_group)
);
