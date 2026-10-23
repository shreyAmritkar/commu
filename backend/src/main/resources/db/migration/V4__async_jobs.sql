ALTER TABLE evaluations
    ADD COLUMN status        VARCHAR(10) NOT NULL DEFAULT 'DONE',
    ADD COLUMN attempts      INT         NOT NULL DEFAULT 0,
    ADD COLUMN error_message VARCHAR(300),
    ADD COLUMN updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ALTER COLUMN overall_score DROP NOT NULL,
    ALTER COLUMN outcome       DROP NOT NULL,
    ALTER COLUMN summary       DROP NOT NULL,
    ALTER COLUMN result_json   DROP NOT NULL;

ALTER TABLE alternative_replies
    ADD COLUMN status        VARCHAR(10) NOT NULL DEFAULT 'DONE',
    ADD COLUMN attempts      INT         NOT NULL DEFAULT 0,
    ADD COLUMN error_message VARCHAR(300),
    ADD COLUMN updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ALTER COLUMN alternative_message DROP NOT NULL,
    ALTER COLUMN comparison_json     DROP NOT NULL;